package search.system.peer.search;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetMaxEntryFromGradientResponse extends Message {

    private static final long serialVersionUID = -681559614752739953L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final int entryId;

    public GetMaxEntryFromGradientResponse(PeerAddress source, PeerAddress destination, int entryId) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.entryId = entryId;
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

    public int getEntryId() {
        return entryId;
    }
}
