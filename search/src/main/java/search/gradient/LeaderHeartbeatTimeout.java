package search.gradient;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class LeaderHeartbeatTimeout extends Timeout {

    public LeaderHeartbeatTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}
