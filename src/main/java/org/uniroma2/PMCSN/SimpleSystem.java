package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SimpleSystem implements Sistema{
    // Numero di nodi nel sistema
    private static final int NODES = 3;
    // Numero di repliche da effettuare
    private static final int REPLICAS = 4;
    // Tempo di stop della simulazione (orizzonte finito)
    private static final double STOP = 2000.0;
    // Numero di server configurati per ciascun nodo
    public static final Integer[] SERVERS = {
            20,
            20,
            20
    };

    @Override
    public void runFiniteSimulation() {
        double totalProcessed = 0.0;
        double totalWaiting = 0.0;
        double tnext;

        for (int rep = 0; rep < REPLICAS; rep++) {
            // Inizializza generatore RNG per ogni replica
            Rngs rng = new Rngs();
            rng.plantSeeds(rep + 1); // imposta seed della replica

            for (int i = 0; i < NODES; i++) {

                // Crea il nodo multiserver con SERVERS[i] server
                SimpleMultiserverNode node = new SimpleMultiserverNode(this, i, SERVERS[i], rng);
                // Esegui eventi finché il prossimo evento è prima di STOP
                while ((tnext = node.peekNextEventTime()) < STOP) {
                    node.processNextEvent(tnext);
                }

                // Raccogli statistiche
                totalProcessed += node.getProcessedJobs();
                totalWaiting += node.getAvgWait();
            }
        }

        // Stampa risultati medi
        double avgProcessedPerNode = totalProcessed / (REPLICAS * NODES);
        double avgWaitingPerNode = totalWaiting / (REPLICAS * NODES);

        System.out.println("=== SimpleSystem (Finite Simulation) ===");
        System.out.printf("Repliche: %d, Nodi per replica: %d%n", REPLICAS, NODES);
        System.out.printf("Avg. processed jobs per node: %.2f%n", avgProcessedPerNode);
        System.out.printf("Avg. wait time per node:    %.2f%n", avgWaitingPerNode);
    }



    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 1000;   // job per batch
        final int N_BATCHES  = 30;     // numero di batch da raccogliere

        System.out.println("=== SimpleSystem (Infinite Simulation – Batch Means) ===");
        System.out.printf("Nodi: %d, Batch size: %d, #Batch: %d%n",
                NODES, BATCH_SIZE, N_BATCHES);

        // inizializzo RNG una sola volta
        Rngs rng = new Rngs();
        rng.plantSeeds(1);

        double totalMeanWait = 0.0;
        double totalVar      = 0.0;

        // per ogni nodo, un’unica simulazione "infinita"
        for (int nodeIdx = 0; nodeIdx < NODES; nodeIdx++) {
            // resetto i contatori batch e job
            BatchMeans.resetNBatch();
            BatchMeans.resetJobInBatch();

            SimpleMultiserverNode node = new SimpleMultiserverNode(this, nodeIdx, SERVERS[nodeIdx], rng);
            List<Double> batchMeans = new ArrayList<>();
            double    lastAreaCum  = 0.0;    // area cumulata al termine dell’ultimo batch

            // raccolgo N_BATCHES batch
            while (BatchMeans.getNBatch() <= N_BATCHES) {
                double tnext = node.peekNextEventTime();
                int    srv   = node.processNextEvent(tnext);

                // se è effettivamente una DEPARTURE (server 1…SERVERS)
                if (srv >= 1 && srv <= SERVERS[nodeIdx]) {
                    BatchMeans.incrementJobInBatch();
                }

                // quando ho servito BATCH_SIZE job, chiudo il batch
                if (BatchMeans.getJobInBatch() >= BATCH_SIZE) {
                    long   doneJobs = node.getProcessedJobs();
                    double areaCum  = node.getAvgWait() * doneJobs;
                    double batchSum = areaCum - lastAreaCum;
                    double meanWait = batchSum / BATCH_SIZE;
                    batchMeans.add(meanWait);

                    System.out.printf("Nodo %d – Batch %2d: mean wait = %.4f%n",
                            nodeIdx,
                            BatchMeans.getNBatch(),
                            meanWait);

                    // aggiorno contatori per il prossimo batch
                    lastAreaCum = areaCum;
                    BatchMeans.incrementNBatch();
                    BatchMeans.resetJobInBatch();
                }
            }

            // statistiche sui batch-means raccolti
            double meanOfMeans = batchMeans.stream()
                    .mapToDouble(d -> d)
                    .average()
                    .orElse(0.0);
            double var = batchMeans.stream()
                    .mapToDouble(d -> Math.pow(d - meanOfMeans, 2))
                    .sum()
                    / (batchMeans.size() - 1);

            totalMeanWait += meanOfMeans;
            totalVar      += var;

            System.out.printf("Nodo %2d – Mean of batch-means = %.4f, Variance = %.4f%n",
                    nodeIdx, meanOfMeans, var);
        }

        // riepilogo finale su tutti i nodi
        double avgMeanWait = totalMeanWait / NODES;
        double avgVar      = totalVar      / NODES;

        System.out.println("\n=== Summary Infinite Simulation ===");
        System.out.printf("Nodi: %d%n", NODES);
        System.out.printf("Avg. of batch-means wait per node:     %.4f%n", avgMeanWait);
        System.out.printf("Avg. of batch-means variance per node: %.4f%n", avgVar);
    }






    @Override
    public void generateFeedback(MsqEvent event) {
        //non usato in questo sistema
    }
}