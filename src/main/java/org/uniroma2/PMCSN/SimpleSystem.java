package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;
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

            double sumAreaSys      = 0.0;
            long   sumJobsSys      = 0;
            double sumAreaQueueSys = 0.0;
            long   sumQueueJobsSys = 0;
           // double nextReportTime = 20.0;

            // Simulo nodo per nodo
            for (int i = 0; i < NODES; i++) {
                SimpleMultiserverNode node = new SimpleMultiserverNode(this, i, SERVERS[i], rng);
                double nextReportTime = 200.0;

                node.setLastAreaSnapshot(0.0);
                node.setLastProcessedSnapshot(0);
                node.setLastQueueAreaSnapshot(0.0);
                node.setLastQueueJobsSnapshot(0);


//                double tnext;


                while (true) {
                    double tnext = node.peekNextEventTime();

                    // se non ci sono più eventi utili e ho già superato STOP e l’ultimo report:
                    if ((tnext > STOP) && (nextReportTime > STOP)) {
                        break;
                    }

                    // caso A: il report cade prima del prossimo evento
                    if (nextReportTime <= tnext && nextReportTime <= STOP) {
                        // 1) integra fino a t = nextReportTime
                        node.integrateTo(nextReportTime);

                        // 2) calcola i delta sull’esatto intervallo di 20 unità
                        double deltaArea  = node.getArea()      - node.getLastAreaSnapshot();
                        long   deltaJobs  = node.getProcessedJobs() - node.getLastProcessedSnapshot();
                        double deltaQArea = node.getAreaQueue() - node.getLastQueueAreaSnapshot();
                        long   deltaQJobs = node.getQueueJobs() - node.getLastQueueJobsSnapshot();
                        double intervalLength = 200.0;

                        double eTs = deltaJobs > 0 ? deltaArea / deltaJobs : 0.0;
                        double eNs = deltaArea / intervalLength;
                        double eTq = deltaQJobs > 0 ? deltaQArea / deltaQJobs : 0.0;
                        double eNq = deltaQArea / intervalLength;
                        double serviceInterval = node.getIncrementalServiceTime();
                        double rho = (serviceInterval / intervalLength) / SERVERS[i];

                        // 3) scrivi su CSV
                        FileCSVGenerator.writeIntervalData(
                                true,            // finite
                                rep + 1,         // seed
                                i,               // centro
                                nextReportTime,  // istante esatto
                                eTs, eNs, eTq, eNq, rho
                        );

                        // 4) aggiorna i marker
                        node.setLastAreaSnapshot(node.getArea());
                        node.setLastProcessedSnapshot(node.getProcessedJobs());
                        node.setLastQueueAreaSnapshot(node.getAreaQueue());
                        node.setLastQueueJobsSnapshot(node.getQueueJobs());

                        nextReportTime += 200.0;

                        // caso B: il prossimo evento cade prima del report → processalo
                    } else if (tnext <= STOP) {
                        node.processNextEvent(tnext);

                        // caso C: non ci sono eventi entro STOP, ma magari ci sono ancora report pendenti > STOP
                    } else {
                        // nessun altro evento da processare, ma reportTime > STOP, esco
                        break;
                    }
                }



//                while ((tnext = node.peekNextEventTime()) < STOP) {
//                    node.processNextEvent(tnext);
//                    while (node.getCurrentTime() >= nextReportTime) {
//                        // CALCOLA I DELTA rispetto all'ultimo snapshot
//                        double deltaArea  = node.getArea()      - node.getLastAreaSnapshot();
//                        long   deltaJobs  = node.getProcessedJobs() - node.getLastProcessedSnapshot();
//                        double deltaQArea = node.getAreaQueue() - node.getLastQueueAreaSnapshot();
//                        long   deltaQJobs = node.getQueueJobs() - node.getLastQueueJobsSnapshot();
//                        double intervalLength = nextReportTime
//                                - (nextReportTime - 20.0); // =20
//
//                        // medie sull’intervallo
//                        double eTs = deltaJobs > 0 ? deltaArea / deltaJobs : 0.0;
//                        double eNs = deltaArea / intervalLength;
//                        double eTq = deltaQJobs > 0 ? deltaQArea / deltaQJobs : 0.0;
//                        double eNq = deltaQArea / intervalLength;
//                        double serviceInterval = node.getIncrementalServiceTime();
//                        double rho = (serviceInterval / intervalLength) / SERVERS[i];
//
//                        // SCRIVO su CSV
//                        FileCSVGenerator.writeIntervalData(
//                                true,           // finite
//                                rep + 1,        // seed
//                                i,              // centerIndex
//                                nextReportTime,
//                                eTs, eNs, eTq, eNq, rho
//                        );
//
//                        // **AGGIORNO I MARKER** per il prossimo delta
//                        node.setLastAreaSnapshot(node.getArea());
//                        node.setLastProcessedSnapshot(node.getProcessedJobs());
//                        node.setLastQueueAreaSnapshot(node.getAreaQueue());
//                        node.setLastQueueJobsSnapshot(node.getQueueJobs());
//
//                        nextReportTime += 20.0;
//
//                        System.out.printf("Δarea=%.3f, Δjobs=%d, Δqarea=%.3f, Δqjobs=%d%n",
//                                deltaArea, deltaJobs, deltaQArea, deltaQJobs);
//                    }
//
//                }





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

                sumAreaSys      += node.getAvgResponse() * node.getProcessedJobs();  //accumulo i contributi per le statistiche globali
                sumJobsSys      += node.getProcessedJobs();
                sumAreaQueueSys += node.getAreaQueue();
                sumQueueJobsSys += node.getQueueJobs();
            }

            // Calcolo metriche di sistema per questa replica
            double systemETs = sumAreaSys / sumJobsSys;
            double systemENS = sumAreaSys / STOP;
            double systemETq = sumQueueJobsSys > 0
                    ? sumAreaQueueSys / sumQueueJobsSys
                    : 0.0;
            double systemENq = sumAreaQueueSys / STOP;
            double systemRho = computeSystemUtilization();

            // inserisco nelle stats globali
            systemStats.insert(systemETs, systemENS, systemETq, systemENq, systemRho);

            FileCSVGenerator.writeRepData(
                    true,           // isFinite = true
                    rep + 1,        // seed della replica (oppure usa il seed corretto)
                    -1,             // centerIndex = -1 per indicare "sistema"
                    rep + 1,        // runNumber = numero replica
                    STOP,           // tempo simulazione
                    systemETs,
                    systemENS,
                    systemETq,
                    systemENq,
                    systemRho
            );

        }

        // Stampo i risultati
        System.out.println("=== Finite Simulation – Node Stats ===");
        for (int i = 0; i < NODES; i++) {
            nodeStats[i].printFinalStats("Node " + i);
        }
        System.out.println("=== Finite Simulation – System Stats ===");
        systemStats.printFinalStats("SYSTEM");
    }

    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 1000;
        final int N_BATCHES  = 30;

        System.out.println("=== Infinite Simulation – Batch Means ===");
        Rngs rng = new Rngs();
        rng.plantSeeds(1);

        // Inizializzo i nodi
        List<SimpleMultiserverNode> nodesLoc = new ArrayList<>();
        for (int i = 0; i < NODES; i++) {
            nodesLoc.add(new SimpleMultiserverNode(this, i, SERVERS[i], rng));
        }

        // buffer per aree accumulate (per batch) di nodo e sistema
        double[] lastAreaNode = new double[NODES];
        double[] lastAreaQueueNode  = new double[NODES];
        long[]   lastQueueJobsNode  = new long[NODES];
        double   lastAreaSys  = 0.0;
        double   lastAreaQueueSys   = 0.0;
        double   startTimeBatch = 0.0;      // ADDED: inizio del batch
        double   endTimeBatch   = 0.0;      // ADDED: fine del batch

        int batchCount  = 0;
        int jobsInBatch = 0;

        while (batchCount < N_BATCHES) {
            // Trovo il prossimo evento in tutto il sistema
            double tnext = Double.POSITIVE_INFINITY;
            int chosen = -1;
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
                    startTimeBatch = tnext;          // ADDED: segno inizio batch
                }
                jobsInBatch++;
                endTimeBatch = tnext;               // ADDED: aggiorno fine batch
            }

            if (jobsInBatch >= BATCH_SIZE) {
                // Per ogni nodo calcolo il batch‐mean e lo inserisco
                double areaSys = 0.0;
                double areaQueueSys = 0.0;                        // ADDED: area di coda totale in questo batch
                long   queueJobsSys = 0;
                long jobsProcessedInBatch = 0;

                for (int i = 0; i < NODES; i++) {
                    SimpleMultiserverNode node = nodesLoc.get(i);
                    double areaNode = node.getAvgResponse() * node.getProcessedJobs();
                    double batchMeanNode = (areaNode - lastAreaNode[i]) / BATCH_SIZE;
                    nodeStats[i].insert(batchMeanNode, batchMeanNode, batchMeanNode, batchMeanNode, batchMeanNode);
                    lastAreaNode[i] = areaNode;
                    areaSys += areaNode;
                    jobsProcessedInBatch += node.getProcessedJobs() - node.getLastProcessedJobs();
                    node.setLastProcessedJobs(node.getProcessedJobs()); // <-- Devi esporre questi metodi nella classe SimpleMultiserverNode


                    // --- ADDED: calcolo area coda e job in coda nel batch per nodo ---
                    double thisAreaQueue = node.getAreaQueue();
                    long   thisQueueJobs = node.getQueueJobs();
                    double deltaAreaQueue = thisAreaQueue - lastAreaQueueNode[i];
                    long   deltaJobsQueue = thisQueueJobs - lastQueueJobsNode[i];
                    areaQueueSys += deltaAreaQueue;
                    queueJobsSys += deltaJobsQueue;
                    lastAreaQueueNode[i] = thisAreaQueue;
                    lastQueueJobsNode[i]   = thisQueueJobs;
                }

                // Calcolo batch-mean di sistema e lo inserisco
                double batchMeanSys = jobsProcessedInBatch > 0
                        ? (areaSys - lastAreaSys) / jobsProcessedInBatch
                        : 0.0;

                // Popolazione media di sistema per batch
                double deltaAreaSys = areaSys - lastAreaSys;           // area nel solo batch
                double deltaTime    = endTimeBatch - startTimeBatch;   // durata del batch
                double ensBatch     = deltaAreaSys / deltaTime;       // ENs corretto

                double batchMeanQueue = queueJobsSys > 0
                        ? (areaQueueSys - lastAreaQueueSys) / queueJobsSys
                        : 0.0;                             // tempo medio di attesa in coda
                double avgNumQueue    = (areaQueueSys - lastAreaQueueSys) / (BATCH_SIZE * NODES);

                // 4) *** MODIFIED: CALCOLO DI RHO ***
                // calcolo tempo totale di servizio erogato nel batch
                double serviceTimeBatch = 0.0;                         // ADDED
                for (SimpleMultiserverNode node : nodesLoc) {         // ADDED
                    serviceTimeBatch += node.getIncrementalServiceTime(); // ADDED: devi esporre questo metodo
                }
                int totalServers = Arrays.stream(SERVERS).mapToInt(Integer::intValue).sum(); // ADDED
                double rhoBatch = (serviceTimeBatch / deltaTime) / totalServers; // ADDED

                // 5) inserisco in systemStats
                systemStats.insert(
                        batchMeanSys,  // ETs
                        ensBatch,      // ENs  <-- MODIFIED
                        batchMeanQueue,// ETq
                        avgNumQueue,   // ENq
                        rhoBatch       // Rho  <-- MODIFIED
                );

                // 6) SCRITTURA CSV se vuoi
                FileCSVGenerator.writeRepData(
                        false,                     // isFinite?
                        1,                         // seed
                        -1,                        // system
                        batchCount + 1,            // runNumber
                        endTimeBatch,              // time di chiusura batch
                        batchMeanSys,
                        ensBatch,
                        batchMeanQueue,
                        avgNumQueue,
                        rhoBatch
                );

                // 7) reset per il prossimo batch
                lastAreaSys      = areaSys;
                lastAreaQueueSys = areaQueueSys;
                jobsInBatch      = 0;
                batchCount++;
            }
        }

        // Stampa nodi e sistema
        System.out.println("=== Infinite Simulation – Node Stats ===");
        for (int i = 0; i < NODES; i++) {
            nodeStats[i].printFinalStats("Node " + i);
        }
        System.out.println("=== Infinite Simulation – System Stats ===");
        systemStats.printFinalStats("SYSTEM");
    }

    /** Calcola la media delle utilizzazioni registrate in nodeStats */
    private double computeSystemUtilization() {
        double sum = 0.0;
        for (int i = 0; i < NODES; i++) {
            sum += nodeStats[i].mean(nodeStats[i].getUtilizations());
        }
        return sum / NODES;
    }


    @Override
    public void generateFeedback(MsqEvent event) {
        //non usato in questo sistema
    }


}