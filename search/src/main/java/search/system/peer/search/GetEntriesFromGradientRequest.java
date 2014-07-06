package search.system.peer.search;

import se.sics.kompics.Event;

/**
 *
 * @author teo
 */
public class GetEntriesFromGradientRequest extends Event {

    private int maxEntry;

    public GetEntriesFromGradientRequest(int maxEntry) {
        this.maxEntry = maxEntry;
    }

    public int getMaxEntry() {
        return maxEntry;
    }
}
