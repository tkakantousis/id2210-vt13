package search.gradient;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class LeaderElectionTimeout extends Timeout {

    public LeaderElectionTimeout(ScheduleTimeout request) {
        super(request);
    }
}
