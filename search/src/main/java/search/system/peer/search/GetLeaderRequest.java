package search.system.peer.search;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class GetLeaderRequest extends Timeout {

    public GetLeaderRequest(SchedulePeriodicTimeout request) {
        super(request);
    }
}
