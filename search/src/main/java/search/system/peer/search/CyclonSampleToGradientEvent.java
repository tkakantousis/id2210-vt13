package search.system.peer.search;

import common.peer.PeerAddress;
import java.util.ArrayList;
import java.util.List;
import se.sics.kompics.Event;

/**
 *
 * @author teo
 */
public class CyclonSampleToGradientEvent extends Event {

    private final List<PeerAddress> cyclonSample;

    public CyclonSampleToGradientEvent(List<PeerAddress> cyclonSample) {
        this.cyclonSample = cyclonSample;
    }

    public List<PeerAddress> getCyclonSample() {
        return cyclonSample;
    }
}
