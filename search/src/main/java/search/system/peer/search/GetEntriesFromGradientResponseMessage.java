package search.system.peer.search;

import common.peer.PeerAddress;
import java.util.List;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetEntriesFromGradientResponseMessage extends Message {

    private static final long serialVersionUID = -6815596149921962953L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private List<IndexEntry> entries;
    public GetEntriesFromGradientResponseMessage(PeerAddress source, PeerAddress destination, List<IndexEntry> entries) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.entries = entries;
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

    public List<IndexEntry> getEntries() {
        return entries;
    }

   
    
}
