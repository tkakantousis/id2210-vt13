package search.gradient;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class LeaderHeartbeatResponse extends Message {

    private static final long serialVersionUID = -6815591234580962153L;
    private final PeerAddress source;
    private final PeerAddress destination;
    public LeaderHeartbeatResponse(PeerAddress source, PeerAddress destination) {
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
