package org.uniroma2.PMCSN;

import java.util.ArrayList;
import java.util.List;

public class ReplicationStats {
    private final List<Double> responseTimes = new ArrayList<>();
    private final List<Double> avgInNode      = new ArrayList<>();
    private final List<Double> waitingTimes   = new ArrayList<>();
    private final List<Double> avgInQueue     = new ArrayList<>();

    public List<Double> getUtilizations() {
        return utilizations;
    }

    private final List<Double> utilizations   = new ArrayList<>();

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

    public double stdDev(List<Double> data) {
        double m = mean(data);
        return Math.sqrt(data.stream()
                .mapToDouble(d -> (d - m) * (d - m))
                .sum() / (data.size() - 1));
    }

    public void printFinalStats(String label) {
        System.out.printf("%s E[T_S]=%.5f ± %.5f%n",
                label, mean(responseTimes), 1.96 * stdDev(responseTimes) / Math.sqrt(responseTimes.size()));
        System.out.printf("%s E[N_S]=%.5f ± %.5f%n",
                label, mean(avgInNode),      1.96 * stdDev(avgInNode)      / Math.sqrt(avgInNode.size()));
        System.out.printf("%s E[T_Q]=%.5f ± %.5f%n",
                label, mean(waitingTimes),   1.96 * stdDev(waitingTimes)   / Math.sqrt(waitingTimes.size()));
        System.out.printf("%s E[N_Q]=%.5f ± %.5f%n",
                label, mean(avgInQueue),     1.96 * stdDev(avgInQueue)     / Math.sqrt(avgInQueue.size()));
        System.out.printf("%s Rho   =%.5f ± %.5f%n",
                label, mean(utilizations),   1.96 * stdDev(utilizations)   / Math.sqrt(utilizations.size()));
    }
}
