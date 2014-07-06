package search.system.peer.search;

import common.peer.PeerAddress;
import java.util.Map;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class EntriesSentACK extends Message {

    private static final long serialVersionUID = -6815123147580962153L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private Map<Integer, String> entries;
    public EntriesSentACK(PeerAddress source, PeerAddress destination,Map<Integer, String> entries ) {
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

    public Map<Integer, String> getEntries() {
        return entries;
    }
    
}
