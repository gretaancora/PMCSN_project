package org.uniroma2.PMCSN;

public interface Sistema {
    void runFiniteSimulation();
    void runInfiniteSimulation();
    void generateFeedback(MsqEvent event);
}
