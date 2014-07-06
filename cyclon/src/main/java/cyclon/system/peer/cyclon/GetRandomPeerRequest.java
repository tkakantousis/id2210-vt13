package cyclon.system.peer.cyclon;

import common.peer.PeerAddress;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetRandomPeerRequest extends Message {

    private static final long serialVersionUID = -6815596147580962133L;
    private final PeerAddress source;
    private final PeerAddress destination;


    public GetRandomPeerRequest(PeerAddress source, PeerAddress destination) {
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
