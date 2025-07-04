package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;
import org.uniroma2.PMCSN.Libs.Rvms;

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
    private static final double STOP = 250.0;
    // Numero di server configurati per ciascun nodo semplice
    public static final Integer[] SERVERS_SIMPLE = {
            23,
            6,
            6
    };
    private static final int SERVERS_RIDESHARING = 30;
    private Rngs rng;

    // Statistiche globali del sistema
    private final ReplicationStats systemStats = new ReplicationStats();

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
        final double REPORT_INTERVAL = 20.0;
        // per convenzione, useremo centerIndex=-1 nei report globali
        final int SYSTEM_INDEX = -1;

        for (int rep = 0; rep < REPLICAS; rep++) {
            // 1) Inizializza RNG e resetta stato nodi
            rng = new Rngs();
            rng.plantSeeds(rep + 1);
            for (Node n : nodes) {
                // Se serve, potresti dover ricreare i nodi qui o resettarli
                // (dipende da come gestisci lo stato interno).
            }

            double nextReportTime = REPORT_INTERVAL;               // ← NUOVO
            double sumAreaSys      = 0.0;  // cumulato area sistema
            long   sumJobsSys      = 0;    // cumulato job completati
            double sumAreaQueueSys = 0.0;  // cumulato area coda
            long   sumQueueJobsSys = 0;    // cumulato queue‐jobs

            // 2) Ciclo di simulazione fino a STOP
            while (true) {
                // Trova prossimo evento
                int    chosenIdx = 0;
                double tmin      = Double.POSITIVE_INFINITY;
                for (int i = 0; i < nodes.size(); i++) {
                    double t = nodes.get(i).peekNextEventTime();
                    if (t < tmin) {
                        tmin = t;
                        chosenIdx = i;
                    }
                }

                // Se siamo oltre STOP **e** oltre l’ultimo report, usciamo
                if (tmin > STOP && nextReportTime > STOP) {
                    break;
                }

                // Caso A: il report viene prima del prossimo evento
                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    // ← NUOVO: integra **tutti** i nodi fino a nextReportTime
                    for (Node n : nodes) {
                        if (n instanceof SimpleMultiserverNode) {
                            n.integrateTo(nextReportTime);
                        } else if (n instanceof RideSharingMultiserverNode) {
                            n.integrateTo(nextReportTime);
                        }
                    }

                    // ← NUOVO: calcolo cumulativo di sistema fino a qui
                    double cumAreaSys      = 0.0;
                    long   cumJobsSys      = 0;
                    double cumAreaQueueSys = 0.0;
                    long   cumQueueJobsSys = 0;
                    for (Node n : nodes) {
                        cumAreaSys      += n.getArea();
                        cumJobsSys      += n.getProcessedJobs();
                        cumAreaQueueSys += n.getAreaQueue();
                        cumQueueJobsSys += n.getQueueJobs();
                    }

                    double t = nextReportTime;
                    double cumETs = cumJobsSys  > 0 ? cumAreaSys      / cumJobsSys      : 0.0;
                    double cumENs = cumAreaSys  / t;
                    double cumETq = cumQueueJobsSys > 0 ? cumAreaQueueSys / cumQueueJobsSys : 0.0;
                    double cumENq = cumAreaQueueSys / t;
                    // global rho: media delle rho dei nodi
                    double cumRho = 0.0;
                    for (Node n : nodes) {
                        cumRho += n.getUtilization();
                    }
                    cumRho /= nodes.size();

                    // ← NUOVO: scrive il report globale a intervalli
                    FileCSVGenerator.writeIntervalData(
                            true,              // finite
                            rep + 1,           // seed
                            SYSTEM_INDEX,      // centerIndex = -1 → sistema
                            nextReportTime,    // Time
                            cumETs, cumENs, cumETq, cumENq, cumRho
                    );

                    nextReportTime += REPORT_INTERVAL;
                    continue;
                }

                // Caso B: evento prima di STOP e dei report pendenti
                if (tmin <= STOP) {
                    nodes.get(chosenIdx).processNextEvent(tmin);
                } else {
                    // nessun altro evento utile
                    break;
                }
            }

            // 3) Dopo STOP, raccogli le statistiche finali per ogni nodo
            double totalProcessed = 0.0;
            double totalWaiting   = 0.0;
            for (Node n : nodes) {
                totalProcessed += n.getProcessedJobs();
                totalWaiting   += n.getAvgResponse();
            }

            // 4) Calcola medie per nodo
            double avgProcessed = totalProcessed / nodes.size();
            double avgWaiting   = totalWaiting   / nodes.size();

            // 5) Stampa
            System.out.println("=== RideSharingSystem (Finite Simulation) ===");
            System.out.printf("Replica %d/%d%n", rep+1, REPLICAS);
            System.out.printf("Avg processed jobs per center: %.2f%n", avgProcessed);
            System.out.printf("Avg response  time per center: %.2f%n", avgWaiting);
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

    @Override
    public void runInfiniteSimulation() {
        final int BATCH_SIZE = 256;
        final int N_BATCHES  = 64;
        final int TOTAL_NODES = SIMPLE_CENTERS + RIDE_CENTERS;

        System.out.println("=== RideSharingSystem (Infinite Simulation – Batch Means) ===");
        System.out.printf("Nodi: %d (simple=%d, ride=%d), Batch size: %d, #Batch totali: %d%n",
                TOTAL_NODES, SIMPLE_CENTERS, RIDE_CENTERS, BATCH_SIZE, N_BATCHES);

        // Prepara CSV (header)
        FileCSVGenerator.writeInfiniteGlobal(0, 0, 0, 0, 0, 0);

        // Inizializza RNG e nodi
        Rngs rng = new Rngs();
        rng.plantSeeds(1);
        List<Node> nodesLoc = new ArrayList<>(TOTAL_NODES);
        for (int i = 0; i < SIMPLE_CENTERS; i++)
            nodesLoc.add(new SimpleMultiserverNode(this, i, SERVERS_SIMPLE[i], rng));
        for (int j = 0; j < RIDE_CENTERS; j++)
            nodesLoc.add(new RideSharingMultiserverNode(rng, this));

        int   batchCount    = 0;
        int   completions   = 0;
        double startBatch   = 0.0, endBatch = 0.0;

        /*aggiunte le liste per batch means */
        // Liste per batch means
        List<Double> etList = new ArrayList<>();
        List<Double> enList = new ArrayList<>();
        List<Double> etqList = new ArrayList<>();
        List<Double> enqList = new ArrayList<>();
        List<Double> rhoList = new ArrayList<>();
        /*aggiunte le liste per batch means */

        while (batchCount < N_BATCHES) {
            // 1) Trova prossimo evento
            double tnext = Double.POSITIVE_INFINITY;
            Node   chosen = null;
            for (Node n : nodesLoc) {
                double t = n.peekNextEventTime();
                if (t < tnext) {
                    tnext = t;
                    chosen = n;
                }
            }

            // ← CORRETTO: integra tutti i nodi fino a tnext
            for (Node n : nodesLoc) {
                if (n instanceof SimpleMultiserverNode)
                    n.integrateTo(tnext);
                else
                    n.integrateTo(tnext);
            }

            // 2) Processa l’evento “vincente”
            int srv = chosen.processNextEvent(tnext);
            if (srv >= 0) {
                if (completions == 0) startBatch = tnext; // inizio batch
                completions++;
                endBatch = tnext;                         // fine batch
            }

            // 3) Se chiudo un batch…
            if (completions >= BATCH_SIZE) {
                batchCount++;

                // ← CORRETTO: sommo le aree e i job di *tutti* i nodi
                double totalArea     = 0.0;
                double totalQueueA   = 0.0;
                long   totalJobs     = 0;
                long   totalQueueJobs= 0;
                for (Node n : nodesLoc) {
                    totalArea      += n.getArea();         // area integrata per nodo
                    totalQueueA    += n.getAreaQueue();
                    totalJobs      += n.getProcessedJobs();
                    totalQueueJobs += n.getQueueJobs();
                }

                double totalTime = endBatch;                          // ← FIX: tempo dall'inizio al termine di questo batch

                // metriche cumulative corrette:
                double cumETs = totalJobs   > 0      ? totalArea   / totalJobs   : 0.0;
                double cumENs = totalTime    > 0      ? totalArea   / totalTime   : 0.0;  // ← FIX
                double cumETq = totalQueueJobs > 0    ? totalQueueA / totalQueueJobs : 0.0;
                double cumENq = totalTime    > 0      ? totalQueueA / totalTime   : 0.0;  // ← FIX

                // utilizzo cumulativo
                double servInc = 0.0;
                for (Node n : nodesLoc) {
                    servInc += n.getIncrementalServiceTime();
                }
                int totalServers = Arrays.stream(SERVERS_SIMPLE).mapToInt(x->x).sum()
                        + RIDE_CENTERS * RideSharingMultiserverNode.getNumServersPerRide();

                double cumRho = totalTime > 0
                        ? (servInc / totalTime) / totalServers
                        : 0.0;  // ← FIX

                /*aggiunte le liste per batch means */
                // Salva il batch
                etList.add(cumETs);
                enList.add(cumENs);
                etqList.add(cumETq);
                enqList.add(cumENq);
                rhoList.add(cumRho);
                /*aggiunte le liste per batch means */

                // Scrivo il cumulativo su CSV
                FileCSVGenerator.writeInfiniteGlobal(
                        batchCount,
                        cumETs,
                        cumENs,
                        cumETq,
                        cumENq,
                        cumRho
                );

                // Reset per il prossimo batch
                completions = 0;
            }
        }

        /*aggiunte le liste per batch means */
        // Calcola e stampa intervalli di confidenza
        System.out.println("=== Intervalli di confidenza (95%) ===");

        systemStats.printConfidenceInterval("ETs", etList);
        systemStats.printConfidenceInterval("ENs", enList);
        systemStats.printConfidenceInterval("ETq", etqList);
        systemStats.printConfidenceInterval("ENq", enqList);
        systemStats.printConfidenceInterval("Rho", rhoList);
        /*aggiunte le liste per batch means */

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

}
