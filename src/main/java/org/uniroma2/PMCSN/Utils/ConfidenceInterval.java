package org.uniroma2.PMCSN.Utils;

import org.uniroma2.PMCSN.Libs.Rvms;
import java.util.List;

/**
 * Utility per costruire intervalli di confidenza per la media
 * usando t-Student. Internamente usa Welford per stabilità numerica.
 */
public class ConfidenceInterval {

    /**
     * Calcola l'intervallo [lower, upper] al livello (1–α)×100%.
     *
     * @param data  lista delle osservazioni
     * @param alpha significatività (es. 0.05 → IC95%)
     * @param rvms  istanza di Rvms per idfStudent(df, p)
     * @return array {lower, upper}
     */
    public static double[] compute(List<Double> data, double alpha, Rvms rvms) {
        int n = data.size();
        if (n < 2) {
            throw new IllegalArgumentException("Servono ≥2 osservazioni per l'intervallo.");
        }

        // 1) Calcolo media e varianza con Welford
        Welford w = new Welford();
        for (double x : data) {
            w.accept(x);
        }
        double mean   = w.getMean();
        double stdDev = Math.sqrt(w.getSampleVariance());

        // 2) Trova t* per (1−α/2) con df = n−1
        double p     = 1 - alpha / 2.0;
        double tStar = rvms.idfStudent(n - 1, p);

        // 3) Calcola il margine
        double margin = tStar * stdDev / Math.sqrt(n);

        return new double[]{ mean - margin, mean + margin };
    }
}
