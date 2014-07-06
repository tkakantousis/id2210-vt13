package search.gradient;

import common.peer.PeerAddress;
import java.util.SortedSet;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class ExchangeGradientRequest extends Message {

    private static final long serialVersionUID = -6815596199980962153L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final SortedSet<PeerAddress> similarSet;

    public ExchangeGradientRequest(PeerAddress source, PeerAddress destination, SortedSet<PeerAddress> similarSet) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.similarSet = similarSet;

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

    public SortedSet<PeerAddress> getSimilarSet() {
        return similarSet;
    }
}
