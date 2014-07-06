package search.system.peer.search;

import common.peer.PeerAddress;
import java.util.Map;
import se.sics.kompics.network.Message;

/**
 *
 * @author teo
 */
public class GetPartitionEntriesRequest extends Message {

    private static final long serialVersionUID = -681559614300962953L;
    private final PeerAddress source;
    private final PeerAddress destination;
    private final String requestID;
    private final String query;

    public GetPartitionEntriesRequest(PeerAddress source, PeerAddress destination, String requestID, String query) {
        super(source.getPeerAddress(), destination.getPeerAddress());
        this.source = source;
        this.destination = destination;
        this.requestID = requestID;
        this.query = query;
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

    public String getRequestID() {
        return requestID;
    }

    public String getQuery() {
        return query;
    }
}
