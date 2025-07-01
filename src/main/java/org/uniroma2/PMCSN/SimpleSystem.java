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
    private static final double STOP = 1000.0;
    // Numero di server configurati per ciascun nodo
    public static final Integer[] SERVERS = {
            33,
            11,
            11
    };

    @Override
    public void runFiniteSimulation() {
        double totalProcessed = 0.0;
        double totalResponse = 0.0;
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
                totalResponse += node.getAvgResponse();
            }
        }

        // Stampa risultati medi
        double avgProcessedPerNode = totalProcessed / (REPLICAS * NODES);
        double avgResponsePerNode = totalResponse / (REPLICAS * NODES);

        System.out.println("=== SimpleSystem (Finite Simulation) ===");
        System.out.printf("Repliche: %d, Nodi per replica: %d%n", REPLICAS, NODES);
        System.out.printf("Avg. processed jobs per node: %.2f%n", avgProcessedPerNode);
        System.out.printf("Avg. response time per node:    %.2f%n", avgResponsePerNode);
    }


    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 1000;   // job per batch
        final int N_BATCHES  = 30;     // numero totale di batch

        System.out.println("=== SimpleSystem (Infinite Simulation – Batch Means) ===");
        System.out.printf("Nodi: %d, Batch size: %d, #Batch totali: %d%n",
                NODES, BATCH_SIZE, N_BATCHES);

        // inizializzo RNG una sola volta
        Rngs rng = new Rngs();
        rng.plantSeeds(1);

        // Istanzio tutti i nodi
        SimpleMultiserverNode[] nodes = new SimpleMultiserverNode[NODES];
        for (int i = 0; i < NODES; i++) {
            nodes[i] = new SimpleMultiserverNode(this, i, SERVERS[i], rng);
        }

        // Resetto BatchMeans
        BatchMeans.resetNBatch();
        BatchMeans.resetJobInBatch();

        List<Double> batchMeans = new ArrayList<>();
        double lastAreaSys = 0.0;   // area cumulata sistema fino a fine batch precedente

        // Finché non ho raccolto N_BATCHES batch
        while (BatchMeans.getNBatch() <= N_BATCHES) {
            // Trovo il prossimo evento fra tutti i nodi
            double tnext = Double.POSITIVE_INFINITY;
            int    nextNode = -1;
            for (int i = 0; i < NODES; i++) {
                double tn = nodes[i].peekNextEventTime();
                if (tn < tnext) {
                    tnext = tn;
                    nextNode = i;
                }
            }

            // Processa l'evento sul nodo scelto
            int srv = nodes[nextNode].processNextEvent(tnext);

            // Se è una DEPARTURE (server valido), conto un job nel batch
            if (srv >= 1 && srv <= SERVERS[nextNode]) {
                BatchMeans.incrementJobInBatch();
            }

            // Quando raggiungo BATCH_SIZE job, chiudo il batch
            if (BatchMeans.getJobInBatch() >= BATCH_SIZE) {
                // Calcolo l'area cumulata di tutto il sistema
                double areaSys = 0.0;
                long  jobsSys = 0;
                for (int i = 0; i < NODES; i++) {
                    // getAvgWait * getProcessedJobs = area del singolo nodo
                    areaSys += nodes[i].getAvgResponse() * nodes[i].getProcessedJobs();
                    jobsSys += nodes[i].getProcessedJobs();
                }
                // Somma dei tempi d'attesa di questo batch sul sistema
                double batchSum = areaSys - lastAreaSys;
                double meanResponse = batchSum / BATCH_SIZE;
                batchMeans.add(meanResponse);

                System.out.printf("Batch %2d chiuso: mean wait sistema = %.4f%n",
                        BatchMeans.getNBatch(), meanResponse);

                // Preparo il batch successivo
                lastAreaSys = areaSys;
                BatchMeans.incrementNBatch();
                BatchMeans.resetJobInBatch();
            }
        }

        // Calcolo media e varianza dei batch-means
        double meanOfMeans = batchMeans.stream()
                .mapToDouble(d -> d)
                .average()
                .orElse(0.0);
        double var = batchMeans.stream()
                .mapToDouble(d -> Math.pow(d - meanOfMeans, 2))
                .sum()
                / (batchMeans.size() - 1);

        System.out.println("\n=== Summary Infinite Simulation ===");
        System.out.printf("Totale batch: %d%n", N_BATCHES);
        System.out.printf("Avg. of batch-means response (sistema):     %.4f%n", meanOfMeans);
        System.out.printf("Avg. of batch-means variance (sistema): %.4f%n", var);
    }


    @Override
    public void generateFeedback(MsqEvent event) {
        //non usato in questo sistema
    }
}