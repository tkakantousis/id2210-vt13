package search.system.peer.search;

import common.peer.PeerAddress;
import java.util.List;
import se.sics.kompics.network.Message;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class MissingIndexEntries {

    public static class Request extends Message {

        List<Range> missingRanges;
        private final PeerAddress source;

        public Request(PeerAddress source, PeerAddress destination, List<Range> missingRanges) {
            super(source.getPeerAddress(), destination.getPeerAddress());
            this.source = source;
            this.missingRanges = missingRanges;
        }

        public List<Range> getMissingRanges() {
            return missingRanges;
        }

        public PeerAddress getPeerSource() {
            return source;
        }
    }

    public static class Response extends Message {

        private final PeerAddress source;
        private final List<IndexEntry> entries;

        public Response(PeerAddress source, PeerAddress destination, List<IndexEntry> entries) {
            super(source.getPeerAddress(), destination.getPeerAddress());
            this.source = source;
            assert (entries != null);
            this.entries = entries;
        }

        public List<IndexEntry> getEntries() {
            return entries;
        }

        public PeerAddress getPeerSource() {
            return source;
        }
    }

    public static class RequestTimeout extends Timeout {

        private final PeerAddress destination;

        RequestTimeout(ScheduleTimeout st, PeerAddress destination) {
            super(st);
            this.destination = destination;
        }

        public PeerAddress getDestination() {
            return destination;
        }
    }
}
