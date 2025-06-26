package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

import java.util.ArrayList;
import java.util.List;

import static org.uniroma2.PMCSN.Libs.Distributions.*;
import static org.uniroma2.PMCSN.Libs.Distributions.idfNormal;
import static org.uniroma2.PMCSN.RideSharingSystem.generateFeedback;

public class RideSharingMultiserverNode implements Node{

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
    private static double FEEDBACK = 0.15;
    private static double DELAY = 5;
    private static double TIME_WINDOW = 5;
    private static double SERVER_SMALL = 5;
    private static double SERVER_MEDIUM = 5;

    private static List<MsqEvent> pendingArrivals = new ArrayList<MsqEvent>();


    public RideSharingMultiserverNode(int servers, Rngs rng) {
        this.SERVERS = servers;
        this.r = rng;
        this.sarrival = 0.0;
        this.currentTime = 0.0;
        this.number = 0;
        this.index = 0;
        this.area = 0.0;

        // eventi e somme
        event = new MsqEvent[servers + 1];
        sum = new MsqSum[servers + 1];


        for (int i = 0; i <= servers; i++) {
            event[i] = new MsqEvent();
            if (i>0) {
                if (i < SERVER_SMALL) {
                    event[i].capacità = 3;
                } else if (i < SERVER_SMALL + SERVER_MEDIUM) {
                    event[i].capacità = 4;
                } else {
                    event[i].capacità = 8;
                }
            }
            sum[i] = new MsqSum();
            event[i].x = 0;
            sum[i].service = 0.0;
            sum[i].served = 0;
        }

        // schedulo il primo arrivo “esterno”
        event[ARRIVAL].t = getNextArrivalTime();
        event[ARRIVAL].x = 1;
    }

    // trova server libero
    //chi rimane fuori rimane nella coda pending e mettiamo in event[0]
    public int findOne() {
       int i=0;
        while (i<pendingArrivals.size()){
            match
       }

    }

    @Override
    public void generateNewFeedbackArrival() {
        //non utilizzato in questo tipo di centro
    }


    // Avanza la simulazione di questo nodo fino all'evento scelto
    // e restituisce eventuale DEPARTURE schedulato (server index), oppure -1.
    public int processNextEvent(double t) {
        int e = peekNextEventType();
        double tnext = event[e].t;
        // integrazione area
        area += (tnext - currentTime) * number;
        currentTime = tnext;

        if (e == ARRIVAL) {
            // ARRIVAL “esterno” o da routing
            number++;
            // programma il prossimo ARRIVAL esterno
            event[ARRIVAL].t = getNextArrivalTime();
            double pLoss = r.random();
            if (pLoss < P_EXIT) {
                number--;
            } else if (pLoss < FEEDBACK) {
                generateFeedback(event[ARRIVAL]);
            } else {
                int i=0;
                while(true){
                    pendingArrivals.add(i, new MsqEvent());
                    pendingArrivals.get(i).t = getNextArrivalTime();
                    pendingArrivals.get(i).x = 1;
                    if(pendingArrivals.get(i).t > currentTime + TIME_WINDOW) break;
                    i++;
                }
                findOne();
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

    // Espone il prossimo evento attivo
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= SERVERS; i++)
            if (event[i].x == 1 && event[i].t < tmin)
                tmin = event[i].t;
        return tmin;
    }

    public int peekNextEventType() {
        int best = -1;
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= SERVERS; i++)
            if (event[i].x == 1 && event[i].t < tmin) {
                tmin = event[i].t;
                best = i;
            }
        return best;
    }

    public double getNextArrivalTime() {
        r.selectStream(0);
        sarrival += exponential(2.0, r);
        return sarrival;
    }

    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTime() {
        r.selectStream(1);
        //return uniform(2.0, 10.0, r);
        double alpha, beta;
        double a = 1;
        double b = 60;

        alpha = cdfNormal(1.5, 2.0, a);
        beta = cdfNormal(1.5, 2.0, b);

        double u = uniform(alpha, 1.0-beta, r);
        return (idfNormal(1.5, 2.0, u)+DELAY);
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
}
