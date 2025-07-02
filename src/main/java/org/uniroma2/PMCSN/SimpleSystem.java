package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;
import java.util.ArrayList;
import java.util.List;

public class SimpleSystem implements Sistema{
    private static final int NODES    = 3;
    private static final int REPLICAS = 4;
    private static final double STOP  = 1000.0;
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

            // Simulo nodo per nodo
            for (int i = 0; i < NODES; i++) {
                SimpleMultiserverNode node = new SimpleMultiserverNode(this, i, SERVERS[i], rng);
                double tnext;
                while ((tnext = node.peekNextEventTime()) < STOP) {
                    node.processNextEvent(tnext);
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
        double   lastAreaSys  = 0.0;

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
            if (srv >= 0) jobsInBatch++;

            if (jobsInBatch >= BATCH_SIZE) {
                // Per ogni nodo calcolo il batch‐mean e lo inserisco
                double areaSys = 0.0;
                for (int i = 0; i < NODES; i++) {
                    SimpleMultiserverNode node = nodesLoc.get(i);
                    double areaNode = node.getAvgResponse() * node.getProcessedJobs();
                    double batchMeanNode = (areaNode - lastAreaNode[i]) / BATCH_SIZE;
                    nodeStats[i].insert(batchMeanNode, batchMeanNode, batchMeanNode, batchMeanNode, batchMeanNode);
                    lastAreaNode[i] = areaNode;
                    areaSys += areaNode;
                }
                // Calcolo batch-mean di sistema e lo inserisco
                double batchMeanSys = (areaSys - lastAreaSys) / (BATCH_SIZE * NODES);
                // Popolazione media di sistema per batch
                double ensSys = areaSys / ((batchCount + 1) * NODES);

                systemStats.insert(
                        batchMeanSys,    // ETs
                        ensSys,          // ENS
                        batchMeanSys,    // ETq (se lo interpreti come risposta)
                        ensSys,          // ENq
                        batchMeanSys     // Rho proxy
                );
                lastAreaSys = areaSys;

                FileCSVGenerator.writeRepData(
                        false,          // isFinite = false
                        1,              // seed fisso (puoi modificare se vuoi)
                        -1,             // centerIndex = -1 per indicare "sistema"
                        batchCount + 1, // runNumber = numero batch
                        STOP,
                        batchMeanSys,
                        ensSys,
                        batchMeanSys,
                        ensSys,
                        batchMeanSys
                );

                // preparo il batch successivo
                jobsInBatch = 0;
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