package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

import java.util.ArrayList;
import java.util.List;

import static org.uniroma2.PMCSN.Libs.Distributions.*;

public class SimpleMultiserverNode implements Node{

    private static final int ARRIVAL = 0;
    private final int SERVERS;
    private double sarrival;    // orario cumulato per gli arrivi
    private final List<MsqEvent> event;   // event[0]=next arrival, [1..S]=server departures
    private final MsqSum[] sum;       // statistiche per ogni server
    private long number;        // job totali nel nodo (in servizio + in coda)
    private long index;         // contatore job processati
    private double area;        // integrale del numero in sistema
    private double areaQueue = 0.0;  // area sotto la curva dei job in coda
    private long queueJobs = 0;      // numero totale di job che hanno fatto coda
    private double currentTime;
    private final Rngs r;
    private static final double P_EXIT = 0.2;
    private static final double P_SMALL = 0.6;
    private static final double P_MEDIUM = 0.2;
    private static final double P_LARGE = 0.2;
    private final int centerIndex;
    private final Sistema system;
    private final ReplicationStats stats = new ReplicationStats();
    private double lastTotalService = 0.0;


    // Marker per snapshot a intervalli
    private double lastAreaSnapshot      = 0.0;
    private long   lastProcessedSnapshot = 0;
    private double lastQueueAreaSnapshot = 0.0;
    private long   lastQueueJobsSnapshot = 0;



    public SimpleMultiserverNode(Sistema system, int index, int servers, Rngs rng) {
        this.SERVERS = servers;
        this.r = rng;
        this.sarrival = 0.0;
        this.currentTime = 0.0;
        this.number = 0;
        this.index = 0;
        this.area = 0.0;
        this.centerIndex = index;
        this.system = system;
        this.lastTotalService = 0.0;



        // eventi e somme
        event = new ArrayList<>();
        sum = new MsqSum[servers + 2];


        for (int i = 0; i <= servers+1; i++) {
            event.add(i, new MsqEvent());
            sum[i] = new MsqSum();
            event.get(i).x = 0;
            sum[i].service = 0.0;
            sum[i].served = 0;
        }

        // schedulo il primo arrivo “esterno”
        event.get(ARRIVAL).t = getNextArrivalTime();
        event.get(ARRIVAL).x = 1;
    }

    // Espone il prossimo evento attivo
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < event.size(); i++)
            if (event.get(i).x == 1 && event.get(i).t < tmin)
                tmin = event.get(i).t;
        return tmin;
    }

    public int peekNextEventType() {
        int best = -1;
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < event.size(); i++)
            if (event.get(i).x == 1 && event.get(i).t < tmin) {
                tmin = event.get(i).t;
                best = i;
            }
        return best;
    }

    // Avanza la simulazione di questo nodo fino all'evento scelto
    // e restituisce eventuale DEPARTURE schedulato (server index), oppure -1.
    public int processNextEvent(double t) {
        int e = peekNextEventType();
        double tnext = event.get(e).t;
        // integrazione area
        area += (tnext - currentTime) * number;
        // se c'è coda, incrementa anche areaQueue
        if (number > SERVERS) {
            areaQueue += (tnext - currentTime) * (number - SERVERS);
        }
//        System.out.println("number: " + number);
//        System.out.println("tnext: " + tnext);
//        System.out.println("currenttime: " + currentTime);
      //  System.out.println("Area SIMPLE: " + area);
        currentTime = tnext;

        if (e == ARRIVAL || e>SERVERS) {
            if (e == ARRIVAL) {
                // ARRIVAL “esterno” o da routing
                number++;
                // programma il prossimo ARRIVAL esterno
                event.get(ARRIVAL).t = getNextArrivalTime();      // sarrival++;
                // System.out.println("Arrivo: " + sarrival);
                r.selectStream(2); /* stream per generare la p di loss */
                double pLoss = r.random();
                if (pLoss < P_EXIT) {
                    number--;
                    return -1;
                }
            }

            int srv = findOne();
            if (srv != -1) {
                if (number > SERVERS) queueJobs++; // è in coda
                double svc = getServiceTime();
                event.get(srv).t = currentTime + svc;
                event.get(srv).x = 1;
                sum[srv].service += svc;
                sum[srv].served++;
                //se ho processato arrivo esterno lo elimino dalla coda degli eventi
                if (e>SERVERS){
                    event.remove(e);
                }
                // non incremento number perché è in servizio, non in coda
                return srv;
            }

        } else {
            // DEPARTURE da server e = srv
            index++;
           // System.out.println("Completamento SIMPLE: " + index);
            number--;
            // coda non vuota?
            if (number >= SERVERS) {
                double svc = getServiceTime();
                event.get(e).t = currentTime + svc;
                sum[e].service += svc;
                sum[e].served++;
                return e;
            } else {
                event.get(e).x = 0;
            }
        }

        return -1;
    }



    public double getNextArrivalTime() {
        r.selectStream(0);
        double lambda = 1.65;

        if(system instanceof SimpleSystem) {
            switch (centerIndex) {
                case 0 -> lambda *= P_SMALL;
                case 1 -> lambda *= P_MEDIUM;
                case 2 -> lambda *= P_LARGE;
                default -> System.out.println("Centro inesistente!");
            }
        }else{
            switch (centerIndex) {
                case 0 -> lambda *= P_SMALL * 0.7;
                case 1 -> lambda *= P_MEDIUM * 0.7;
                case 2 -> lambda *= P_LARGE * 0.7;
                default -> System.out.println("Centro inesistente!");
            }
        }

        sarrival += exponential(1/lambda, r);
        //System.out.println("Arrivo a:" + sarrival);
        return sarrival;
    }


    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTime() {
        r.selectStream(1);
        //return uniform(2.0, 10.0, r);
        double alpha, beta;
        double a = 2;
        double b = 60;

        alpha = cdfNormal(20.0, 2.0, a);
        beta = cdfNormal(20.0, 2.0, b);

        double u = uniform(alpha, beta, r);
        //System.out.println("Servizio:" + idfNormal(30.0, 2.0, u));
        return idfNormal(20.0, 2.0, u);
        //return exponential(20.0,r);
    }


    public int findOne() {
        for (int s = 1; s <= SERVERS; s++) {
            if (event.get(s).x == 0) {
                return s;
            }
        }
        return -1;  // nessun server libero
    }


    // Metodi per statistiche a fine run
    public double getAvgInterArrival() {
        return event.get(ARRIVAL).t / index;
    }

    public double getAvgResponse() {
        return area / index;
    }

    public double getAvgNumInNode() {
        return area / currentTime;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public long getProcessedJobs() {
        return index;
    }

    public void setArrivalEvent(MsqEvent event) {
        this.event.add(event);
    }

    public void addNumber() {
        this.number++;
    }

    public double getAvgWaitingInQueue() {
        long totalJobs = getProcessedJobs();
        return totalJobs > 0 ? areaQueue / totalJobs : 0.0;
    }


    public double getAvgNumInQueue() {
        return areaQueue / currentTime;
    }

    public double getAreaQueue() {
        return areaQueue;
    }

    public long getQueueJobs() {
        return queueJobs;
    }


    public double getUtilization() {
        double busyTime = 0.0;
        for (int s = 1; s <= SERVERS; s++) {
            busyTime += sum[s].service;
        }
        return busyTime / (SERVERS * currentTime);
    }


    @Override
    public void collectStatistics(int replicaIndex) {
        double eTs = getAvgResponse();
        double eNs = getAvgNumInNode();
        double eTq = getAvgWaitingInQueue();
        double eNq = getAvgNumInQueue();
        double rho = getUtilization();
        stats.insert(eTs, eNs, eTq, eNq, rho);
    }


    @Override
    public void printFinalStats() {
        // stampa media ± 95% CI
        stats.printFinalStats("Node " + centerIndex);
    }

    @Override
    public ReplicationStats getStats() {
        return stats;
    }

    /**
     * Restituisce il servizio erogato (somma di sum[s].service) da
     * quando è stato registrato l'ultimo batch, e aggiorna il marcatore.
     */
    public double getIncrementalServiceTime() {
        // calcola il servizio totale corrente:
        double totalService = 0.0;
        for (int s = 1; s <= SERVERS; s++) {
            totalService += sum[s].service;
        }
        // differenza rispetto a quando è iniziato l'ultimo batch
        double delta = totalService - lastTotalService;
        // aggiorna il marcatore per il prossimo batch
        lastTotalService = totalService;
        return delta;
    }

    private long lastProcessedJobs = 0;

    public long getLastProcessedJobs() {
        return lastProcessedJobs;
    }

    public void setLastProcessedJobs(long value) {
        this.lastProcessedJobs = value;
    }




        // getters & setters
        public double getLastAreaSnapshot()                    { return lastAreaSnapshot; }
        public void   setLastAreaSnapshot(double v)            { lastAreaSnapshot = v; }
        public long   getLastProcessedSnapshot()               { return lastProcessedSnapshot; }
        public void   setLastProcessedSnapshot(long v)         { lastProcessedSnapshot = v; }
        public double getLastQueueAreaSnapshot()               { return lastQueueAreaSnapshot; }
        public void   setLastQueueAreaSnapshot(double v)       { lastQueueAreaSnapshot = v; }
        public long   getLastQueueJobsSnapshot()               { return lastQueueJobsSnapshot; }
        public void   setLastQueueJobsSnapshot(long v)         { lastQueueJobsSnapshot = v; }


    /**
     * Restituisce l’area sotto la curva del numero in sistema
     */
    public double getArea() {
        return area;
    }




        /**
         * Integra le aree fino al tempo t (t ≥ currentTime), senza generare alcun evento.
         */
        public void integrateTo(double t) {
            if (t <= currentTime) return;
            double dt = t - currentTime;
            // integrale numero in sistema
            area += dt * number;
            // integrale numero in coda
            if (number > SERVERS) {
                areaQueue += dt * (number - SERVERS);
            }
            currentTime = t;
        }



}
