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

}
