package org.uniroma2.PMCSN;

public interface Node {

    int processNextEvent(double t);
    double peekNextEventTime();
    int peekNextEventType();
    double getNextArrivalTime();
    double getServiceTime();
    double getAvgInterArrival();
    double getAvgResponse();
    long getProcessedJobs();
    double getCurrentTime();
    double getAvgNumInNode();
    int findOne();
    void setArrivalEvent(MsqEvent event);
    void addNumber();
    void collectStatistics(int replicaIndex);
    void printFinalStats();  // calcola medie + σ e stampa a video
    double getAreaQueue();
    long getQueueJobs();
    double getIncrementalServiceTime();
    ReplicationStats getStats();
    void integrateTo(double t);
    /**
     * Restituisce l’area sotto la curva del numero in sistema
     */
    double getArea();

    double getUtilization();
}
