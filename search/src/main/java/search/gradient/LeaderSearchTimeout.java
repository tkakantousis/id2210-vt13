package search.gradient;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author teo
 */
public class LeaderSearchTimeout extends Timeout {

    private boolean addingEntry;

    public LeaderSearchTimeout(SchedulePeriodicTimeout request, boolean addingEntry) {
        super(request);
        this.addingEntry = addingEntry;
    }

    public LeaderSearchTimeout(ScheduleTimeout request, boolean addingEntry) {
        super(request);
        this.addingEntry = addingEntry;
    }

    public boolean isAddingEntry() {
        return addingEntry;
    }
}
