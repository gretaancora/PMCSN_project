package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;
import org.uniroma2.PMCSN.Utils.ReplicationStats;

import java.util.ArrayList;
import java.util.List;

import static org.uniroma2.PMCSN.Libs.Distributions.*;

public class SimpleMultiserverNode implements Node{

    private static final int ARRIVAL = 0;
    private final int SERVERS;
    private double sarrival;    // orario cumulato per gli arrivi
    private final List<MsqEvent> event;   // event[0]=next arrival, [1..S]=server departures
    private final MsqSum[] sum;       // statistiche per ogni server
    private final MsqT clock;
    private long number;        // job totali nel nodo (in servizio + in coda)
    private long index;         // contatore job processati
    private double area;        // integrale del numero in sistema
    private double areaQueue;  // area sotto la curva dei job in coda
    private double areaService; //area sotto la curva dei job in servizio

    private long queueJobs = 0;      // numero totale di job che hanno fatto coda
    private final Rngs r;
    private final int centerIndex;
    private final Sistema system;
    private final ReplicationStats stats = new ReplicationStats();
    private double lastTotalService;

    /*Constants*/
    private static final double P_EXIT = 0.2;
    private static final double P_SMALL = 0.6;
    private static final double P_MEDIUM = 0.2;
    private static final double P_LARGE = 0.2;

    public SimpleMultiserverNode(Sistema system, int index, int servers, Rngs rng) {
        this.SERVERS = servers;
        this.r = rng;
        this.sarrival = 0.0;
        this.number = 0;
        this.index = 0;
        this.area = 0.0;
        this.areaQueue = 0.0;
        this.areaService = 0.0;
        this.centerIndex = index;
        this.system = system;
        this.lastTotalService = 0.0;
        this.clock = new MsqT();


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
        clock.next = event.get(e).t;
        // integrazione area (tutti i job nel sistema)

        // integrazione aree
        double dt = clock.next - clock.current;
        /*area totale*/
        area += dt * number;

        // area servizio (fino a SERVERS o meno se ci sono meno job)
        int busyServers = (int) Math.min(number, SERVERS);
        areaService += dt * busyServers;

        // se c'è coda, incrementa anche areaQueue
        if (number > SERVERS) {
            areaQueue += (dt) * (number - SERVERS);
        }

        clock.current = clock.next;

        if (e == ARRIVAL || e>SERVERS) {
            if (e == ARRIVAL) {
                // ARRIVAL “esterno” o da routing
                number++;
                // programma il prossimo ARRIVAL esterno
                event.getFirst().t = getNextArrivalTime();
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
                event.get(srv).t = clock.current + svc;
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
                event.get(e).t = clock.current + svc;
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
        double lambda = 2.25;

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
        /* System.out.println("Arrivo a:" + sarrival); */
        return sarrival;
    }


    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTime() {
        r.selectStream(1);
        //return uniform(2.0, 10.0, r);
        double alpha, beta;
        double a = 2;
        double b = 60;

        alpha = cdfNormal(20.0, 10.0, a);
        beta = cdfNormal(20.0, 10.0, b);

        double u = uniform(alpha, beta, r);
        /*System.out.println("Servizio:" + idfNormal(20.0, 10.0, u));*/
        return idfNormal(20.0, 10.0, u);
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

    /* Metodi per statistiche a fine run */

    public double getAvgResponse() {
        return area / index;
    }

    public double getAvgNumInNode() {
        return area / clock.current;
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
        return areaQueue / clock.current;
    }

    public double getAreaQueue() {
        return areaQueue;
    }

    public long getQueueJobs() {
        return queueJobs;
    }

    /* aggiunta per area service */

    public double getAreaService() {
        return areaService;
    }

    public double getNumInService() {
        return Math.min(number, SERVERS);
    }

    /* aggiunta per area service */

    public double getUtilization() {
        double busyTime = 0.0;
        for (int s = 1; s <= SERVERS; s++) {
            busyTime += sum[s].service;
        }
        return busyTime / (SERVERS * clock.current);
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
        if (!(t > clock.current)) return;
        /*se t è minore o uguale al clock current non faccio nulla */
        double dt = t - clock.current;
        /* tempo trascorso dall'ultimo aggiornamento*/
        // integrale numero in sistema
        area += dt * number;
        /*base per altezza*/
        // integrale numero in coda
        if (number > SERVERS) {
            areaQueue += dt * (number - SERVERS);
        }

        // area servizio
        int busyServers = (int) Math.min(number, SERVERS);
        areaService += dt * busyServers;

        clock.current = t;
    }

    /** Azzera tutti i contatori e le aree per una nuova replica */
    public void resetState() {
        this.number = 0;
        this.index = 0;
        this.area = 0.0;
        this.areaQueue = 0.0;
        this.queueJobs = 0;
        this.clock.current = 0.0;
        this.clock.next = 0.0;
        this.lastTotalService = 0.0;
        // reset sum[] per server
        for (MsqSum s : sum) {
            s.service = 0.0;
            s.served = 0;
        }
        /* reset event times: schedule il primo arrivo */
        event.getFirst().t = getNextArrivalTime();
        event.getFirst().x = 1;
        // per semplicità, non tocchiamo pendingArrivals qui
    }
}
