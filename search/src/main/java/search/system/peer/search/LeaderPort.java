package search.system.peer.search;

import se.sics.kompics.PortType;

/**
 *
 * @author teo
 */
public class LeaderPort extends PortType{

    {
        positive(GetLeaderEvent.class);
        positive(CyclonSampleToGradientEvent.class);
        positive(GetEntriesFromGradientRequest.class);
        negative(GetLeaderResponse.class);
        negative(EntriesToSearchEvent.class);
    }
}
