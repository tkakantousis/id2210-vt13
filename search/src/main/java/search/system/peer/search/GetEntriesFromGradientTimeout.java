package search.system.peer.search;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class GetEntriesFromGradientTimeout extends Timeout{

    public GetEntriesFromGradientTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}
