package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

import java.util.ArrayList;
import java.util.List;

import static org.uniroma2.PMCSN.Libs.Distributions.*;
import static org.uniroma2.PMCSN.Libs.Distributions.idfNormal;

public class RideSharingMultiserverNode implements Node{

    private static final int ARRIVAL = 0;
    private static final int SERVERS = 20;
    private double sarrival;    // orario cumulato per gli arrivi
    private final MsqEvent[] event;   // event[0]=next arrival, [1..S]=server departures
    private final MsqSum[] sum;       // statistiche per ogni server
    private final MsqT clock;
    private long number;        // job totali nel nodo (in servizio + in coda)
    private long index;         // contatore job processati
    private double area;        // integrale del numero in sistema
    private final Rngs r;
    private static final double P_EXIT = 0.2;
    private static final double FEEDBACK = 0.4;
    private static final double DELAY = 10;
    private static final double TIME_WINDOW = 5;
    private static final int SERVER_SMALL = 10;
    private static final int SERVER_MEDIUM = 5;
    private static final int SERVER_LARGE = 5;
    private static final double P_MATCH_BUSY = 0.6;
    private static final double P_MATCH_IDLE = 0.6;
    private static Sistema system;
    private double areaQueue = 0.0;  // area sotto la curva dei job in coda
    private double lastTotalService;
    private long queueJobs = 0;      // numero totale di job che hanno fatto coda



    private static List<MsqEvent> pendingArrivals = new ArrayList<MsqEvent>();


    public RideSharingMultiserverNode(Rngs rng, Sistema system) {
        this.r = rng;
        this.sarrival = 0.0;
        this.number = 0;
        this.index = 0;
        this.area = 0.0;
        this.lastTotalService = 0.0;
        this.clock = new MsqT();
        this.system = system;

        // eventi e somme
        event = new MsqEvent[SERVERS + 1];
        sum = new MsqSum[SERVERS + 1];


        for (int i = 0; i <= SERVERS; i++) {
            event[i] = new MsqEvent();
            if (i>0) {
                if (i < SERVER_SMALL) {
                    event[i].capacità = 3;
                    event[i].capacitàRimanente = 3;
                    event[i].numRichiesteServite = 0;
                } else if (i < SERVER_SMALL + SERVER_MEDIUM) {
                    event[i].capacità = 4;
                    event[i].capacitàRimanente = 4;
                    event[i].numRichiesteServite = 0;
                } else {
                    event[i].capacità = 8;
                    event[i].capacitàRimanente = 8;
                    event[i].numRichiesteServite = 0;
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
        event[ARRIVAL].postiRichiesti = getNumPosti();
    }

    public static int getNumServersPerRide() {
        return SERVERS;
    }

    /**
     * Tenta di servire quante più richieste possibili dalla coda pendingArrivals.
     * Prima prova su server già attivi (P_MATCH_BUSY), poi su server inattivi.
     * Ripete finché in un passaggio non viene servita almeno una richiesta.
     * @return numero di richieste servite in totale in questo invocazione di findOne()*/

    public int findOne() {
        int totalMatched = 0;
        boolean servedSomething;

        do {
            servedSomething = false;

            // Se non ci sono richieste pendenti, esco subito
            if (pendingArrivals.isEmpty()) {
                break;
            }

            // Faccio un “snapshot” della coda
            List<MsqEvent> snapshot = new ArrayList<>(pendingArrivals);

            // Scorro snapshot, ma rimuovo da pendingArrivals
            for (MsqEvent req : snapshot) {
                boolean matched = false;

                // 1) server attivi
                r.selectStream(3); // stream 3 per match con server attivi
                for (int i = 1; i <= SERVERS && !matched; i++) {
                    if (event[i].x == 1
                            && event[i].capacitàRimanente >= req.postiRichiesti
                            && r.random() < P_MATCH_BUSY) {

                        double svc = getServiceTime();
                        event[i].t = clock.current + svc;
                        event[i].svc = (event[i].svc * event[i].numRichiesteServite + svc)
                                / (event[i].numRichiesteServite + 1);
                        event[i].numRichiesteServite++;
                        event[i].capacitàRimanente -= req.postiRichiesti;
                        event[i].postiRichiesti += req.postiRichiesti;

                        matched = true;

                    }
                }

                // 2) server inattivi
                if (!matched) {
                    r.selectStream(4); // stream 4 per match con server inattivi
                    for (int i = 1; i <= SERVERS && !matched; i++) {
                        if (event[i].x == 0
                                && event[i].capacitàRimanente >= req.postiRichiesti && r.random() < P_MATCH_IDLE) {

                            double svc = getServiceTime();
                            event[i].t = clock.current + svc;
                            event[i].svc = (event[i].svc * event[i].numRichiesteServite + svc)
                                    / (event[i].numRichiesteServite + 1);
                            event[i].numRichiesteServite++;
                            event[i].x = 1;
                            event[i].capacitàRimanente -= req.postiRichiesti;
                            event[i].postiRichiesti += req.postiRichiesti;

                            matched = true;

                        }
                    }
                }


                if (matched) {
                    // rimuovo dal pending e aggiorno contatori
                    pendingArrivals.remove(req);
                    totalMatched++;
                    servedSomething = true;
                    break;  // esco dal for(snapshot) per ripartire da capo
                }
            }

        } while (servedSomething);


        return totalMatched;
    }


    @Override
    public void setArrivalEvent(MsqEvent event) {
        //non utilizzato in questo tipo di centro
    }

    @Override
    public void addNumber() {
        //non utilizzato in questo tipo di centro
    }

    @Override
    public void collectStatistics(int replicaIndex) {

    }

    // Avanza la simulazione di questo nodo fino all'evento scelto
    // e restituisce eventuale DEPARTURE schedulato (server index), oppure -1.
    public int processNextEvent(double t) {
        int e = peekNextEventType();
        clock.next = event[e].t;

        // integrazione area
        area += (clock.next - clock.current) * number;
        clock.current = clock.next;
        //System.out.println(e);
        if (e == ARRIVAL) {
            // ARRIVAL “esterno” o da routing
            number++;
            // programma il prossimo ARRIVAL esterno
            event[ARRIVAL].t = getNextArrivalTime();
            event[ARRIVAL].postiRichiesti = getNumPosti();
            pendingArrivals.add(event[ARRIVAL]);

            int i = 0;
            while (true) {
                pendingArrivals.add(i, new MsqEvent());
                pendingArrivals.get(i).t = getNextArrivalTime();
                pendingArrivals.get(i).x = 1;
                pendingArrivals.get(i).postiRichiesti = getNumPosti();
                if (pendingArrivals.get(i).t > clock.current + TIME_WINDOW){
                    pendingArrivals.remove(i);
                    break;
                }
                // Seleziono uno stream dedicato per probabilità di perdita/feedback
                r.selectStream(5);
                double pLoss = r.random();
                if (pLoss < P_EXIT) {
                    pendingArrivals.remove(i);
                    return -1;
                } else if (pLoss < P_EXIT + FEEDBACK) {
                    system.generateFeedback(pendingArrivals.get(i));
                    pendingArrivals.remove(i);
                    return -1;
                }else{
                    number++;
                    i++;
                }
            }

            findOne();

        } else {
            // DEPARTURE da server e
            int serverIndex = e-1;
            sum[serverIndex].service += event[e].svc;
            sum[serverIndex].served += event[e].numRichiesteServite;
            index += event[e].numRichiesteServite;
            number-=event[e].numRichiesteServite;
            event[e].x = 0;
            event[e].capacitàRimanente = event[e].capacità;
            event[e].numRichiesteServite = 0;
            event[e].postiRichiesti = 0;
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
        double lambda = 1.65 * 0.3;
        sarrival += exponential(1/lambda, r);
        return sarrival;
    }


    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTime() {
        r.selectStream(1);
        //return uniform(2.0, 10.0, r);
        double alpha, beta;
        double a = 1;
        double b = 60;

        alpha = cdfNormal(20.0, 2.0, a);
        beta = cdfNormal(20.0, 2.0, b);

        double u = uniform(alpha, beta, r);
        //System.out.println("Servizio: " + idfNormal(30, 2.0, u)+DELAY);
        return (idfNormal(20.0, 2.0, u)+DELAY);
    }


    public int getNumPosti(){
        double rand = r.random();
        if(rand<0.4){
            return 1;
        }if (rand<0.7){
            return 2;
        }if (rand<0.8){
            return 3;
        }if (rand<0.85){
            return 4;
        }if (rand<0.9){
            return 5;
        }if (rand<0.95){
            return 6    ;
        }else{
            return 7;
        }
    }


    // Metodi per statistiche a fine run
    public double getAvgInterArrival() {
        return event[ARRIVAL].t / index;
    }

    public double getAvgResponse() {
        //  System.out.println(area);
        //  System.out.println(index);
        //  System.out.println(area/index);
        return area / index;
    }

    public double getAvgNumInNode() {
        return area / clock.current;
    }

    public long getProcessedJobs() {
        return index;
    }

    public double getAreaQueue() {
        return areaQueue;
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

    public long getQueueJobs() {
        return queueJobs;
    }

    /**
     * Integra le aree fino al tempo t (t ≥ currentTime), senza generare alcun evento.
     */
    public void integrateTo(double t) {
        if (t <= clock.current) return;
        double dt = t - clock.current;
        // integrale numero in sistema
        area += dt * number;
        // integrale numero in coda
        if (number > SERVERS) {
            areaQueue += dt * (number - SERVERS);
        }
        clock.current = t;
    }

    /**
     * Restituisce l’area sotto la curva del numero in sistema
     */
    public double getArea() {
        return area;
    }

    @Override
    public double getUtilization() {
        // Somma del tempo di servizio erogato da ciascun server
        double busyTime = 0.0;
        for (int s = 1; s <= SERVERS; s++) {
            busyTime += sum[s].service;
        }
        // Capacità totale di posti (seat‐time per unità di tempo)
        int totalSeats = SERVER_SMALL * 3
                + SERVER_MEDIUM * 4
                + SERVER_LARGE * 8;
        return clock.current > 0.0
                ? busyTime / (totalSeats * clock.current)
                : 0.0;
    }

    public void resetState() {
        this.number      = 0;
        this.index       = 0;
        this.area        = 0.0;
        this.areaQueue   = 0.0;
        this.queueJobs   = 0;
        this.clock.current = 0.0;
        this.clock.next = 0.0;
        this.lastTotalService = 0.0;
        // reset sum per server
        for (MsqSum s : sum) {
            s.service = 0.0;
            s.served  = 0;
        }
        // rischedula primo arrivo
        event[ARRIVAL].t = getNextArrivalTime();
        event[ARRIVAL].x = 1;
        event[ARRIVAL].postiRichiesti = getNumPosti();
        // svuota la coda pendente
        pendingArrivals.clear();
    }
}
