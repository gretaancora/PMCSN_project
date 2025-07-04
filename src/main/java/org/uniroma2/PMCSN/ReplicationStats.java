package org.uniroma2.PMCSN;
import org.uniroma2.PMCSN.Libs.Rvms;

import java.util.ArrayList;
import java.util.List;



public class ReplicationStats {
    private final List<Double> responseTimes = new ArrayList<>();
    private final List<Double> avgInNode      = new ArrayList<>();
    private final List<Double> waitingTimes   = new ArrayList<>();
    private final List<Double> avgInQueue     = new ArrayList<>();

    /*aggiunta per le batch means*/
    private final Rvms rvms = new Rvms();
    /*aggiunta per le batch means*/

    public List<Double> getUtilizations() {
        return utilizations;
    }

    private final List<Double> utilizations   = new ArrayList<>();

    /*aggiunta intervallo di confidenza */

    /*aggiunta intervallo di confidenza */
    public void insert(double eTs, double eNs, double eTq, double eNq, double rho) {
        responseTimes.add(eTs);
        avgInNode.add(eNs);
        waitingTimes.add(eTq);
        avgInQueue.add(eNq);
        utilizations.add(rho);
    }

    public double mean(List<Double> data) {
        return data.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    /*public double stdDev(List<Double> data) {
        double m = mean(data);
        return Math.sqrt(data.stream()
                .mapToDouble(d -> (d - m) * (d - m))
                .sum() / (data.size() - 1));
    }*/

    public void printFinalStats(String label) {
        System.out.printf("=== %s ===%n", label);
        printConfidenceInterval("E[T_S]", responseTimes);
        printConfidenceInterval("E[N_S]", avgInNode);
        printConfidenceInterval("E[T_Q]", waitingTimes);
        printConfidenceInterval("E[N_Q]", avgInQueue);
        printConfidenceInterval("Rho", utilizations);
    }

    /*aggiunta intervallo di confidenza */
    public void printConfidenceInterval(String name, List<Double> data) {
        int n = data.size();
        /* numero di batch nella simulazione */
        double mean = data.stream().mapToDouble(d -> d).average().orElse(0.0);
        /*media campionaria dei valori */
        double variance = data.stream().mapToDouble(d -> Math.pow(d - mean, 2)).sum() / (n - 1);
        /*varianza campionaria dei valori */
        double stdDev = Math.sqrt(variance);

        // Usa idfStudent da Rvms
        double u = 0.975; // per IC 95% → 2 code, 0.975 quantile
        double t = rvms.idfStudent(n - 1, u);

        double margin = t * stdDev / Math.sqrt(n);
        double lower = mean - margin;
        double upper = mean + margin;

        System.out.printf("%-5s : %.4f ± %.4f → IC95%% = [%.4f, %.4f]%n",
                name, mean, margin, lower, upper);
    }
    /*aggiunta intervallo di confidenza */
}
