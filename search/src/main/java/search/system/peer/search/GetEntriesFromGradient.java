package search.system.peer.search;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetEntriesFromGradient extends Message {

    private static final long serialVersionUID = -6815596147821962953L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private int lastMissingEntry;
    public GetEntriesFromGradient(PeerAddress source, PeerAddress destination, int lastMissingEntry) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.lastMissingEntry = lastMissingEntry;
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public PeerAddress getPeerSource() {
        return source;
    }

    public PeerAddress getPeerDestination() {
        return destination;
    }

    public int getLastMissingEntry() {
        return lastMissingEntry;
    }
    
}
