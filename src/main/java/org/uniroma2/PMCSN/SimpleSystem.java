package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

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
                SimpleMultiserverNode node = new SimpleMultiserverNode(i, SERVERS[i], rng);
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
        // Implementazione analoga con stop condizionato (es. numero di eventi)
        throw new UnsupportedOperationException("Infinite simulation not implemented");
    }

    @Override
    public void generateFeedback(MsqEvent event) {
        //non usato in questo sistema
    }
}