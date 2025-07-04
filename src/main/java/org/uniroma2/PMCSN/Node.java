package org.uniroma2.PMCSN;

public interface Node {

    int processNextEvent(double t);
    double peekNextEventTime();
    int peekNextEventType();
    double getNextArrivalTime();
    double getServiceTime();
    double getAvgResponse();
    long getProcessedJobs();
    double getAvgNumInNode();
    int findOne();
    void setArrivalEvent(MsqEvent event);
    void addNumber();
    void collectStatistics(int replicaIndex);
    double getAreaQueue();
    long getQueueJobs();
    double getIncrementalServiceTime();
    void integrateTo(double t);
    /**
     * Restituisce l’area sotto la curva del numero in sistema
     */
    double getArea();

    double getUtilization();
}
