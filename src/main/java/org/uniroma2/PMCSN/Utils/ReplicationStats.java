package org.uniroma2.PMCSN.Utils;

import org.uniroma2.PMCSN.Libs.Rvms;
import java.util.ArrayList;
import java.util.List;

/**
 * Raccoglie batch-means globali di simulazione e stampa
 * un set di intervalli di confidenza usando ConfidenceInterval.
 */
public class ReplicationStats {
    private final List<Double> responseTimes = new ArrayList<>();
    private final List<Double> avgInNode      = new ArrayList<>();
    private final List<Double> waitingTimes   = new ArrayList<>();
    private final List<Double> avgInQueue     = new ArrayList<>();
    private final List<Double> utilizations   = new ArrayList<>();
    private final Rvms rvms = new Rvms();

    /** Inserisce i batch-means globali di un singolo batch. */
    public void insert(double eTs, double eNs, double eTq, double eNq, double rho) {
        responseTimes.add(eTs);
        avgInNode.   add(eNs);
        waitingTimes.add(eTq);
        avgInQueue.  add(eNq);
        utilizations.add(rho);
    }

    /**
     * Stampa l'intervallo di confidenza (1−α)×100% per una metrica.
     *
     * @param name  etichetta
     * @param data  lista di valori
     * @param alpha significatività
     */
    public void printConfidenceInterval(String name, List<Double> data, double alpha) {
        try {
            double[] interval = ConfidenceInterval.compute(data, alpha, rvms);
            // ricalcolo media per stampare il margine
            Welford w = new Welford();
            data.forEach(w::accept);
            double mean   = w.getMean();
            double margin = (interval[1] - interval[0]) / 2.0;

            System.out.printf(
                    "%-5s : %.4f ± %.4f → IC%.0f%% = [%.4f, %.4f]%n",
                    name, mean, margin, (1 - alpha) * 100,
                    interval[0], interval[1]
            );
        } catch (IllegalArgumentException ex) {
            System.out.printf("%-5s : dati insufficienti per IC%n", name);
        }
    }

    /** Stampa tutti e cinque gli intervalli con un solo richiamo. */
    public void printAllConfidenceIntervals(double alpha) {
        printConfidenceInterval("ETs", responseTimes, alpha);
        printConfidenceInterval("ENs", avgInNode,      alpha);
        printConfidenceInterval("ETq", waitingTimes,   alpha);
        printConfidenceInterval("ENq", avgInQueue,     alpha);
        printConfidenceInterval("Rho", utilizations,   alpha);
    }
}
