package search.gradient;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class LEMessageSearch extends Message {

    private static final long serialVersionUID = -6815196147580962153L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final PeerAddress requester;
    private int TTL;

    public LEMessageSearch(PeerAddress source, PeerAddress destination, PeerAddress requester, int TTL) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.requester = requester;
        this.TTL = TTL;
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

    public PeerAddress getRequester() {
        return requester;
    }

    public int getTTL() {
        return TTL;
    }
    
}
