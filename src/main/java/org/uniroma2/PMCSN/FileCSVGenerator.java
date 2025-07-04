package org.uniroma2.PMCSN;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class FileCSVGenerator {


    private static final String RESULT = "resources/results/";

    private static boolean ensureFile(String path) {
        File f = new File(path);
        if (!f.exists()) {
            try {
                f.getParentFile().mkdirs();
                f.createNewFile();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static void writeRepData(
            boolean isFinite,
            long seed,
            int centerIndex,
            int runNumber,
            double time,
            double eTs,
            double eNs,
            double eTq,
            double eNq,
            double rho
    ) {
        String suffix = isFinite ? "finite" : "infinite";
        String fileName = RESULT + suffix + "_center" + centerIndex + ".csv";
        boolean newFile = ensureFile(fileName);

        try (FileWriter fw = new FileWriter(fileName, true)) {
            if (newFile) {
                // header
                fw.append("Run,Seed,Center,Time,ETs,ENs,ETq,ENq,Rho\n");
            }
            fw.append(String.join(",",
                    String.valueOf(runNumber),
                    String.valueOf(seed),
                    String.valueOf(centerIndex),
                    String.format("%.2f", time),
                    String.format("%.5f", eTs),
                    String.format("%.5f", eNs),
                    String.format("%.5f", eTq),
                    String.format("%.5f", eNq),
                    String.format("%.5f", rho)
            ));
            fw.append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeIntervalData(
            boolean isFinite,
            long seed,
            int centerIndex,
            double time,
            double eTs,
            double eNs,
            double eTq,
            double eNq,
            double rho
    ) {
        String suffix = isFinite ? "finite_interval" : "infinite_interval";
        String fileName = RESULT + suffix + "_center" + centerIndex + ".csv";
        boolean newFile = ensureFile(fileName);

        try (FileWriter fw = new FileWriter(fileName, true)) {
            if (newFile) {
                fw.append("Seed,Center,Time,ETs,ENs,ETq,ENq,Rho\n");
            }
            fw.append(String.join(",",
                    String.valueOf(seed),
                    String.valueOf(centerIndex),
                    String.format(Locale.US,"%.2f", time),
                    String.format(Locale.US,"%.5f", eTs),
                    String.format(Locale.US,"%.5f", eNs),
                    String.format(Locale.US,"%.5f", eTq),
                    String.format(Locale.US,"%.5f", eNq),
                    String.format(Locale.US,"%.5f", rho)
            ));
            fw.append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Scrive la media cumulativa dei globali dei batch in infinite_global.csv
     * I campi sono separati da virgola e i decimali usano punto.
     * Ogni riga k contiene la media cumulativa fino al batch k.
     */
    public static void writeInfiniteGlobal(
            int batchNumber,
            double cumETs,
            double cumENs,
            double cumETq,
            double cumENq,
            double cumRho
    ) {
        String fileName = RESULT + "infinite_global.csv";
        boolean newFile = ensureFile(fileName);

        try (FileWriter fw = new FileWriter(fileName, true)) {
            if (newFile) {
                // header
                fw.append("Batch,ETs,ENs,ETq,ENq,Rho\n");
            }
            // riga cumulativa
            fw.append(String.join(",",
                    String.valueOf(batchNumber),
                    String.format(Locale.US, "%.5f", cumETs),
                    String.format(Locale.US, "%.5f", cumENs),
                    String.format(Locale.US, "%.5f", cumETq),
                    String.format(Locale.US, "%.5f", cumENq),
                    String.format(Locale.US, "%.5f", cumRho)
            ));
            fw.append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
