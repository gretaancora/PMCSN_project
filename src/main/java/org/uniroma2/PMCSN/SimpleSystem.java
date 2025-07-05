package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;
import org.uniroma2.PMCSN.Utils.FileCSVGenerator;
import org.uniroma2.PMCSN.Utils.ReplicationStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimpleSystem implements Sistema{
    private static final int NODES    = 3;
    private static final int REPLICAS = 4;
    private static final double STOP  = 10000.0;
    public static final Integer[] SERVERS = {33, 11, 11};

    // Statistiche per ogni nodo
    private final ReplicationStats[] nodeStats   = new ReplicationStats[NODES];
    // Statistiche globali del sistema
    private final ReplicationStats   systemStats = new ReplicationStats();

    public SimpleSystem() {
        for (int i = 0; i < NODES; i++) {
            nodeStats[i] = new ReplicationStats();
        }
    }

    @Override
    public void runFiniteSimulation() {
        for (int rep = 0; rep < REPLICAS; rep++) {
            Rngs rng = new Rngs();
            rng.plantSeeds(rep + 1);

            // Simulo nodo per nodo
            for (int i = 0; i < NODES; i++) {
                SimpleMultiserverNode node = new SimpleMultiserverNode(this, i, SERVERS[i], rng);
                double nextReportTime = 50.0;


                while (true) {
                    double tnext = node.peekNextEventTime();

                    // se non ci sono più eventi utili e ho già superato STOP e l’ultimo report:
                    if ((tnext > STOP) && (nextReportTime > STOP)) {
                        break;
                    }


                    // caso A: report prima del prossimo evento
                    if (nextReportTime <= tnext && nextReportTime <= STOP) {
                        // 1) integra fino a t = nextReportTime
                        node.integrateTo(nextReportTime);

                        // 2) calcola le cumulative fino a qui
                        double cumArea  = node.getArea();
                        long   cumJobs  = node.getProcessedJobs();
                        double cumQArea = node.getAreaQueue();
                        long   cumQJobs = node.getQueueJobs();
                        double t        = nextReportTime;

                        double cumETs = cumJobs  > 0 ? cumArea  / cumJobs  : 0.0;
                        double cumENs = cumArea  / t;
                        double cumETq = cumQJobs > 0 ? cumQArea / cumQJobs : 0.0;
                        double cumENq = cumQArea / t;

                        /*aggiunta area service*/
                        double cumES = cumJobs > 0 ? cumAreaService / cumJobs : 0.0;
                        double cumENS = cumAreaService / t;
                        /*aggiunta area service*/

                        double cumRho = node.getUtilization();

                        // 3) scrivi SOLO cumulative
                        FileCSVGenerator.writeIntervalData(
                                true,            // finite
                                rep + 1,         // seed
                                i,               // centerIndex
                                nextReportTime,  // Time
                                // cumulative
                                cumETs,
                                cumENs,
                                cumETq,
                                cumENq,
                                cumRho
                        );

                        // 4) non ci sono marker da aggiornare, perché usiamo sempre i totali
                        nextReportTime += 50.0;  // o 200.0 come preferisci
                    }

                    else if (tnext <= STOP) {
                        node.processNextEvent(tnext);

                        // caso C: non ci sono eventi entro STOP, ma magari ci sono ancora report pendenti > STOP
                    } else {
                        // nessun altro evento da processare, ma reportTime > STOP, esco
                        break;
                    }
                }

                // raccolta stats nodo
                node.collectStatistics(rep);

                // estraggo le 5 metriche dal nodo
                double eTs = node.getAvgResponse();
                double eNs = node.getAvgNumInNode();
                double eTq = node.getAvgWaitingInQueue();
                double eNq = node.getAvgNumInQueue();
                double rho = node.getUtilization();

                // inserisco nelle stats per quel nodo
                nodeStats[i].insert(eTs, eNs, eTq, eNq, rho);
            }
        }

        // Stampo i risultati
        System.out.println("=== Finite Simulation – Node Stats ===");
        for (int i = 0; i < NODES; i++) {
            nodeStats[i].printFinalStats("Node " + i);
        }


    }

    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 256;
        final int N_BATCHES  = 64;

        System.out.println("=== Infinite Simulation – Batch Means ===");
        Rngs rng = new Rngs();
        rng.plantSeeds(1);

        // Inizializzo i nodi
        List<SimpleMultiserverNode> nodesLoc = new ArrayList<>();
        for (int i = 0; i < NODES; i++) {
            nodesLoc.add(new SimpleMultiserverNode(this, i, SERVERS[i], rng));
        }

        // Variabili per accumulo cumulativo dei globali
        double cumETs = 0.0, cumENs = 0.0, cumETq = 0.0, cumENq = 0.0, cumRho = 0.0;


        // Marker per delta batch
        double   lastAreaSys       = 0.0;
        double   lastAreaQueueSys  = 0.0;

        int batchCount  = 0;
        int jobsInBatch;
        jobsInBatch = 0;
        double startTimeBatch = 0.0;
        double endTimeBatch = 0.0;

        /*aggiunte le liste per batch means */
        // Liste per batch means
        List<Double> etList = new ArrayList<>();
        List<Double> enList = new ArrayList<>();
        List<Double> etqList = new ArrayList<>();
        List<Double> enqList = new ArrayList<>();
        List<Double> rhoList = new ArrayList<>();
        /*aggiunte le liste per batch means */

        while (batchCount < N_BATCHES) {
            // Trovo e processiamo il prossimo evento di sistema
            double tnext = Double.POSITIVE_INFINITY;
            int    chosen = -1;
            for (int i = 0; i < NODES; i++) {
                double t = nodesLoc.get(i).peekNextEventTime();
                if (t < tnext) {
                    tnext = t;
                    chosen = i;
                }
            }
            int srv = nodesLoc.get(chosen).processNextEvent(tnext);
            if (srv >= 0) {
                if (jobsInBatch == 0) {
                    startTimeBatch = tnext;
                }
                jobsInBatch++;
                endTimeBatch = tnext;
            }

            // Se raccolti abbastanza job, chiudo il batch
            if (jobsInBatch >= BATCH_SIZE) {
                batchCount++;

                // Calcolo dei contributi per ogni nodo
                double areaSys        = 0.0;
                double areaQueueSys   = 0.0;
                long   queueJobsSys   = 0;
                int    jobsProcessed  = 0;

                for (int i = 0; i < NODES; i++) {
                    SimpleMultiserverNode node = nodesLoc.get(i);

                    // ETs: area‐based
                    double nodeTotalArea = node.getAvgResponse() * node.getProcessedJobs();
                    areaSys += nodeTotalArea;
                    int   processedNow   = (int)(node.getProcessedJobs() - node.getLastProcessedJobs());
                    jobsProcessed += processedNow;
                    node.setLastProcessedJobs(node.getProcessedJobs());

                    // ENs & queue
                    double thisAreaQ  = node.getAreaQueue();
                    long   thisQJobs  = node.getQueueJobs();

                    areaQueueSys += thisAreaQ;
                    queueJobsSys += thisQJobs;
                }

                // Metriche di batch
                double batchETs = (jobsProcessed > 0)
                        ? (areaSys - lastAreaSys) / jobsProcessed
                        : 0.0;
                double batchENs = (areaSys - lastAreaSys) / (endTimeBatch - startTimeBatch);
                double batchETq = (queueJobsSys > 0)
                        ? (areaQueueSys - lastAreaQueueSys) / queueJobsSys
                        : 0.0;
                double batchENq = (areaQueueSys - lastAreaQueueSys) / (BATCH_SIZE * NODES);

                // Rho batch
                double serviceTimeBatch = 0.0;

                for (SimpleMultiserverNode node : nodesLoc) {
                    serviceTimeBatch += node.getIncrementalServiceTime();
                }
                int totalServers = Arrays.stream(SERVERS).mapToInt(Integer::intValue).sum();
                double batchRho = (serviceTimeBatch / (endTimeBatch - startTimeBatch)) / totalServers;

                // Inserisco nella statistica di sistema per batch
                systemStats.insert(batchETs, batchENs, batchETq, batchENq, batchRho);

                // Aggiorno i cumulativi
                cumETs += batchETs;
                cumENs += batchENs;
                cumETq += batchETq;
                cumENq += batchENq;
                cumRho += batchRho;

                /*aggiunte le liste per batch means */
                etList.add(batchETs);
                enList.add(batchENs);
                etqList.add(batchETq);
                enqList.add(batchENq);
                rhoList.add(batchRho);
                /*aggiunte le liste per batch means */

                // Scrivo la media cumulativa fino a questo batch
                FileCSVGenerator.writeInfiniteGlobal(
                        batchCount,
                        cumETs / batchCount,
                        cumENs / batchCount,
                        cumETq / batchCount,
                        cumENq / batchCount,
                        cumRho / batchCount
                );

                // Reset contatore jobs per il batch successivo
                lastAreaSys      = areaSys;
                lastAreaQueueSys = areaQueueSys;
                jobsInBatch      = 0;
            }
        }

        systemStats.printConfidenceInterval("ETs", etList);
        systemStats.printConfidenceInterval("ENs", enList);
        systemStats.printConfidenceInterval("ETq", etqList);
        systemStats.printConfidenceInterval("ENq", enqList);
        systemStats.printConfidenceInterval("Rho", rhoList);

        System.out.println("=== Infinite Simulation – Fine ===");
    }

    @Override
    public void generateFeedback(MsqEvent event) {
        //non usato in questo sistema
    }
}