package search.system.peer.search;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class SendWebresponseTimeout extends Timeout{

    public SendWebresponseTimeout(ScheduleTimeout request) {
        super(request);
    }
    public SendWebresponseTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }

}
