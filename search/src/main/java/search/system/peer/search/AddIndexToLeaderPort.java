package search.system.peer.search;

import se.sics.kompics.PortType;

/**
 *
 * @author teo
 */
public class AddIndexToLeaderPort extends PortType {
    {
        positive(GetLeaderResponse.class);
        positive(EntriesToSearchEvent.class);
    }
}
