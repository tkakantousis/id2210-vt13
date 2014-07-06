package search.system.peer.search;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class EmptyBufferTimeout extends Timeout {

    public EmptyBufferTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}
