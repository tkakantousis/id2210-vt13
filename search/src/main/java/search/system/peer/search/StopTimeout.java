package search.system.peer.search;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class StopTimeout extends Timeout{

    public StopTimeout(ScheduleTimeout request) {
        super(request);
    }

}
