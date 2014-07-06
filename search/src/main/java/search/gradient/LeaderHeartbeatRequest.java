package search.gradient;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class LeaderHeartbeatRequest extends Message {

    private static final long serialVersionUID = -6815596117580962153L;
    private final PeerAddress source;
    private final PeerAddress destination;

    public LeaderHeartbeatRequest(PeerAddress source, PeerAddress destination) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
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


}
