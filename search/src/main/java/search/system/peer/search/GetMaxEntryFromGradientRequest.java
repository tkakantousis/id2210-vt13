package search.system.peer.search;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetMaxEntryFromGradientRequest extends Message {

    private static final long serialVersionUID = -681559614752734153L;
    private final PeerAddress source;
    private final PeerAddress destination;

    public GetMaxEntryFromGradientRequest(PeerAddress source, PeerAddress destination) {
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
