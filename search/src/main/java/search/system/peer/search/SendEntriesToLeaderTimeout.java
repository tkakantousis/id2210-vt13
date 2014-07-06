package search.system.peer.search;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class SendEntriesToLeaderTimeout extends Timeout {

    public SendEntriesToLeaderTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}
