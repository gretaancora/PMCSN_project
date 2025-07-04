package org.uniroma2.PMCSN.Utils;

public class BatchMeans {
    private static int nBatch = 1;
    private static int nJonInBatch = 0;

    public static void incrementNBatch(){
        nBatch++;
    }

    public static void incrementJobInBatch(){
        nJonInBatch++;
    }

    public static int getJobInBatch(){
        return nJonInBatch;
    }

    public static int getNBatch(){
        return nBatch;
    }

    public static void resetJobInBatch(){
        nJonInBatch = 0;
    }
    public static void resetNBatch(){
        nBatch = 1;
    }
}
