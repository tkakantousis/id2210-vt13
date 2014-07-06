package search.system.peer.search;

import java.util.List;
import java.util.Map;
import se.sics.kompics.Event;

/**
 *
 * @author teo
 */
public class EntriesToSearchEvent extends Event {
    private  Map<Integer,String> entriesToAdd;

    public EntriesToSearchEvent(Map<Integer,String>  entriesToAdd) {
        this.entriesToAdd = entriesToAdd;
    }

    public Map<Integer,String>  getEntries() {
        return entriesToAdd;
    }
    
}
