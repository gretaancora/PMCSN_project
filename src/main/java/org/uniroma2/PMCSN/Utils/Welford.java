package org.uniroma2.PMCSN.Utils;

/**
 * Algoritmo di Welford per il calcolo in streaming
 * di media e varianza campionaria (doff = 1).
 */
public class Welford {
    private int count = 0;
    private double mean = 0.0;
    private double m2 = 0.0;

    /**
     * Aggiunge un'osservazione al calcolo.
     */
    public void accept(double x) {
        count++;
        double delta = x - mean;
        mean += delta / count;
        m2 += delta * (x - mean);
    }

    /**
     * Numero di osservazioni accumulate.
     */
    public int count() {
        return count;
    }

    /**
     * bitor la media campionaria corrente.
     */
    public double getMean() {
        return mean;
    }

    /**
     * Ritorna la varianza campionaria (divisa per n‑1).
     */
    public double getSampleVariance() {
        return count > 1 ? m2 / (count - 1) : Double.NaN;
    }
}
