package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;
import org.uniroma2.PMCSN.Utils.FileCSVGenerator;
import org.uniroma2.PMCSN.Utils.ReplicationStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RideSharingSystem implements Sistema {
    // Numero di centri semplici nel sistema
    private static final int SIMPLE_CENTERS = 3;
    // Numero di centri ride-sharing nel sistema
    private static final int RIDE_CENTERS = 1;
    // Numero di repliche da effettuare
    private static final int REPLICAS = 4;
    // Tempo di stop della simulazione (orizzonte finito)
    private static final double STOP = 10000.0;
    // Numero di server configurati per ciascun nodo semplice
    public static final Integer[] SERVERS_SIMPLE = {
            27,
            9,
            9
    };
    private Rngs rng;
    private static final List<Node> nodes = new ArrayList<>(4);

    public RideSharingSystem(){

        rng = new Rngs();

        //istanza 3 centri semplici
        for (int i = 0; i < SIMPLE_CENTERS; i++) {
            SimpleMultiserverNode center = new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng);
            nodes.add(center);
        }

        // istanza centro ride sharing
        for (int j = 0; j < RIDE_CENTERS; j++) {
            RideSharingMultiserverNode rideNode = new RideSharingMultiserverNode(rng, this);
            nodes.add(rideNode);
        }
    }


    @Override
    public void runFiniteSimulation() {
        final double REPORT_INTERVAL = 50.0;
        final int    SYSTEM_INDEX    = -1;
        final double WARMUP = 200.0;                // elimina primo transient
// NON scrivere nulla per t ≤ WARMUP


        for (int rep = 1; rep <= REPLICAS; rep++) {
            // 1) Inizializza RNG
            rng = new Rngs();
            rng.plantSeeds(rep);

            // 2) Ricrea i nodi "puliti" per questa replica
            List<Node> localNodes = new ArrayList<>();
            for (int i = 0; i < SIMPLE_CENTERS; i++) {
                SimpleMultiserverNode n = new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng);
                n.resetState();
                localNodes.add(n);
            }
            for (int j = 0; j < RIDE_CENTERS; j++) {
                RideSharingMultiserverNode n = new RideSharingMultiserverNode(rng, this);
                n.resetState();
                localNodes.add(n);
            }

            // 3) Prepara il reporting a intervalli
            double nextReportTime = WARMUP + REPORT_INTERVAL;

            // 4) Loop eventi fino a STOP
            while (true) {
                // trova il prossimo evento
                double tmin = Double.POSITIVE_INFINITY;
                int idx = 0;
                for (int i = 0; i < localNodes.size(); i++) {
                    double t = localNodes.get(i).peekNextEventTime();
                    if (t < tmin) {
                        tmin = t;
                        idx  = i;
                    }
                }

                // se non ci sono più eventi e ho già superato STOP e l'ultimo report
                if (tmin > STOP && nextReportTime > STOP) {
                    break;
                }

                // caso report prima del prossimo evento
                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    // integra tutti i nodi fino a nextReportTime
                    for (Node n : localNodes) {
                        n.integrateTo(nextReportTime);
                    }

                    // --- Statistiche GLOBALI ---
                    double cumArea      = 0.0;
                    long   cumJobs      = 0;
                    double cumAreaQueue = 0.0;
                    /* long cumQJobs= 0; // non più usato per ETq */
                    for (Node n : localNodes) {
                        cumArea      += n.getArea();
                        cumJobs      += n.getProcessedJobs();
                        cumAreaQueue += n.getAreaQueue();
                    }
                    double cumETs = cumJobs > 0 ? cumArea / cumJobs : 0.0;
                    double cumENs = cumArea / nextReportTime;
                    double cumETq = cumJobs > 0 ? cumAreaQueue / cumJobs : 0.0;
                    double cumENq = cumAreaQueue / nextReportTime;
                    double cumRho = localNodes.stream()
                            .mapToDouble(Node::getUtilization)
                            .average()
                            .orElse(0.0);

                    // scrivo la riga GLOBAL
                    FileCSVGenerator.writeIntervalData(
                            true,           // global flag
                            rep,
                            SYSTEM_INDEX,   // -1 per sistema globale
                            nextReportTime,
                            cumETs, cumENs, cumETq, cumENq, cumRho
                    );

                    // --- Statistiche per ciascun NODO ---
                    for (int i = 0; i < localNodes.size(); i++) {
                        Node n = localNodes.get(i);

                        double area_i      = n.getArea();
                        long   jobs_i      = n.getProcessedJobs();
                        double areaQ_i     = n.getAreaQueue();
                        //long   qJobs_i     = n.getQueueJobs(); // non usato per ETq
                        double ETs_i       = jobs_i > 0 ? area_i / jobs_i : 0.0;
                        double ENs_i       = area_i / nextReportTime;
                        double ETq_i       = jobs_i > 0 ? areaQ_i / jobs_i : 0.0;
                        double ENq_i       = areaQ_i / nextReportTime;
                        double rho_i       = n.getUtilization();

                        // scrivo la riga per il nodo i
                        FileCSVGenerator.writeIntervalData(
                                true,      // nodo-level flag
                                rep,
                                i,          // SYSTEM_INDEX = indice del nodo
                                nextReportTime,
                                ETs_i, ENs_i, ETq_i, ENq_i, rho_i
                        );
                    }

                    // avanza il reporting
                    nextReportTime += REPORT_INTERVAL;
                    continue;
                }

                // altrimenti processo il prossimo evento
                if (tmin <= STOP) {
                    localNodes.get(idx).processNextEvent(tmin);
                } else {
                    break;
                }
            }

            // 5) statistiche finali di replica (globali)
            double procSum = 0.0, respSum = 0.0;
            for (Node n : localNodes) {
                procSum += n.getProcessedJobs();
                respSum += n.getAvgResponse();
            }
            double avgProc = procSum / localNodes.size();
            double avgResp = respSum / localNodes.size();

            System.out.println("=== RideSharingSystem (Finite) replica " + rep + " ===");
            System.out.printf(" Avg jobs: %.2f, Avg response: %.2f%n", avgProc, avgResp);
        }
    }

    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE   = 256;
        final int N_BATCHES    = 64;
        final int TOTAL_NODES  = SIMPLE_CENTERS + RIDE_CENTERS;

        System.out.println("=== RideSharingSystem (Infinite Simulation – Batch Means – Per‑Node Cumulative) ===");
        System.out.printf("Nodi: %d (simple=%d, ride=%d), Batch size: %d, #Batch totali: %d%n",
                TOTAL_NODES, SIMPLE_CENTERS, RIDE_CENTERS, BATCH_SIZE, N_BATCHES);

        // 1) CSV globale (medie cumulative)
        FileCSVGenerator.writeInfiniteGlobal(0, 0, 0, 0, 0, 0);

        // 2) Inizializza RNG e nodi
        Rngs rng = new Rngs();
        rng.plantSeeds(1);
        List<Node> nodesLoc = new ArrayList<>(TOTAL_NODES);
        for (int i = 0; i < SIMPLE_CENTERS; i++) {
            nodesLoc.add(new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng));
        }
        for (int j = 0; j < RIDE_CENTERS; j++) {
            nodesLoc.add(new RideSharingMultiserverNode(rng, this));
        }

        // 3) Marker per delta batch globali e per‑nodo
        double lastAreaSys      = 0.0;
        double lastAreaQueueSys = 0.0;
        long[]   lastProcessedJobs = new long[TOTAL_NODES];
        double[] lastAreaNode      = new double[TOTAL_NODES];
        double[] lastAreaQueueNode = new double[TOTAL_NODES];

        // 4) Cumulativi globali per medie
        double cumETs = 0.0, cumENs = 0.0, cumETq = 0.0, cumENq = 0.0, cumRho = 0.0;

        // 5) Cumulativi per‑nodo
        double[] cumETsNode  = new double[TOTAL_NODES];
        double[] cumENsNode  = new double[TOTAL_NODES];
        double[] cumETqNode  = new double[TOTAL_NODES];
        double[] cumENqNode  = new double[TOTAL_NODES];
        double[] cumRhoNode  = new double[TOTAL_NODES];

        int batchCount  = 0;
        int completions = 0;
        double startBatch = 0.0, endBatch = 0.0;

        // 6) Liste per intervalli di confidenza globali
        List<Double> etList  = new ArrayList<>();
        List<Double> enList  = new ArrayList<>();
        List<Double> etqList = new ArrayList<>();
        List<Double> enqList = new ArrayList<>();
        List<Double> rhoList = new ArrayList<>();

        // 7) Ciclo di simulazione a batch
        while (batchCount < N_BATCHES) {
            double tnext = Double.POSITIVE_INFINITY;
            Node chosen = null;
            for (Node n : nodesLoc) {
                double t = n.peekNextEventTime();
                if (t < tnext) {
                    tnext = t;
                    chosen = n;
                }
            }
            for (Node n : nodesLoc) n.integrateTo(tnext);

            if (chosen.processNextEvent(tnext) >= 0) {
                if (completions == 0) startBatch = tnext;
                completions++;
                endBatch = tnext;
            }

            if (completions >= BATCH_SIZE) {
                batchCount++;
                double batchDuration = endBatch - startBatch;

                // 8) Calcolo metriche globali batch
                double areaSys       = 0.0;
                double areaQueueSys  = 0.0;
                int    batchJobsProcessed = 0;
                double sumUtil       = 0.0;

                for (int i = 0; i < TOTAL_NODES; i++) {
                    Node node = nodesLoc.get(i);
                    double a    = node.getArea();
                    double aq   = node.getAreaQueue();
                    long   pj   = node.getProcessedJobs();

                    areaSys      += a;
                    areaQueueSys += aq;
                    batchJobsProcessed += (int)(pj - lastProcessedJobs[i]);
                    sumUtil       += node.getUtilization();
                }

                double batchETs = batchJobsProcessed > 0
                        ? (areaSys - lastAreaSys) / batchJobsProcessed
                        : 0.0;
                double batchENs = (areaSys - lastAreaSys) / batchDuration;
                double batchETq = batchJobsProcessed > 0
                        ? (areaQueueSys - lastAreaQueueSys) / batchJobsProcessed
                        : 0.0;
                double batchENq = (areaQueueSys - lastAreaQueueSys) / batchDuration;
                double batchRho = sumUtil / TOTAL_NODES;

                // 9) Aggiorna cumulativi globali e salva
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

                etList.add(batchETs);
                enList.add(batchENs);
                etqList.add(batchETq);
                enqList.add(batchENq);
                rhoList.add(batchRho);

                // 10) Statistiche per‑nodo cumulative
                for (int i = 0; i < TOTAL_NODES; i++) {
                    Node node = nodesLoc.get(i);
                    long pj     = node.getProcessedJobs();
                    double a    = node.getArea();
                    double aq   = node.getAreaQueue();

                    double deltaA  = a  - lastAreaNode[i];
                    double deltaAQ = aq - lastAreaQueueNode[i];
                    int    deltaPj = (int)(pj - lastProcessedJobs[i]);

                    double ETs_i = deltaPj > 0 ? deltaA / deltaPj : 0.0;
                    double ENs_i = deltaA / batchDuration;
                    double ETq_i = deltaPj > 0 ? deltaAQ / deltaPj : 0.0;
                    double ENq_i = deltaAQ / batchDuration;
                    double rho_i = node.getUtilization();

                    // accumula e calcola media cumulativa per il nodo i
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

                    lastProcessedJobs[i]   = pj;
                    lastAreaNode[i]        = a;
                    lastAreaQueueNode[i]   = aq;
                }

                // 11) Aggiorna marker globali e resetta contatore
                lastAreaSys      = areaSys;
                lastAreaQueueSys = areaQueueSys;
                completions      = 0;
            }
        }

        // 12) Stampa intervalli di confidenza globali
        System.out.println("=== Intervalli di confidenza (95%) ===");
        systemStats.printConfidenceInterval("ETs",  etList);
        systemStats.printConfidenceInterval("ENs",  enList);
        systemStats.printConfidenceInterval("ETq",  etqList);
        systemStats.printConfidenceInterval("ENq",  enqList);
        systemStats.printConfidenceInterval("Rho",  rhoList);

        System.out.println("=== Infinite Simulation – Fine ===");
    }



    public void generateFeedback(MsqEvent event) {
        if (event.postiRichiesti < 4) {
            nodes.getFirst().setArrivalEvent(event);
            nodes.getFirst().addNumber();
        } else if (event.postiRichiesti == 4){
            nodes.get(1).setArrivalEvent(event);
            nodes.get(1).addNumber();
        }else{
            nodes.get(2).setArrivalEvent(event);
            nodes.get(2).addNumber();
        }
    }
    private final ReplicationStats systemStats = new ReplicationStats();
}
