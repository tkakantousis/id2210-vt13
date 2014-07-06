package cyclon.system.peer.cyclon;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetRandomPeerResponse extends Message {

    private static final long serialVersionUID = -6815596147580922133L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final PeerAddress randomPeer;

    public PeerAddress getRandomPeer() {
        return randomPeer;
    }

    public GetRandomPeerResponse(PeerAddress source, PeerAddress destination, PeerAddress randomPeer) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.randomPeer = randomPeer;
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
