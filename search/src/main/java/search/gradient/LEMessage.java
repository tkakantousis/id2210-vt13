package search.gradient;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class LEMessage extends Message {

    private static final long serialVersionUID = -6815596147580962153L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final String type;

    public LEMessage(PeerAddress source, PeerAddress destination, String type) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.type = type;
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

    public String getType() {
        return type;
    }
    
    
}
