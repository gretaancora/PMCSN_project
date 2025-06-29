package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;
import static org.uniroma2.PMCSN.Libs.Distributions.*;

public class SimpleMultiserverNode implements Node{

    private static final int ARRIVAL = 0;
    private int SERVERS;
    private double sarrival;    // orario cumulato per gli arrivi
    private MsqEvent[] event;   // event[0]=next arrival, [1..S]=server departures
    private MsqSum[] sum;       // statistiche per ogni server
    private long number;        // job totali nel nodo (in servizio + in coda)
    private long index;         // contatore job processati
    private double area;        // integrale del numero in sistema
    private double currentTime;
    private Rngs r;
    private static double P_EXIT = 0.05;

    public SimpleMultiserverNode(int servers, Rngs rng) {
        this.SERVERS = servers;
        this.r = rng;
        this.sarrival = 0.0;
        this.currentTime = 0.0;
        this.number = 0;
        this.index = 0;
        this.area = 0.0;

        // eventi e somme
        event = new MsqEvent[servers + 2];
        sum = new MsqSum[servers + 2];


        for (int i = 0; i <= servers+1; i++) {
            event[i] = new MsqEvent();
            sum[i] = new MsqSum();
            event[i].x = 0;
            sum[i].service = 0.0;
            sum[i].served = 0;
        }

        // schedulo il primo arrivo “esterno”
        event[ARRIVAL].t = getNextArrivalTime();
        event[ARRIVAL].x = 1;
    }

    // Espone il prossimo evento attivo
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= SERVERS+1; i++)
            if (event[i].x == 1 && event[i].t < tmin)
                tmin = event[i].t;
        return tmin;
    }

    public int peekNextEventType() {
        int best = -1;
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= SERVERS+1; i++)
            if (event[i].x == 1 && event[i].t < tmin) {
                tmin = event[i].t;
                best = i;
            }
        return best;
    }

    // Avanza la simulazione di questo nodo fino all'evento scelto
    // e restituisce eventuale DEPARTURE schedulato (server index), oppure -1.
    public int processNextEvent(double t) {
        int e = peekNextEventType();
        double tnext = event[e].t;
        // integrazione area
        area += (tnext - currentTime) * number;
        currentTime = tnext;

        if (e <2) {
            if (e == ARRIVAL) {
                // ARRIVAL “esterno” o da routing
                number++;
                // programma il prossimo ARRIVAL esterno
                event[ARRIVAL].t = getNextArrivalTime();
                double pLoss = r.random();
                if (pLoss < P_EXIT) {
                    number--;
                }
            }

            int srv = findOne();
            if (srv != -1) {
                double svc = getServiceTime();
                event[srv].t = currentTime + svc;
                event[srv].x = 1;
                sum[srv].service += svc;
                sum[srv].served++;
                // non incremento number perché è in servizio, non in coda
                return srv;
            }

        } else {
            // DEPARTURE da server e = srv
            index++;
            number--;
            // coda non vuota?
            if (number >= SERVERS) {
                double svc = getServiceTime();
                event[e].t = currentTime + svc;
                sum[e].service += svc;
                sum[e].served++;
                return e;
            } else {
                event[e].x = 0;
            }
        }

        return -1;
    }



    public double getNextArrivalTime() {
        r.selectStream(0);
        sarrival += exponential(2.0, r);
        //System.out.println("Arrivo a:" + sarrival);
        return sarrival;
    }

    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTime() {
        r.selectStream(1);
        //return uniform(2.0, 10.0, r);
        double alpha, beta;
        double a = 1;
        double b = 60;

        alpha = cdfNormal(30.0, 2.0, a);
        beta = cdfNormal(30.0, 2.0, b);

        double u = uniform(alpha, beta, r);
        System.out.println("Servizio:" + idfNormal(30.0, 2.0, u));
        return idfNormal(30.0, 2.0, u);
    }


    public int findOne() {
        for (int s = 2; s <= SERVERS+1; s++) {
            if (event[s].x == 0) {
                return s;
            }
        }
        return -1;  // nessun server libero
    }


    // Metodi per statistiche a fine run
    public double getAvgInterArrival() {
        return event[ARRIVAL].t / index;
    }

    public double getAvgWait() {
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
        this.event[1] = event;
    }

    public void addNumber() {
        this.number++;
    }

}
