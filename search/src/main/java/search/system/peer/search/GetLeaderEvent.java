package search.system.peer.search;

import se.sics.kompics.Event;

/**
 *
 * @author teo
 */
public class GetLeaderEvent extends Event {

    private boolean triggeredBySendToLeader;

    public GetLeaderEvent(boolean triggeredBySendToLeader) {
        this.triggeredBySendToLeader = triggeredBySendToLeader;
    }

    public boolean isTriggeredBySendToLeader() {
        return triggeredBySendToLeader;
    }
}
