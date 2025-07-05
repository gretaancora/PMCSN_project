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
    private static final double STOP  = 30000.0;
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

//   @Override
//    public void runFiniteSimulation() {
//        for (int rep = 0; rep < REPLICAS; rep++) {
//            Rngs rng = new Rngs();
//            rng.plantSeeds(rep + 1);
//
//            // Simulo nodo per nodo
//            for (int i = 0; i < NODES; i++) {
//                SimpleMultiserverNode node = new SimpleMultiserverNode(this, i, SERVERS[i], rng);
//                double nextReportTime = 100.0;
//
//
//                while (true) {
//                    double tnext = node.peekNextEventTime();
//
//                    // se non ci sono più eventi utili e ho già superato STOP e l’ultimo report:
//                    if ((tnext > STOP) && (nextReportTime > STOP)) {
//                        break;
//                    }
//
//
//                    // caso A: report prima del prossimo evento
//                    if (nextReportTime <= tnext) {
//                        // 1) integra fino a t = nextReportTime
//                        node.integrateTo(nextReportTime);
//
//                        // 2) calcola le cumulative fino a qui
//                        double cumArea  = node.getArea();
//                        long   cumJobs  = node.getProcessedJobs();
//                        double cumQArea = node.getAreaQueue();
//                        long   cumQJobs = node.getQueueJobs();
//                        /*aggiunta area service*/
//                        double cumAreaService = node.getAreaService();
//                        double   cumSJobs = node.getNumInService();
//                        /*aggiunta area service*/
//                        /*aggiungere areaService nelle statistiche*/
//
//                        double t        = nextReportTime;
//
//                        double cumETs = cumJobs  > 0 ? cumArea  / cumJobs  : 0.0;
//                        double cumENs = cumArea  / t;
//                        double cumETq = cumJobs > 0 ? cumQArea / cumJobs : 0.0;
//                        double cumENq = cumQArea / t;
//
//                        /*aggiunta area service*/
//                        double cumES = cumJobs > 0 ? cumAreaService / cumJobs : 0.0;
//                        double cumENS = cumAreaService / t;
//                        /*aggiunta area service*/
//
//                        double cumRho = node.getUtilization();
//
//                        // 3) scrivi SOLO cumulative
//                        FileCSVGenerator.writeIntervalData(
//                                true,            // finite
//                                rep + 1,         // seed
//                                i,               // centerIndex
//                                nextReportTime,  // Time
//                                // cumulative
//                                cumETs,
//                                cumENs,
//                                cumETq,
//                                cumENq,
//                                cumRho
//                        );
//
//                        // 4) non ci sono marker da aggiornare, perché usiamo sempre i totali
//                        nextReportTime += 100.0;  // o 200.0 come preferisci
//                    }
//
//                    else if (tnext <= STOP) {
//                        node.processNextEvent(tnext);
//
//                        // caso C: non ci sono eventi entro STOP, ma magari ci sono ancora report pendenti > STOP
//                    } else {
//                        // nessun altro evento da processare, ma reportTime > STOP, esco
//                        break;
//                    }
//                }
//
//                // raccolta stats nodo
//                node.collectStatistics(rep);
//
//                // estraggo le 5 metriche dal nodo
//                double eTs = node.getAvgResponse();
//                double eNs = node.getAvgNumInNode();
//                double eTq = node.getAvgWaitingInQueue();
//                double eNq = node.getAvgNumInQueue();
//                double rho = node.getUtilization();
//
//                // inserisco nelle stats per quel nodo
//                nodeStats[i].insert(eTs, eNs, eTq, eNq, rho);
//            }
//        }
//
//        // Stampo i risultati
//        System.out.println("=== Finite Simulation – Node Stats ===");
//        for (int i = 0; i < NODES; i++) {
//            nodeStats[i].printFinalStats("Node " + i);
//        }
//    }
//

    @Override
    public void runFiniteSimulation() {
        final double REPORT_INTERVAL = 50.0;
        final double WARMUP          = 200.0;    // elimina primo transient
        final int    SYSTEM_INDEX    = -1;

        // Prepara il CSV globale (solo header, una volta)
        FileCSVGenerator.writeFiniteIntervalGlobalHeader();

        for (int rep = 1; rep <= REPLICAS; rep++) {
            // 1) Inizializza RNG
            Rngs rng = new Rngs();
            rng.plantSeeds(rep);

            // 2) Crea tutti i nodi “puliti”
            List<SimpleMultiserverNode> localNodes = new ArrayList<>();
            for (int i = 0; i < NODES; i++) {
                SimpleMultiserverNode n = new SimpleMultiserverNode(this, i, SERVERS[i], rng);
                n.resetState();
                localNodes.add(n);
            }

            // 3) Imposta il primo report dopo warmup
            double nextReportTime = WARMUP + REPORT_INTERVAL;

            // 4) Ciclo principale di simulazione (eventi + reporting)
            while (true) {
                // Trova il prossimo evento tra tutti i nodi
                double tmin = Double.POSITIVE_INFINITY;
                int idxMin = -1;
                for (int i = 0; i < localNodes.size(); i++) {
                    double t = localNodes.get(i).peekNextEventTime();
                    if (t < tmin) {
                        tmin  = t;
                        idxMin = i;
                    }
                }

                // Se non ci sono più eventi utili e ho superato STOP e l’ultimo report
                if (tmin > STOP && nextReportTime > STOP) {
                    break;
                }

                // **Caso REPORT**: prima del prossimo evento e prima di STOP
                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    // 4.1) Integra tutti i nodi fino a nextReportTime
                    for (SimpleMultiserverNode n : localNodes) {
                        n.integrateTo(nextReportTime);
                    }

                    // 4.2) Calcola STATISTICHE GLOBALI
                    double cumArea      = 0.0;
                    long   cumJobs      = 0;
                    double cumAreaQueue = 0.0;
                    double cumRhoSum    = 0.0;

                    for (SimpleMultiserverNode n : localNodes) {
                        cumArea      += n.getArea();
                        cumJobs      += n.getProcessedJobs();
                        cumAreaQueue += n.getAreaQueue();
                        cumRhoSum    += n.getUtilization();
                    }
                    double cumETs = cumJobs > 0 ? cumArea      / cumJobs      : 0.0;
                    double cumENs =               cumArea      / nextReportTime;
                    double cumETq = cumJobs > 0 ? cumAreaQueue / cumJobs      : 0.0;
                    double cumENq =               cumAreaQueue / nextReportTime;
                    double cumRho =               cumRhoSum    / localNodes.size();

                    // 4.3) SCRIVE la riga GLOBALE
                    FileCSVGenerator.writeFiniteIntervalGlobal(
                            rep,
                            nextReportTime,
                            cumETs, cumENs, cumETq, cumENq, cumRho
                    );

                    // 4.4) Calcola e SCRIVE le righe per ciascun nodo
                    for (int i = 0; i < localNodes.size(); i++) {
                        SimpleMultiserverNode n = localNodes.get(i);

                        double area_i  = n.getArea();
                        long   jobs_i  = n.getProcessedJobs();
                        double areaQ_i = n.getAreaQueue();

                        double ETs_i = jobs_i > 0 ? area_i      / jobs_i      : 0.0;
                        double ENs_i =               area_i      / nextReportTime;
                        double ETq_i = jobs_i > 0 ? areaQ_i     / jobs_i      : 0.0;
                        double ENq_i =               areaQ_i     / nextReportTime;
                        double rho_i = n.getUtilization();

                        FileCSVGenerator.writeIntervalData(
                                true,              // finite
                                rep,
                                i,
                                nextReportTime,
                                ETs_i, ENs_i, ETq_i, ENq_i, rho_i
                        );
                    }

                    // 4.5) Avanza il reporting
                    nextReportTime += REPORT_INTERVAL;
                    continue;
                }

                // **Caso EVENTO**: processo il prossimo evento
                if (tmin <= STOP) {
                    localNodes.get(idxMin).processNextEvent(tmin);
                } else {
                    break;
                }
            }

            // 5) Statistiche finali di replica (facoltative a console)
            double respSum = 0.0;
            for (SimpleMultiserverNode n : localNodes) {
                respSum += n.getAvgResponse();
            }
            System.out.printf("Replica %d – Avg response system-wide: %.5f%n",
                    rep, respSum / localNodes.size());
        }
    }

    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 256;
        final int N_BATCHES  = 64;

        System.out.println("=== Infinite Simulation – Batch Means (Global + Per‑Node) ===");
        Rngs rng = new Rngs();
        rng.plantSeeds(1);

        // 1) Inizializza i nodi
        List<SimpleMultiserverNode> nodesLoc = new ArrayList<>();
        for (int i = 0; i < NODES; i++) {
            nodesLoc.add(new SimpleMultiserverNode(this, i, SERVERS[i], rng));
        }

        // 2) Prepara CSV globali e per‑nodo (header)
        FileCSVGenerator.writeInfiniteGlobal(0, 0, 0, 0, 0, 0);
        // assume writeInfiniteLocal già esiste come descritto prima

        // 3) Marker per delta batch globali e per‑nodo
        double lastAreaSys      = 0.0;
        double lastAreaQueueSys = 0.0;
        long[]   lastProcessedJobs = new long[NODES];
        double[] lastAreaNode      = new double[NODES];
        double[] lastAreaQueueNode = new double[NODES];

        // 4) Cumulativi globali per medie cumulative
        double cumETs = 0.0, cumENs = 0.0, cumETq = 0.0, cumENq = 0.0, cumRho = 0.0;
        // 5) Cumulativi per‑nodo
        double[] cumETsNode = new double[NODES];
        double[] cumENsNode = new double[NODES];
        double[] cumETqNode = new double[NODES];
        double[] cumENqNode = new double[NODES];
        double[] cumRhoNode = new double[NODES];

        int batchCount  = 0;
        int jobsInBatch = 0;
        double startTimeBatch = 0.0, endTimeBatch = 0.0;

        // 6) Liste per intervalli di confidenza (globali)
        List<Double> etList  = new ArrayList<>();
        List<Double> enList  = new ArrayList<>();
        List<Double> etqList = new ArrayList<>();
        List<Double> enqList = new ArrayList<>();
        List<Double> rhoList = new ArrayList<>();

        // 7) Ciclo di simulazione
        while (batchCount < N_BATCHES) {
            // Trova il prossimo evento
            double tnext = Double.POSITIVE_INFINITY;
            int    chosen = -1;
            for (int i = 0; i < NODES; i++) {
                double t = nodesLoc.get(i).peekNextEventTime();
                if (t < tnext) {
                    tnext = t;
                    chosen = i;
                }
            }

            // Integra TUTTI i nodi fino a tnext
            for (SimpleMultiserverNode node : nodesLoc) {
                node.integrateTo(tnext);
            }

            // Processa l’evento scelto
            int srv = nodesLoc.get(chosen).processNextEvent(tnext);
            if (srv >= 0) {
                if (jobsInBatch == 0) startTimeBatch = tnext;
                jobsInBatch++;
                endTimeBatch = tnext;
            }

            // Chiudi batch se completati BATCH_SIZE job
            if (jobsInBatch >= BATCH_SIZE) {
                batchCount++;
                double batchDur = endTimeBatch - startTimeBatch;

                // --- 8) Calcolo metriche GLOBALI per il batch ---
                double areaSys       = 0.0;
                double areaQueueSys  = 0.0;
                int    jobsProcessed = 0;
                double sumRhoGlobal  = 0.0;

                // Calcola incrementali e usa getUtilization() per rho globale
                for (int i = 0; i < NODES; i++) {
                    SimpleMultiserverNode node = nodesLoc.get(i);
                    double area      = node.getArea();
                    double areaQ     = node.getAreaQueue();
                    long   procJobs  = node.getProcessedJobs();

                    areaSys      += area;
                    areaQueueSys += areaQ;

                    int deltaP = (int)(procJobs - lastProcessedJobs[i]);
                    jobsProcessed += deltaP;

                    sumRhoGlobal += node.getUtilization();
                }

                double batchETs = jobsProcessed > 0
                        ? (areaSys - lastAreaSys) / jobsProcessed
                        : 0.0;
                double batchENs = (areaSys - lastAreaSys) / batchDur;
                double batchETq = jobsProcessed > 0
                        ? (areaQueueSys - lastAreaQueueSys) / jobsProcessed
                        : 0.0;
                double batchENq = (areaQueueSys - lastAreaQueueSys) / batchDur;
                double batchRho = sumRhoGlobal / NODES;  // media delle utilità

                // Aggiorna cumulativi globali e scrive media cumulativa
                cumETs += batchETs;
                cumENs += batchENs;
                cumETq += batchETq;
                cumENq += batchENq;
                cumRho += batchRho;

                FileCSVGenerator.writeInfiniteGlobal(
                        batchCount,
                        cumETs / batchCount,
                        cumENs / batchCount,
                        cumETq / batchCount,
                        cumENq / batchCount,
                        cumRho / batchCount
                );

                // Salva per intervalli di confidenza
                etList.add(batchETs);
                enList.add(batchENs);
                etqList.add(batchETq);
                enqList.add(batchENq);
                rhoList.add(batchRho);

                // --- 9) Statistiche cumulative PER NODO ---
                for (int i = 0; i < NODES; i++) {
                    SimpleMultiserverNode node = nodesLoc.get(i);

                    double area      = node.getArea();
                    double areaQ     = node.getAreaQueue();
                    long   procJobs  = node.getProcessedJobs();
                    double rho_i     = node.getUtilization();

                    double deltaA   = area - lastAreaNode[i];
                    double deltaAQ  = areaQ - lastAreaQueueNode[i];
                    int    deltaPj  = (int)(procJobs - lastProcessedJobs[i]);

                    double ETs_i = deltaPj > 0 ? deltaA / deltaPj : 0.0;
                    double ENs_i = deltaA / batchDur;
                    double ETq_i = deltaPj > 0 ? deltaAQ / deltaPj : 0.0;
                    double ENq_i = deltaAQ / batchDur;

                    // Accumula e calcola media cumulativa per il nodo i
                    cumETsNode[i] += ETs_i;
                    cumENsNode[i] += ENs_i;
                    cumETqNode[i] += ETq_i;
                    cumENqNode[i] += ENq_i;
                    cumRhoNode[i] += rho_i;

                    FileCSVGenerator.writeInfiniteLocal(
                            batchCount,
                            i,
                            cumETsNode[i] / batchCount,
                            cumENsNode[i] / batchCount,
                            cumETqNode[i] / batchCount,
                            cumENqNode[i] / batchCount,
                            cumRhoNode[i] / batchCount
                    );

                    // Aggiorna marker per il prossimo batch
                    lastProcessedJobs[i]  = procJobs;
                    lastAreaNode[i]       = area;
                    lastAreaQueueNode[i]  = areaQ;
                }

                // --- 10) Reset marker globali e contatore batch ---
                lastAreaSys      = areaSys;
                lastAreaQueueSys = areaQueueSys;
                jobsInBatch      = 0;
            }
        }

        // 11) Stampa intervalli di confidenza globali
        System.out.println("=== Intervalli di confidenza (95%) ===");
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