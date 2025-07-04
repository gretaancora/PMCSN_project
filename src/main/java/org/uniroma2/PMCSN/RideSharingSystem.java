package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
            23,
            6,
            6
    };
    private static final int SERVERS_RIDESHARING = 30;
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

        for (int rep = 1; rep <= REPLICAS; rep++) {
            // 1) Inizializza RNG
            rng = new Rngs();
            rng.plantSeeds(rep);

            // 2) Ricrea i nodi "puliti" per questa replica
            List<Node> localNodes = new ArrayList<>();
            for (int i = 0; i < SIMPLE_CENTERS; i++) {
                SimpleMultiserverNode n = new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng);
                n.resetState();               // li inizializzo a zero
                localNodes.add(n);
            }
            for (int j = 0; j < RIDE_CENTERS; j++) {
                RideSharingMultiserverNode n = new RideSharingMultiserverNode(rng, this);
                n.resetState();               // li inizializzo a zero
                localNodes.add(n);
            }

            // 3) Prepara il reporting a intervalli
            double nextReportTime   = REPORT_INTERVAL;

            // 4) Loop eventi fino a STOP
            while (true) {
                // trova il prossimo evento
                double tmin = Double.POSITIVE_INFINITY;
                int    idx  = 0;
                for (int i = 0; i < localNodes.size(); i++) {
                    double t = localNodes.get(i).peekNextEventTime();
                    if (t < tmin) {
                        tmin = t;
                        idx  = i;
                    }
                }

                // se non ci sono più eventi e ho già superato STOP e l'ultimo report
                if (tmin > STOP && nextReportTime > STOP) break;

                // caso report prima del prossimo evento
                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    // integra tutti i nodi fino a nextReportTime
                    for (Node n : localNodes) {
                        if (n instanceof SimpleMultiserverNode) {
                            n.integrateTo(nextReportTime);
                        } else {
                            n.integrateTo(nextReportTime);
                        }
                    }
                    // calcola cumulativi di sistema
                    double cumArea      = 0.0;
                    long   cumJobs      = 0;
                    double cumAreaQueue = 0.0;
                    long   cumQJobs     = 0;
                    for (Node n : localNodes) {
                        cumArea      += n.getArea();
                        cumJobs      += n.getProcessedJobs();
                        cumAreaQueue += n.getAreaQueue();
                        cumQJobs     += n.getQueueJobs();
                    }
                    double t = nextReportTime;
                    double cumETs = cumJobs > 0 ? cumArea / cumJobs : 0.0;
                    double cumENs = cumArea / t;
                    double cumETq = cumQJobs > 0 ? cumAreaQueue / cumQJobs : 0.0;
                    double cumENq = cumAreaQueue / t;
                    // rho medio dei nodi
                    double cumRho = 0;
                    for (Node n : localNodes) cumRho += n.getUtilization();
                    cumRho /= localNodes.size();

                    FileCSVGenerator.writeIntervalData(
                            true,
                            rep,             // seed = numero di replica
                            SYSTEM_INDEX,    // -1 per sistema global
                            t,
                            cumETs, cumENs, cumETq, cumENq, cumRho
                    );

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

            // 5) statistiche finali di replica
            double procSum = 0, respSum = 0;
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



    /*@Override
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
            for (int i = 0; i < 4; i++) {
                totalProcessed += nodes.get(i).getProcessedJobs();
                totalWaiting += nodes.get(i).getAvgResponse();
            }
        }

        // Calcola medie
        double avgProcessed = totalProcessed / (REPLICAS * (RIDE_CENTERS + SIMPLE_CENTERS));
        double avgWaiting = totalWaiting / (REPLICAS * (RIDE_CENTERS + SIMPLE_CENTERS));

        // Stampa risultati
        System.out.println("=== RideSharingSystem (Finite Simulation) ===");
        System.out.printf("Repliche: %d%n", REPLICAS);
        System.out.printf("Simple Centers: %d, Ride-Sharing Centers: %d%n", SIMPLE_CENTERS, RIDE_CENTERS);
        System.out.printf("Avg. processed jobs per center: %.2f%n", avgProcessed);
        System.out.printf("Avg. response time per center:    %.2f%n", avgWaiting);
    }*/
/*
    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 256;   // job per batch
        final int N_BATCHES  = 64;     // numero totale di batch

        System.out.println("=== SimpleSystem (Infinite Simulation – Batch Means) ===");
        System.out.printf("Nodi: %d, Batch size: %d, #Batch totali: %d%n",
                SIMPLE_CENTERS+RIDE_CENTERS, BATCH_SIZE, N_BATCHES);

        // inizializzo RNG una sola volta
        Rngs rng = new Rngs();
        rng.plantSeeds(1);

        Node[] nodes = new Node[SIMPLE_CENTERS + RIDE_CENTERS];

        for (int i = 0; i < SIMPLE_CENTERS; i++) {
            nodes[i] = new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng);
        }

        for (int i = 0; i < RIDE_CENTERS; i++) {
            nodes[SIMPLE_CENTERS + i] = new RideSharingMultiserverNode(rng, this);
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
            int i;
            for (i = 0; i < SIMPLE_CENTERS+RIDE_CENTERS; i++) {
                double tn = nodes[i].peekNextEventTime();
                if (tn < tnext) {
                    tnext = tn;
                    nextNode = i;
                }
            }

            // Processa l'evento sul nodo scelto
            int srv = nodes[nextNode].processNextEvent(tnext);

            // Se è una DEPARTURE (server valido), conto un job nel batch
            if ((srv >= 1 && nextNode < SIMPLE_CENTERS && srv <= SERVERS_SIMPLE[nextNode]) || (srv >= 1 && nextNode < SIMPLE_CENTERS+RIDE_CENTERS && srv <= SERVERS_RIDESHARING)) {
                BatchMeans.incrementJobInBatch();
            }

            // Quando raggiungo BATCH_SIZE job, chiudo il batch
            if (BatchMeans.getJobInBatch() >= BATCH_SIZE) {
                // Calcolo l'area cumulata di tutto il sistema
                double areaSys = 0.0;
                long  jobsSys = 0;
                for (i = 0; i < SIMPLE_CENTERS+RIDE_CENTERS; i++) {
                    // getAvgWait * getProcessedJobs = area del singolo nodo
                    areaSys += nodes[i].getAvgResponse() * nodes[i].getProcessedJobs();
                    jobsSys += nodes[i].getProcessedJobs();
                }
                // Somma dei tempi d'attesa di questo batch sul sistema
                double batchSum = areaSys - lastAreaSys;
                double meanWait = batchSum / BATCH_SIZE;
                batchMeans.add(meanWait);

                System.out.printf("Batch %2d chiuso: mean wait sistema = %.4f%n",
                        BatchMeans.getNBatch(), meanWait);

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
        System.out.printf("Avg. of batch-means wait (sistema):     %.4f%n", meanOfMeans);
        System.out.printf("Avg. of batch-means variance (sistema): %.4f%n", var);
    }
*/

//    @Override
//    public void runInfiniteSimulation() {
//        final int BATCH_SIZE = 256;
//        final int N_BATCHES  = 64;
//        final int TOTAL_NODES = SIMPLE_CENTERS + RIDE_CENTERS;
//
//        System.out.println("=== RideSharingSystem (Infinite Simulation – Batch Means) ===");
//        System.out.printf("Nodi: %d (simple=%d, ride=%d), Batch size: %d, #Batch totali: %d%n",
//                TOTAL_NODES, SIMPLE_CENTERS, RIDE_CENTERS, BATCH_SIZE, N_BATCHES);
//
//        // Prepara CSV (header)
//        FileCSVGenerator.writeInfiniteGlobal(0, 0, 0, 0, 0, 0);
//
//        // Inizializza RNG e nodi
//        Rngs rng = new Rngs();
//        rng.plantSeeds(1);
//        List<Node> nodesLoc = new ArrayList<>(TOTAL_NODES);
//        for (int i = 0; i < SIMPLE_CENTERS; i++)
//            nodesLoc.add(new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng));
//        for (int j = 0; j < RIDE_CENTERS; j++)
//            nodesLoc.add(new RideSharingMultiserverNode(rng, this));
//
//        int   batchCount    = 0;
//        int   completions   = 0;
//        double startBatch   = 0.0, endBatch = 0.0;
//
//        while (batchCount < N_BATCHES) {
//            // 1) Trova prossimo evento
//            double tnext = Double.POSITIVE_INFINITY;
//            Node   chosen = null;
//            for (Node n : nodesLoc) {
//                double t = n.peekNextEventTime();
//                if (t < tnext) {
//                    tnext = t;
//                    chosen = n;
//                }
//            }
//
//            // ← CORRETTO: integra tutti i nodi fino a tnext
//            for (Node n : nodesLoc) {
//                if (n instanceof SimpleMultiserverNode)
//                    n.integrateTo(tnext);
//                else
//                    n.integrateTo(tnext);
//            }
//
//            // 2) Processa l’evento “vincente”
//            int srv = chosen.processNextEvent(tnext);
//            if (srv >= 0) {
//                if (completions == 0) startBatch = tnext; // inizio batch
//                completions++;
//                endBatch = tnext;                         // fine batch
//            }
//
//            // 3) Se chiudo un batch…
//            if (completions >= BATCH_SIZE) {
//                batchCount++;
//
//                // ← CORRETTO: sommo le aree e i job di *tutti* i nodi
//                double totalArea     = 0.0;
//                double totalQueueA   = 0.0;
//                long   totalJobs     = 0;
//                long   totalQueueJobs= 0;
//                for (Node n : nodesLoc) {
//                    totalArea      += n.getArea();         // area integrata per nodo
//                    totalQueueA    += n.getAreaQueue();
//                    totalJobs      += n.getProcessedJobs();
//                    totalQueueJobs += n.getQueueJobs();
//                }
//
//                double totalTime = endBatch;                          // ← FIX: tempo dall'inizio al termine di questo batch
//
//                // metriche cumulative corrette:
//                double cumETs = totalJobs   > 0      ? totalArea   / totalJobs   : 0.0;
//                double cumENs = totalTime    > 0      ? totalArea   / totalTime   : 0.0;  // ← FIX
//                double cumETq = totalQueueJobs > 0    ? totalQueueA / totalQueueJobs : 0.0;
//                double cumENq = totalTime    > 0      ? totalQueueA / totalTime   : 0.0;  // ← FIX
//
//                // utilizzo cumulativo
//                double servInc = 0.0;
//                for (Node n : nodesLoc) {
//                    servInc += n.getIncrementalServiceTime();
//                }
//                int totalServers = Arrays.stream(SERVERS_SIMPLE).mapToInt(x->x).sum()
//                        + RIDE_CENTERS * RideSharingMultiserverNode.getNumServersPerRide();
//
//                double cumRho = totalTime > 0
//                        ? (servInc / totalTime) / totalServers
//                        : 0.0;  // ← FIX
//
//                // Scrivo il cumulativo su CSV
//                FileCSVGenerator.writeInfiniteGlobal(
//                        batchCount,
//                        cumETs,
//                        cumENs,
//                        cumETq,
//                        cumENq,
//                        cumRho
//                );
//
//                // Reset per il prossimo batch
//                completions = 0;
//            }
//        }
//
//        System.out.println("=== Infinite Simulation – Fine ===");
//    }

    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 256;
        final int N_BATCHES  = 64;
        final int TOTAL_NODES = SIMPLE_CENTERS + RIDE_CENTERS;

        System.out.println("=== RideSharingSystem (Infinite Simulation – Batch Means – Simple Style) ===");
        System.out.printf("Nodi: %d (simple=%d, ride=%d), Batch size: %d, #Batch totali: %d%n",
                TOTAL_NODES, SIMPLE_CENTERS, RIDE_CENTERS, BATCH_SIZE, N_BATCHES);

        // Prepara CSV (header)
        FileCSVGenerator.writeInfiniteGlobal(0, 0, 0, 0, 0, 0);

        // Inizializza RNG e nodi
        Rngs rng = new Rngs();
        rng.plantSeeds(1);
        List<Node> nodesLoc = new ArrayList<>(TOTAL_NODES);
        for (int i = 0; i < SIMPLE_CENTERS; i++) {
            nodesLoc.add(new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng));
        }
        for (int j = 0; j < RIDE_CENTERS; j++) {
            nodesLoc.add(new RideSharingMultiserverNode(rng, this));
        }

        // Variabili cumulative globali
        double cumETs = 0.0, cumENs = 0.0, cumETq = 0.0, cumENq = 0.0, cumRho = 0.0;

        // Marker per delta batch
        double[] lastAreaNode      = new double[TOTAL_NODES];
        double[] lastAreaQueueNode = new double[TOTAL_NODES];
        long[]   lastProcessedJobs = new long[TOTAL_NODES];
        long[]   lastQueueJobs     = new long[TOTAL_NODES];
        double   lastAreaSys       = 0.0;
        double   lastAreaQueueSys  = 0.0;

        int batchCount    = 0;
        int completions   = 0;
        double startBatch = 0.0, endBatch = 0.0;

        while (batchCount < N_BATCHES) {
            // Trova prossimo evento
            double tnext = Double.POSITIVE_INFINITY;
            Node   chosen = null;
            for (Node n : nodesLoc) {
                double t = n.peekNextEventTime();
                if (t < tnext) {
                    tnext = t;
                    chosen = n;
                }
            }

            // Integra tutti i nodi fino a tnext
            for (Node n : nodesLoc) {
                n.integrateTo(tnext);
            }

            // Processa l’evento
            int srv = chosen.processNextEvent(tnext);
            if (srv >= 0) {
                if (completions == 0) {
                    startBatch = tnext;
                }
                completions++;
                endBatch = tnext;
            }

            // Chiudi batch
            if (completions >= BATCH_SIZE) {
                batchCount++;

                // Calcolo area e job cumulati
                double areaSys      = 0.0;
                double areaQueueSys = 0.0;
                long   totalJobs    = 0;
                long   totalQueueJobs = 0;
                int    batchJobsProcessed = 0;

                for (int i = 0; i < TOTAL_NODES; i++) {
                    Node node = nodesLoc.get(i);
                    double a   = node.getArea();
                    double aq  = node.getAreaQueue();
                    long  pj   = node.getProcessedJobs();
                    long  qj   = node.getQueueJobs();

                    areaSys        += a;
                    areaQueueSys   += aq;
                    totalJobs      += pj;
                    totalQueueJobs += qj;

                    int deltaProcessed = (int)(pj - lastProcessedJobs[i]);
                    batchJobsProcessed += deltaProcessed;

                    lastAreaNode[i]      = a;
                    lastAreaQueueNode[i] = aq;
                    lastProcessedJobs[i] = pj;
                    lastQueueJobs[i]     = qj;
                }

                // Metriche di batch
                double batchETs = batchJobsProcessed > 0
                        ? (areaSys - lastAreaSys) / batchJobsProcessed
                        : 0.0;
                double batchENs = (areaSys - lastAreaSys) / (endBatch - startBatch);
                long   queueJobsDelta = totalQueueJobs - Arrays.stream(lastQueueJobs).sum();
                double batchETq = queueJobsDelta > 0
                        ? (areaQueueSys - lastAreaQueueSys) / queueJobsDelta
                        : 0.0;
                double batchENq = (areaQueueSys - lastAreaQueueSys) / (endBatch - startBatch);

                // Rho di batch
                double servInc = 0.0;
                for (Node n : nodesLoc) {
                    servInc += n.getIncrementalServiceTime();
                }
                int totalServers = Arrays.stream(SERVERS_SIMPLE).mapToInt(x -> x).sum()
                        + RIDE_CENTERS * RideSharingMultiserverNode.getNumServersPerRide();
                double batchRho = (servInc / (endBatch - startBatch)) / totalServers;

                // Inserimento batch e aggiornamento cumulativi
                systemStats.insert(batchETs, batchENs, batchETq, batchENq, batchRho);
                cumETs += batchETs;
                cumENs += batchENs;
                cumETq += batchETq;
                cumENq += batchENq;
                cumRho += batchRho;

                // Scrittura medias globali su CSV
                FileCSVGenerator.writeInfiniteGlobal(
                        batchCount,
                        cumETs / batchCount,
                        cumENs / batchCount,
                        cumETq / batchCount,
                        cumENq / batchCount,
                        cumRho / batchCount
                );

                // Reset marker per batch successivo
                lastAreaSys      = areaSys;
                lastAreaQueueSys = areaQueueSys;
                completions      = 0;
            }
        }

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

    private final ReplicationStats   systemStats = new ReplicationStats();

}
