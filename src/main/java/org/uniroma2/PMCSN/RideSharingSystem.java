package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

import java.util.ArrayList;
import java.util.List;

public class RideSharingSystem implements Sistema {
    // Numero di centri semplici nel sistema
    private static final int SIMPLE_CENTERS = 3;
    // Numero di centri ride-sharing nel sistema
    private static final int RIDE_CENTERS = 1;
    // Numero di repliche da effettuare
    private static final int REPLICAS = 50;
    // Tempo di stop della simulazione (orizzonte finito)
    private static final double STOP = 2000.0;
    // Numero di server configurati per ciascun nodo semplice
    public static final Integer[] SERVERS_SIMPLE = {
            20,
            20,
            20
    };
    private Rngs rng;
    private static List<Node> nodes = new ArrayList<>(4);

    public RideSharingSystem(){

        rng = new Rngs();

        //istanzia 3 centri semplici
        for (int i = 0; i < SIMPLE_CENTERS; i++) {
            SimpleMultiserverNode center = new SimpleMultiserverNode(SERVERS_SIMPLE[i], rng);
            nodes.add(center);
        }

        // istanzia centro ride sharing
        for (int j = 0; j < RIDE_CENTERS; j++) {
            RideSharingMultiserverNode rideNode = new RideSharingMultiserverNode(rng, this);
            nodes.add(rideNode);
        }
    }


    @Override
    public void runFiniteSimulation() {
        double totalProcessed = 0.0;
        double totalWaiting = 0.0;

        for (int rep = 0; rep < REPLICAS; rep++) {
            // Inizializza generatore RNG per ogni replica
            rng = new Rngs();
            rng.plantSeeds(rep + 1);

            while (true) {
                int j=0;
                double tmin = Double.MAX_VALUE;
                double tcurr;

                // Prendo indice centro con prossimo evento con time minore
                for (int i = 0; i < 4; i++) {
                    tcurr = nodes.get(i).peekNextEventTime();
                    if (tcurr < tmin) {
                        tmin = tcurr;
                        j=i;
                    }
                }

                if (tmin > STOP) break;

                //processo il prossimo evento
                nodes.get(j).processNextEvent(tmin);
            }

            // Raccogli statistiche
            totalProcessed += nodes.get(rep).getProcessedJobs();
            totalWaiting += nodes.get(rep).getAvgWait();

        }

        // Calcola medie
        double avgProcessed = totalProcessed / (REPLICAS * (RIDE_CENTERS + SIMPLE_CENTERS));
        double avgWaiting = totalWaiting / (REPLICAS * (RIDE_CENTERS + SIMPLE_CENTERS));

        // Stampa risultati
        System.out.println("=== RideSharingSystem (Finite Simulation) ===");
        System.out.printf("Repliche: %d%n", REPLICAS);
        System.out.printf("Simple Centers: %d, Ride-Sharing Centers: %d%n", SIMPLE_CENTERS, RIDE_CENTERS);
        System.out.printf("Avg. processed jobs per simple center: %.2f%n", avgProcessed);
        System.out.printf("Avg. wait time per simple center:    %.2f%n", avgWaiting);
    }

    @Override
    public void runInfiniteSimulation() {
        throw new UnsupportedOperationException("Infinite simulation not implemented for RideSharingSystem");
    }


    public void generateFeedback(MsqEvent event) {
        if (event.postiRichiesti < 4) {
            nodes.get(0).setArrivalEvent(event);
            nodes.get(0).addNumber();
        } else if (event.postiRichiesti == 4){
            nodes.get(1).setArrivalEvent(event);
            nodes.get(1).addNumber();
        }else{
            nodes.get(2).setArrivalEvent(event);
            nodes.get(2).addNumber();
        }
    }
}
