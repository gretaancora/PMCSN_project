package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

public class RideSharingSystem implements Sistema {
    // Numero di centri semplici nel sistema
    private static final int SIMPLE_CENTERS = 3;
    // Numero di centri ride-sharing nel sistema
    private static final int RIDE_CENTERS = 1;
    // Numero di repliche da effettuare
    private static final int REPLICAS = 50;
    // Tempo di stop della simulazione (orizzonte finito)
    private static final double STOP = 20000.0;
    // Numero di server per ciascun SimpleMultiserverNode
    private static final int SERVERS_SIMPLE = 2;
    // Numero di server per il RideSharingMultiserverNode
    private static final int SERVERS_RIDE = 4;

    @Override
    public void runFiniteSimulation() {
        double totalProcessedSimple = 0.0;
        double totalWaitingSimple = 0.0;
        double totalProcessedRide = 0.0;
        double totalWaitingRide = 0.0;

        for (int rep = 0; rep < REPLICAS; rep++) {
            // Inizializza generatore RNG per ogni replica
            Rngs rng = new Rngs();
            rng.plantSeeds(rep + 1);

            // Esegui i centri semplici
            for (int i = 0; i < SIMPLE_CENTERS; i++) {
                SimpleMultiserverNode center = new SimpleMultiserverNode(SERVERS_SIMPLE, rng);
                while (center.peekNextEventTime() < STOP) {
                    double tNext = center.peekNextEventTime();
                    center.processNextEvent(tNext);
                }
                totalProcessedSimple += center.getProcessedJobs();
                totalWaitingSimple += center.getAvgWait();
            }

            // Esegui il centro ride-sharing
            for (int j = 0; j < RIDE_CENTERS; j++) {
                RideSharingMultiserverNode rideNode = new RideSharingMultiserverNode(SERVERS_RIDE, rng);
                while (rideNode.peekNextEventTime() < STOP) {
                    double tNext = rideNode.peekNextEventTime();
                    rideNode.processNextEvent(tNext);
                }
                totalProcessedRide += rideNode.getProcessedJobs();
                totalWaitingRide += rideNode.getAvgWait();
            }
        }

        // Calcola medie
        double avgProcessedSimple = totalProcessedSimple / (REPLICAS * SIMPLE_CENTERS);
        double avgWaitingSimple = totalWaitingSimple / (REPLICAS * SIMPLE_CENTERS);
        double avgProcessedRide = totalProcessedRide / (REPLICAS * RIDE_CENTERS);
        double avgWaitingRide = totalWaitingRide / (REPLICAS * RIDE_CENTERS);

        // Stampa risultati
        System.out.println("=== RideSharingSystem (Finite Simulation) ===");
        System.out.printf("Repliche: %d%n", REPLICAS);
        System.out.printf("Simple Centers: %d, Ride-Sharing Centers: %d%n", SIMPLE_CENTERS, RIDE_CENTERS);
        System.out.printf("Avg. processed jobs per simple center: %.2f%n", avgProcessedSimple);
        System.out.printf("Avg. wait time per simple center:    %.2f%n", avgWaitingSimple);
        System.out.printf("Avg. processed jobs per ride center:   %.2f%n", avgProcessedRide);
        System.out.printf("Avg. wait time per ride center:      %.2f%n", avgWaitingRide);
    }

    @Override
    public void runInfiniteSimulation() {
        throw new UnsupportedOperationException("Infinite simulation not implemented for RideSharingSystem");
    }
}
