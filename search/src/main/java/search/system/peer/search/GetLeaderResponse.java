package search.system.peer.search;

import common.peer.PeerAddress;
import se.sics.kompics.Event;

public final class GetLeaderResponse extends Event {

    private final PeerAddress leader;

    public GetLeaderResponse(PeerAddress leader) {

        this.leader = leader;
    }

    public PeerAddress getLeader() {
        return leader;
    }
}
