package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.Libs.Rngs;

public class RideSharingMultiserverNode extends SimpleMultiserverNode {

    private MsqEvent[] event;   // event[0]=next arrival, [1..S]=server departures

    public RideSharingMultiserverNode(int servers, Rngs rng) {
        super(servers, rng);
    }

    @Override
    // trova server libero
    public int findOne() {
        int s=1; //in 0 abbiamo arrivo

        while (true){
            if (event[s++].x == 0) break;  //trova il primo libero
        }

        return s;
    }
}
