package search.system.peer.search;

import common.peer.PeerAddress;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetPartitionEntriesResponse extends Message {

    private static final long serialVersionUID = -681559614321962966L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final String results;
    private final String requestID;

    public GetPartitionEntriesResponse(PeerAddress source, PeerAddress destination, String results, String requestID) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.results = results;
        this.requestID = requestID;
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

    public String getResults() {
        return results;
    }

    public String getRequestID() {
        return requestID;
    }
}
