package org.uniroma2.PMCSN;

public interface Node {

    int processNextEvent(double t);
    double peekNextEventTime();
    int peekNextEventType();
    double getNextArrivalTime();
    double getServiceTime();
    double getAvgInterArrival();
    double getAvgWait();
    long getProcessedJobs();
    double getCurrentTime();
    double getAvgNumInNode();
    int findOne();
    void generateNewFeedbackArrival();
    void setArrivalEvent(MsqEvent event);
    void addNumber();

}
