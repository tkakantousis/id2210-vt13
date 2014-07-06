package cyclon.system.peer.cyclon;

import common.configuration.CyclonConfiguration;
import common.peer.PeerAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;

import cyclon.simulator.snapshot.Snapshot;
import java.util.Random;
import org.slf4j.LoggerFactory;

public final class Cyclon extends ComponentDefinition {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Cyclon.class);
    Negative<CyclonPort> cyclonPort = negative(CyclonPort.class);
    //Negative<CyclonPartnersPort> partnersPort = negative(CyclonPartnersPort.class);
    Negative<CyclonSamplePort> samplePort = negative(CyclonSamplePort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    private PeerAddress self;
    private int shuffleLength;
    private long shufflePeriod;
    private long shuffleTimeout;
    private Cache cache;
    private boolean joining;
    private CyclonConfiguration cyclonConfiguration;
    private HashMap<UUID, PeerAddress> outstandingRandomShuffles;

//-------------------------------------------------------------------	
    public Cyclon() {
        outstandingRandomShuffles = new HashMap<UUID, PeerAddress>();

        subscribe(handleInit, control);
        subscribe(handleJoin, cyclonPort);
        subscribe(handleInitiateShuffle, timerPort);
        subscribe(handleShuffleTimeout, timerPort);
        subscribe(handleShuffleRequest, networkPort);
        subscribe(handleShuffleResponse, networkPort);
        //subscribe(handlePartnersRequest, partnersPort);
        subscribe(handleGetRandomPeerRequest, networkPort);
    }
//-------------------------------------------------------------------	
    Handler<CyclonInit> handleInit = new Handler<CyclonInit>() {
        public void handle(CyclonInit init) {
            cyclonConfiguration = init.getConfiguration();
            shuffleLength = cyclonConfiguration.getShuffleLength();
            shufflePeriod = cyclonConfiguration.getShufflePeriod();
            shuffleTimeout = cyclonConfiguration.getShuffleTimeout();
        }
    };
//-------------------------------------------------------------------	
    /**
     * handles a request to join a Cyclon network using a set of introducer
     * nodes provided in the Join event.
     */
    Handler<CyclonJoin> handleJoin = new Handler<CyclonJoin>() {
        public void handle(CyclonJoin event) {
            self = event.getSelf();
            cache = new Cache(cyclonConfiguration.getRandomViewSize(), self);

            LinkedList<PeerAddress> insiders = event.getCyclonInsiders();

            if (insiders.size() == 0) {
                // I am the first peer
                trigger(new JoinCompleted(self), cyclonPort);

                // schedule shuffling
                SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(shufflePeriod, shufflePeriod);
                spt.setTimeoutEvent(new InitiateShuffle(spt));
                trigger(spt, timerPort);
                return;
            }

            PeerAddress peer = insiders.poll();
            initiateShuffle(1, peer);
            joining = true;
        }
    };

//-------------------------------------------------------------------	
    /**
     * initiates a shuffle of size
     * <code>shuffleSize</code>. Called either during the join protocol with a
     * <code>shuffleSize</code> of 1, or periodically to initiate regular
     * shuffles.
     *
     * @param shuffleSize
     * @param randomPeer
     */
    private void initiateShuffle(int shuffleSize, PeerAddress randomPeer) {
        // send the random view to a random peer
        ArrayList<PeerDescriptor> randomDescriptors = cache.selectToSendAtActive(shuffleSize - 1, randomPeer);
        randomDescriptors.add(new PeerDescriptor(self));
        DescriptorBuffer randomBuffer = new DescriptorBuffer(self, randomDescriptors);

        ScheduleTimeout rst = new ScheduleTimeout(shuffleTimeout);
        rst.setTimeoutEvent(new ShuffleTimeout(rst, randomPeer));
        UUID rTimeoutId = rst.getTimeoutEvent().getTimeoutId();

        outstandingRandomShuffles.put(rTimeoutId, randomPeer);
        ShuffleRequest rRequest = new ShuffleRequest(rTimeoutId, randomBuffer, self, randomPeer);

        trigger(rst, timerPort);
        trigger(rRequest, networkPort);
    }
//-------------------------------------------------------------------	
    /**
     * Periodically, will initiate regular shuffles. This is the first half of
     * the "active thread" of the Cyclon specification.
     */
    Handler<InitiateShuffle> handleInitiateShuffle = new Handler<InitiateShuffle>() {
        public void handle(InitiateShuffle event) {
            if (Snapshot.getDeadLeaders().contains(self.getPeerId())) {
                logger.info("Peer: " + self.getPeerId() + Snapshot.getDeadLeaders());
//                unsubscribe(handleInit, control);
//                unsubscribe(handleJoin, cyclonPort);
//                unsubscribe(handleInitiateShuffle, timerPort);
//                unsubscribe(handleShuffleTimeout, timerPort);
//                unsubscribe(handleShuffleRequest, networkPort);
//                unsubscribe(handleShuffleResponse, networkPort);
//                //subscribe(handlePartnersRequest, partnersPort);
//                unsubscribe(handleGetRandomPeerRequest, networkPort);
                int a = 5/0;
                return;
            }
            cache.incrementDescriptorAges();

            PeerAddress randomPeer = cache.selectPeerToShuffleWith();
            Snapshot.incSelectedTimes(randomPeer);

            if (randomPeer != null) {
                initiateShuffle(shuffleLength, randomPeer);
                trigger(new CyclonSample(getPartners()), samplePort);
                //logger.info("Peer: " + self.getPeerloggerId() + " cyclon WORKS");
            }
        }
    };
//-------------------------------------------------------------------	
    Handler<ShuffleRequest> handleShuffleRequest = new Handler<ShuffleRequest>() {
        public void handle(ShuffleRequest event) {
            PeerAddress peer = event.getPeerSource();
            DescriptorBuffer receivedRandomBuffer = event.getRandomBuffer();
            DescriptorBuffer toSendRandomBuffer = new DescriptorBuffer(self, cache.selectToSendAtPassive(receivedRandomBuffer.getSize(), peer));
            cache.selectToKeep(peer, receivedRandomBuffer.getDescriptors());
            ShuffleResponse response = new ShuffleResponse(event.getRequestId(), toSendRandomBuffer, self, peer);
            trigger(response, networkPort);

            Snapshot.updateCyclonPartners(self, getPartners());
        }
    };
//-------------------------------------------------------------------	
    Handler<ShuffleResponse> handleShuffleResponse = new Handler<ShuffleResponse>() {
        public void handle(ShuffleResponse event) {
            if (joining) {
                joining = false;
                trigger(new JoinCompleted(self), cyclonPort);

                // schedule shuffling
                SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(shufflePeriod, shufflePeriod);
                spt.setTimeoutEvent(new InitiateShuffle(spt));
                trigger(spt, timerPort);
            }

            // cancel shuffle timeout
            UUID shuffleId = event.getRequestId();
            if (outstandingRandomShuffles.containsKey(shuffleId)) {
                outstandingRandomShuffles.remove(shuffleId);
                CancelTimeout ct = new CancelTimeout(shuffleId);
                trigger(ct, timerPort);
            }

            PeerAddress peer = event.getPeerSource();
            DescriptorBuffer receivedRandomBuffer = event.getRandomBuffer();
            cache.selectToKeep(peer, receivedRandomBuffer.getDescriptors());

            Snapshot.updateCyclonPartners(self, getPartners());
        }
    };
//-------------------------------------------------------------------	
    Handler<ShuffleTimeout> handleShuffleTimeout = new Handler<ShuffleTimeout>() {
        public void handle(ShuffleTimeout event) {
        }
    };
//-------------------------------------------------------------------	
   /* Handler<CyclonPartnersRequest> handlePartnersRequest = new Handler<CyclonPartnersRequest>() {
     public void handle(CyclonPartnersRequest event) {
     logger.info("Handling request");
     CyclonPartnersResponse response = new CyclonPartnersResponse(getPartners());
     trigger(response, partnersPort);
     }
     };*/
//-------------------------------------------------------------------
    /**
     * Retrieve a random peer from the sample and return it to Search Component.
     */
    Handler<GetRandomPeerRequest> handleGetRandomPeerRequest = new Handler<GetRandomPeerRequest>() {
        @Override
        public void handle(GetRandomPeerRequest event) {

            Random randomGenerator = new Random();
            ArrayList<PeerAddress> partners = getPartners();

            if (partners.size() > 0) {
                PeerAddress randomPeer = partners.get(randomGenerator.nextInt(partners.size()));
                //logger.info("Cyclon :: Peer:"+self.getPeerAddress().getId()+", sending random peer - " + randomPeer.getPeerAddress().getId());
                trigger(new GetRandomPeerResponse(self, event.getPeerSource(), randomPeer), networkPort);
            }
        }
    };

//-------------------------------------------------------------------        
    private ArrayList<PeerAddress> getPartners() {
        ArrayList<PeerDescriptor> partnersDescriptors = cache.getAll();
        ArrayList<PeerAddress> partners = new ArrayList<PeerAddress>();
        for (PeerDescriptor desc : partnersDescriptors) {
            partners.add(desc.getPeerAddress());
        }
//        if (partners.size() == 0) {
//            logger.info("Peer: " + self.getPeerId() + ", has neighbours:" + partners.size());
//        }
        return partners;
    }
}
