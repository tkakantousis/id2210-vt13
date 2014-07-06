package search.gradient;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class LEMessageSearchFound extends Message {

    private static final long serialVersionUID = -6815596147580962153L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final PeerAddress leader;

    public LEMessageSearchFound(PeerAddress source, PeerAddress destination, PeerAddress leader) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.leader = leader;
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

    public PeerAddress getLeader() {
        return leader;
    }
}
