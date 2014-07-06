package search.system.peer.search;

import common.peer.PeerAddress;
import java.util.List;
import java.util.Map;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class EntriesToLeaderMessage extends Message {

    private static final long serialVersionUID = -6815123147580962153L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private Map<Integer,String> entriesToLeader;

    public EntriesToLeaderMessage(PeerAddress source, PeerAddress destination, Map<Integer,String> entriesToLeader) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.entriesToLeader = entriesToLeader;
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

    public Map<Integer, String> getEntries() {
        return entriesToLeader;
    }
}
