package org.uniroma2.PMCSN;

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
}
