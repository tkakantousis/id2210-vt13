package search.gradient;

import common.peer.PeerAddress;
import se.sics.kompics.Init;

/**
 *
 * @author teo
 */
public class GradientInit extends Init {

    private final PeerAddress self;
    private final int num;

    public GradientInit(PeerAddress peerSelf, int num) {
        this.self = peerSelf;
        this.num = num;
    }

    public PeerAddress getSelf() {
        return self;
    }

    public int getNum() {
        return num;
    }
}
