package search.gradient;

import common.peer.PeerAddress;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import search.simulator.snapshot.Snapshot;
import search.system.peer.search.AddIndexToLeaderPort;
import search.system.peer.search.CyclonSampleToGradientEvent;
import search.system.peer.search.EntriesSentACK;
import search.system.peer.search.EntriesToLeaderMessage;
import search.system.peer.search.EntriesToSearchEvent;
import search.system.peer.search.GetEntriesFromGradient;
import search.system.peer.search.GetEntriesFromGradientRequest;
import search.system.peer.search.GetLeaderEvent;
import search.system.peer.search.GetLeaderResponse;
import search.system.peer.search.GetMaxEntryFromGradientRequest;
import search.system.peer.search.LeaderPort;

/**
 * Performs Leader Election, builds Gradient overlay.
 *
 * @author teo
 */
public class Gradient extends ComponentDefinition {

    private static final String LE_ELECTION = "ELECTION";
    private static final String LE_OK = "OK";
    private static final String LE_COORDINATOR = "COORDINATOR";
    private static final Logger logger = LoggerFactory.getLogger(Gradient.class);
    private static final int LEADER_TIMEOUT = 2000;
    private static final int NUMBER_OF_GRADIENT_PEERS = 4;
    private static final int convergeCounterThreshold = 15;
    private static final int TTL = 200;
    private final boolean FAIL_LEADER = false;
    private int convergenceCounter = 0;
    //Kompics instance variables
    Positive<Timer> timerPort = positive(Timer.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<LeaderPort> leaderPort = positive(LeaderPort.class);
    Negative<AddIndexToLeaderPort> indexToLeaderPort = negative(AddIndexToLeaderPort.class);
    //Gradient Instance Variables
    private Set<PeerAddress> random = new HashSet<PeerAddress>();
    private SortedSet<PeerAddress> similarSet = new TreeSet<PeerAddress>();
    private BigInteger utility;
    private PeerAddress self;
    private SortedSet<PeerAddress> lastSimilar = new TreeSet<PeerAddress>();
    private SortedSet<PeerAddress> lastSimilarReceived = new TreeSet<PeerAddress>();
    private SortedSet<PeerAddress> lastSimilarReceived2 = new TreeSet<PeerAddress>();
    private PeerAddress leader = null;
    private PeerAddress receivedLE_OKfrom = null;
    private PeerAddress lastLeader = null;
    private int time = 0;
    private boolean leaderAlive = true;
    private boolean iamAlive = true;
    private int failoverToConverge = 0;
    private int endFailoverTime = 0;
    private boolean startFailoverToConverge = false;
    private boolean startFailoverToElection = false;
    private int quorumCounter = 1;
    private int leaderFailureQuorum = NUMBER_OF_GRADIENT_PEERS / 2 + 1;

    /**
     * Constructor.
     */
    public Gradient() {
        subscribe(handleInit, control);
        subscribe(handleCyclonSampleToGradientEvent, leaderPort);

        subscribe(handleLEMessage, networkPort);
        subscribe(handleLEMessageSearch, networkPort);
        subscribe(handleLEMessageSearchFound, networkPort);
        subscribe(handleLETimeout, timerPort);
        subscribe(handleLeaderOKTimeout, timerPort);
        subscribe(handleLeaderSearchTimeout, timerPort);
        subscribe(handleGetLeaderEvent, leaderPort);
        subscribe(handleGetEntriesFromGradientRequest, leaderPort);
        subscribe(handleEntriesToLeaderMessage, networkPort);
        subscribe(handleLeaderHeartbeatRequest, networkPort);
        subscribe(handleLeaderHeartbeatResponse, networkPort);
        subscribe(handleLeaderPeriodicTimeout, timerPort);

        subscribe(handleExchangeGradientRequest, networkPort);
        subscribe(handleExchangeGradientRequestSecondary, networkPort);

        subscribe(handleSuggestLeaderFailure, networkPort);
    }

    private synchronized PeerAddress getLeader() {
        return leader;
    }

    private synchronized void setLeader(PeerAddress newLeader) {
        leader = newLeader;
    }
    Handler<GradientInit> handleInit = new Handler<GradientInit>() {
        @Override
        public void handle(GradientInit init) {
            self = init.getSelf();
            utility = self.getPeerId();

            similarSet = Collections.synchronizedSortedSet(similarSet);

            //Trigger periodic leader search
            SchedulePeriodicTimeout rst1 = new SchedulePeriodicTimeout(30000, 10000);
            rst1.setTimeoutEvent(new LeaderSearchTimeout(rst1, false));
            trigger(rst1, timerPort);

            SchedulePeriodicTimeout rst3 = new SchedulePeriodicTimeout(5000, 5000);
            rst3.setTimeoutEvent(new LeaderHeartbeatTimeout(rst3));
            trigger(rst3, timerPort);
        }
    };
    Handler<CyclonSampleToGradientEvent> handleCyclonSampleToGradientEvent = new Handler<CyclonSampleToGradientEvent>() {
        @Override
        public void handle(CyclonSampleToGradientEvent event) {
            //Only for testing purposes
//            if (Snapshot.actualPeers < Snapshot.numberOfPeers) {
//                return;
//            }

            ArrayList<PeerAddress> cyclonSample = new ArrayList<PeerAddress>();
            cyclonSample.addAll(event.getCyclonSample());

            for (PeerAddress peer : similarSet) {
                if (cyclon.simulator.snapshot.Snapshot.getDeadLeaders().contains(peer.getPeerId())) {
                    logger.info("Peer:" + self + " remove:" + peer);
                    similarSet.remove(peer);
                    break;
                }
            }
            time++;
            if (startFailoverToConverge) {
                failoverToConverge++;
            }
            if (startFailoverToElection) {
                endFailoverTime++;
            }

            if (FAIL_LEADER && getLeader() != null && getLeader().getPeerId().compareTo(utility) == 0 && convergenceCounter >= convergeCounterThreshold * 2 && !Snapshot.hasLeaderDied()) {
                iamAlive = false;
                Snapshot.setLeaderDied(true);
                Snapshot.removeEntries(self);
                Snapshot.removePeerInfo(self);
                Snapshot.getPeers().remove(self);
                Snapshot.updateSimilarSets();
                Snapshot.numberOfPeers--;


                int a = 5 / 0;
            }

            if (getLeader() != null && lastLeader == null) {
                logger.info("Peer:" + self + ", leader:" + getLeader());
                lastLeader = getLeader();
            } else if (getLeader() != null && lastLeader != null && getLeader().getPeerId().compareTo(lastLeader.getPeerId()) != 0) {
                logger.info("Peer:" + self + ", leader:" + getLeader());
                lastLeader = getLeader();
            }

            if (cyclonSample.size() > 0) {
                SortedSet<PeerAddress> mergedSet = new TreeSet<PeerAddress>();
                mergedSet.addAll(similarSet);
                mergedSet.addAll(cyclonSample);
                similarSet.clear();
                similarSet.addAll(getSimilarSet(mergedSet, utility));
                if (similarSet.size() >= NUMBER_OF_GRADIENT_PEERS) {
                    
                    if (similarSet.equals(lastSimilar)) {
                        convergenceCounter++;
                        if (convergenceCounter == convergeCounterThreshold) {
                            
                            if (similarSet.last().getPeerId().compareTo(utility) < 0) {
                                //logger.info("Peer:" + self + ", convergenceCounter:" + convergenceCounter + ", similar:" + similarSet);
                                if (startFailoverToConverge) {
                                    Snapshot.failoverToConverge = failoverToConverge - convergenceCounter;
                                    startFailoverToConverge = false;
                                    startFailoverToElection = true;
                                }
                                initiateElection();
                                for (PeerAddress peer : cyclonSample) {
                                    trigger(new GetMaxEntryFromGradientRequest(self, peer), networkPort);
                                }
                            }
                        }
                    } else {
                        
                        convergenceCounter = 0;
                        lastSimilar.clear();
                        lastSimilar.addAll(similarSet);
                    }

                    //Send my similarSet to my closest peer
                    SortedSet<PeerAddress> similarToSend = new TreeSet<PeerAddress>();
                    similarToSend.addAll(similarSet);

                    if (similarSet.last().getPeerId().compareTo(utility) < 0) {
                        trigger(new ExchangeGradientRequest(self, similarSet.last(), similarToSend), networkPort);
                    } else {
                        trigger(new ExchangeGradientRequest(self, similarSet.first(), similarToSend), networkPort);
                        
                    }
                }
            }
            Snapshot.checkSimilarSet(self, similarSet, time);
        }
    };
    /**
     * Merged received Similar set and compute new one.
     */
    Handler<ExchangeGradientRequest> handleExchangeGradientRequest = new Handler<ExchangeGradientRequest>() {
        @Override
        public void handle(ExchangeGradientRequest event) {
            SortedSet<PeerAddress> mergedSimilarSets = new TreeSet<PeerAddress>();
            if (!lastSimilarReceived.equals(event.getSimilarSet())) {
                mergedSimilarSets.addAll(similarSet);
                mergedSimilarSets.addAll(event.getSimilarSet());
                if (mergedSimilarSets.contains(self)) {
                    mergedSimilarSets.remove(self);
                }
                
                if (!mergedSimilarSets.isEmpty()) {
                    similarSet.clear();
                    similarSet.addAll(getSimilarSet(mergedSimilarSets, utility));
                    Snapshot.checkSimilarSet(self, similarSet, time);
                }

                lastSimilarReceived.clear();
                lastSimilarReceived.addAll(event.getSimilarSet());
            }
            SortedSet<PeerAddress> similarToSend = new TreeSet<PeerAddress>();
            similarToSend.addAll(similarSet);
            trigger(new ExchangeGradientRequestSecondary(self, event.getPeerSource(), similarToSend), networkPort);

        }
    };
    Handler<ExchangeGradientRequestSecondary> handleExchangeGradientRequestSecondary = new Handler<ExchangeGradientRequestSecondary>() {
        @Override
        public void handle(ExchangeGradientRequestSecondary event) {

            if (!lastSimilarReceived2.equals(event.getSimilarSet())) {
                SortedSet<PeerAddress> mergedSimilarSets = new TreeSet<PeerAddress>();
                mergedSimilarSets.addAll(similarSet);
                mergedSimilarSets.addAll(event.getSimilarSet());
                mergedSimilarSets.add(event.getPeerSource());
                if (mergedSimilarSets.contains(self)) {

                    mergedSimilarSets.remove(self);
                }

                if (!mergedSimilarSets.isEmpty()) {
                    similarSet.clear();

                    similarSet.addAll(getSimilarSet(mergedSimilarSets, utility));
                    Snapshot.checkSimilarSet(self, similarSet, time);
                }

                lastSimilarReceived2.clear();
                lastSimilarReceived2.addAll(event.getSimilarSet());
            }

        }
    };
    /**
     * Handle Message Election Messages.
     */
    Handler<LEMessage> handleLEMessage = new Handler<LEMessage>() {
        @Override
        public void handle(LEMessage event) {
            String type = event.getType();
            if (type.equals(LE_ELECTION)) {
                //If received ELECTION message from lower ID send OK and then ELECTION
                if (event.getPeerSource().getPeerId().compareTo(utility) < 0) {
                    trigger(new LEMessage(self, event.getPeerSource(), LE_OK), networkPort);
                    Snapshot.leaderMessages++;
                    initiateElection();
                }
            } else if (type.equals(LE_OK)) {
                //We received an OK message from a higher node
                if ((receivedLE_OKfrom == null && event.getPeerSource().getPeerId().compareTo(utility) > 0)
                        || (receivedLE_OKfrom != null && event.getPeerSource().getPeerId().compareTo(utility) > 0
                        && event.getPeerSource().getPeerId().compareTo(receivedLE_OKfrom.getPeerId()) > 0)) {
                    //logger.info("Peer:" + self + ", rcv receivedLE_OKfrom:" + event.getPeerSource());
                    receivedLE_OKfrom = event.getPeerSource();
                    //Wait for 2 * LEADER_TIMEOUT
                    ScheduleTimeout rst = new ScheduleTimeout(LEADER_TIMEOUT * 2);
                    rst.setTimeoutEvent(new LeaderOKTimeout(rst));
                    trigger(rst, timerPort);
                }
            }
            if (type.equals(LE_COORDINATOR)) {
                setLeader(event.getPeerSource());
            }

        }
    };
    /**
     * Handles Leader Search Messages.
     */
    Handler<LEMessageSearch> handleLEMessageSearch = new Handler<LEMessageSearch>() {
        @Override
        public void handle(LEMessageSearch event) {
            //If ttl == 0 discard
            if (event.getTTL() <= 0) {
                Snapshot.leaderSearchMessages.put(event.getRequester(), 1);
            } else {
                //If I am leader and similar set is smaller send reply to requester
                if (getLeader() != null && ((getLeader().getPeerId().compareTo(utility) == 0 && similarSet.last().getPeerId().compareTo(utility) < 0)
                        || (similarSet.first().getPeerId().compareTo(utility) < 0))) {
                    trigger(new LEMessageSearchFound(self, event.getRequester(), getLeader()), networkPort);
                } else if (getLeader() != null && similarSet != null && similarSet.size() == NUMBER_OF_GRADIENT_PEERS) {
                    //logger.info("Peer:" + self + ", LEMessage1.similar:" + similarSet);
                    int newTTL = event.getTTL() - 1;
                    if (!Snapshot.leaderSearchMessages.containsKey(event.getRequester())) {
                        Snapshot.leaderSearchMessages.put(event.getRequester(), 0);
                    }
                    int steps = Snapshot.leaderSearchMessages.get(event.getRequester()) + 1;
                    Snapshot.leaderSearchMessages.put(event.getRequester(), steps);
                    trigger(new LEMessageSearch(self, similarSet.last(), event.getRequester(), newTTL), networkPort);

                } else if (getLeader() == null && similarSet != null /*&& similarSet.first().getPeerId().compareTo(utility) < 0*/ && similarSet.size() == NUMBER_OF_GRADIENT_PEERS) {
                    int newTTL = event.getTTL() - 1;
                    if (!Snapshot.leaderSearchMessages.containsKey(event.getRequester())) {
                        Snapshot.leaderSearchMessages.put(event.getRequester(), 0);
                    }
                    int steps = Snapshot.leaderSearchMessages.get(event.getRequester()) + 1;
                    Snapshot.leaderSearchMessages.put(event.getRequester(), steps);
                    trigger(new LEMessageSearch(self, similarSet.last(), event.getRequester(), newTTL), networkPort);

                }
            } //else forward to most similar node
        }
    };
    Handler<LEMessageSearchFound> handleLEMessageSearchFound = new Handler<LEMessageSearchFound>() {
        @Override
        public void handle(LEMessageSearchFound event) {
            if (getLeader() == null || event.getLeader().getPeerId().compareTo(getLeader().getPeerId()) > 0) {
                setLeader(event.getLeader());
                trigger(new GetLeaderResponse(getLeader()), indexToLeaderPort);
            }

        }
    };
    Handler<GetLeaderEvent> handleGetLeaderEvent = new Handler<GetLeaderEvent>() {
        @Override
        public void handle(GetLeaderEvent event) {
            if (!event.isTriggeredBySendToLeader()) {
                trigger(new GetLeaderResponse(getLeader()), indexToLeaderPort);
            } else {
                ScheduleTimeout rst1 = new ScheduleTimeout(10);
                rst1.setTimeoutEvent(new LeaderSearchTimeout(rst1, true));
                trigger(rst1, timerPort);
            }
        }
    };
    /**
     * Search new entries from leader by contacting similar set.
     */
    Handler<GetEntriesFromGradientRequest> handleGetEntriesFromGradientRequest = new Handler<GetEntriesFromGradientRequest>() {
        @Override
        public void handle(GetEntriesFromGradientRequest event) {
            //If I am not leader
            if (getLeader() == null || (getLeader() != null && getLeader().getPeerId().compareTo(utility) != 0)) {
                //Contact closest to leader gradient peer
                if (!similarSet.isEmpty()) {
                    trigger(new GetEntriesFromGradient(self, similarSet.last(), event.getMaxEntry()), networkPort);
                }
            }
        }
    };
    /**
     * Initiate Leader Search.
     */
    Handler<LeaderSearchTimeout> handleLeaderSearchTimeout = new Handler<LeaderSearchTimeout>() {
        @Override
        public void handle(LeaderSearchTimeout event) {
            //If my leader is not null and my similarSet is larger tha me, search for leader.
            if (!event.isAddingEntry()) {
                if (getLeader() != null) {
                    if (similarSet != null && similarSet.size() >= NUMBER_OF_GRADIENT_PEERS && similarSet.first().getPeerId().compareTo(utility) > 0) {
                        //Send LEMessageSearch
                        trigger(new LEMessageSearch(self, similarSet.last(), self, TTL), networkPort);
                        Snapshot.leaderSearchMessages.put(self, 1);

                    }
                }
            } else {
                if (getLeader() == null) {
                    if (similarSet != null && !similarSet.isEmpty() /*&& similarSet.first().getPeerId().compareTo(utility) > 0*/) {
                        //Send LEMessageSearch
                        trigger(new LEMessageSearch(self, similarSet.last(), self, TTL), networkPort);
                        Snapshot.leaderSearchMessages.put(self, 1);
                    }
                } else {
                    trigger(new GetLeaderResponse(getLeader()), indexToLeaderPort);
                }
            }
        }
    };
    /**
     * Triggered after LEADER_TIMEOUT seconds to check if received LE_OK
     * messages.
     */
    Handler<LeaderElectionTimeout> handleLETimeout = new Handler<LeaderElectionTimeout>() {
        @Override
        public void handle(LeaderElectionTimeout event) {
            // I won election
            if (receivedLE_OKfrom == null) {
                setLeader(self);
                //logger.info("Peer:" + self + ", I am new leader!");
                if (startFailoverToElection) {
                    Snapshot.failoverToFinish = endFailoverTime;
                    startFailoverToElection = false;
                }

                Iterator<PeerAddress> iter = similarSet.iterator();
                //GET RID OF FIRST NODE AS I AM SURE I AM LEADER
                iter.next();
                while (iter.hasNext()) {
                    PeerAddress peerToSendCOORDINATION = iter.next();
                    trigger(new LEMessage(self, peerToSendCOORDINATION, LE_COORDINATOR), networkPort);
                    Snapshot.leaderMessages++;
                }
            }
        }
    };
    /**
     * Triggered after LEADER_TIMEOUT seconds to check if received LE_OK
     * messages.
     */
    Handler<LeaderOKTimeout> handleLeaderOKTimeout = new Handler<LeaderOKTimeout>() {
        @Override
        public void handle(LeaderOKTimeout event) {
            if (getLeader() == null) {
                initiateElection();
            }
        }
    };

    private void initiateElection() {
        //Initiate Leader Election  
        if (similarSet.size() >= NUMBER_OF_GRADIENT_PEERS) {

            //Send ELECTION message to peers with lower IDs

            Iterator<PeerAddress> iter = similarSet.iterator();
            //GET RID OF FIRST NODE AS I AM SURE I AM LEADER
            iter.next();
            while (iter.hasNext()) {

                PeerAddress peerToSendELECTION = iter.next();
                trigger(new LEMessage(self, peerToSendELECTION, LE_ELECTION), networkPort);
                Snapshot.leaderMessages++;

            }
            ScheduleTimeout rst = new ScheduleTimeout(LEADER_TIMEOUT);
            rst.setTimeoutEvent(new LeaderElectionTimeout(rst));
            trigger(rst, timerPort);
        }
    }
    /**
     *
     */
    Handler<EntriesToLeaderMessage> handleEntriesToLeaderMessage = new Handler<EntriesToLeaderMessage>() {
        @Override
        public void handle(EntriesToLeaderMessage event) {
            if (getLeader().getPeerId().compareTo(utility) == 0) {
                //SEND ENTRIES TO SEARCH COMPONENT ΤΟ ΒΕ ADDED TO LUCENE
                Map<Integer, String> temp = new HashMap<Integer, String>();
                temp.putAll(event.getEntries());

                //MUST USE A MAP<peerID, lastEntryID>
                trigger(new EntriesToSearchEvent(temp), indexToLeaderPort);
                //Send ACK back to sender
                trigger(new EntriesSentACK(self, event.getPeerSource(), temp), networkPort);
                //logger.info("Peer:" + self + " rcv from:" + event.getPeerSource() + ", sending entries to SearchComponent:" + temp);

            }

        }
    };
    Handler<SuggestLeaderFailure> handleSuggestLeaderFailure = new Handler<SuggestLeaderFailure>() {
        @Override
        public void handle(SuggestLeaderFailure event) {
            
            
            if (getLeader() != null && getLeader().getPeerId().compareTo(utility) != 0) {
                quorumCounter++;
                if (quorumCounter >= leaderFailureQuorum) {
                    cyclon.simulator.snapshot.Snapshot.getDeadLeaders().add(getLeader().getPeerId());

                    for (PeerAddress peer : similarSet) {
                        if (peer.getPeerId().compareTo(getLeader().getPeerId()) == 0) {
                            similarSet.remove(peer);

                            break;
                        }
                    }
                    for (PeerAddress peer : similarSet) {
                        if (peer.getPeerId().compareTo(getLeader().getPeerId()) != 0
                                && peer.getPeerId().compareTo(similarSet.first().getPeerId()) != 0) {
                            Snapshot.failoverMessages++;

                            trigger(new SuggestLeaderFailure(self, peer), networkPort);
                        }
                    }
                    startFailoverToConverge = true;
                    setLeader(null);
                    leaderAlive = true;
                    quorumCounter = 1;
                }
            }
        }
    };
    /**
     * Handles Leader Failure.
     */
    Handler<LeaderHeartbeatTimeout> handleLeaderPeriodicTimeout = new Handler<LeaderHeartbeatTimeout>() {
        @Override
        public void handle(LeaderHeartbeatTimeout event) {
            if (getLeader() != null && getLeader().getPeerId().compareTo(utility) != 0 && similarSet.first().getPeerId().compareTo(utility) < 0) {
                if (!leaderAlive) {
                    for (PeerAddress peer : similarSet) {
                        if (peer.getPeerId().compareTo(getLeader().getPeerId()) != 0
                                && peer.getPeerId().compareTo(similarSet.first().getPeerId()) != 0) {
                            Snapshot.failoverMessages++;

                            trigger(new SuggestLeaderFailure(self, peer), networkPort);
                        }
                    }
                } else {
                    trigger(new LeaderHeartbeatRequest(self, getLeader()), networkPort);
                    leaderAlive = false;
                }

            }
        }
    };
    Handler<LeaderHeartbeatRequest> handleLeaderHeartbeatRequest = new Handler<LeaderHeartbeatRequest>() {
        @Override
        public synchronized void handle(LeaderHeartbeatRequest event) {
            if (iamAlive) {
                trigger(new LeaderHeartbeatResponse(self, event.getPeerSource()), networkPort);
            }
        }
    };
    Handler<LeaderHeartbeatResponse> handleLeaderHeartbeatResponse = new Handler<LeaderHeartbeatResponse>() {
        @Override
        public synchronized void handle(LeaderHeartbeatResponse event) {
            leaderAlive = true;
        }
    };

    /**
     * Search for similar peer in order to update Similar set.
     *
     * @param cyclonSample
     * @return Similar Peer according to Utility
     */
    private static PeerAddress getSimilarPeer(ArrayList<PeerAddress> sample, BigInteger selfUtility, boolean similar) {
        PeerAddress similarPeer = sample.get(0);
        //logger.info("getSimilarPeer:" + sample);

        if (sample.size() == 1) {
            return sample.get(0);
        } else {
            for (int j = 1; j < sample.size(); j++) {
                if (similar) {
                    if (utilityFunction(sample.get(j).getPeerId(), similarPeer.getPeerId(), selfUtility)) {
                        similarPeer = sample.get(j);
                    }
                } else {
                    if (utilityFunctionReverse(sample.get(j).getPeerId(), similarPeer.getPeerId(), selfUtility)) {
                        similarPeer = sample.get(j);
                    }
                }
            }
        }
        return similarPeer;

    }

    public static Map<BigInteger, SortedSet<PeerAddress>> getAllSimilarSets(List<PeerAddress> peers) {
        Map<BigInteger, SortedSet<PeerAddress>> peerSets = new HashMap<BigInteger, SortedSet<PeerAddress>>();
        for (PeerAddress currPeer : peers) {
            ArrayList<PeerAddress> tempPeers = new ArrayList<PeerAddress>();
            tempPeers.addAll(peers);
            //Remove myself
            for (PeerAddress peerToRemove : tempPeers) {
                if (peerToRemove.getPeerId().compareTo(currPeer.getPeerId()) == 0) {
                    tempPeers.remove(peerToRemove);
                    break;
                }
            }
            for (int i = 0; i < NUMBER_OF_GRADIENT_PEERS; i++) {
                if (!peerSets.containsKey(currPeer.getPeerId())) {
                    peerSets.put(currPeer.getPeerId(), new TreeSet<PeerAddress>());
                }


                PeerAddress similarPeer = getSimilarPeer(tempPeers, currPeer.getPeerId(), true);
                peerSets.get(currPeer.getPeerId()).add(similarPeer);
                tempPeers.remove(similarPeer);
            }

        }


        return peerSets;


    }

    public static SortedSet<PeerAddress> getSimilarSet(SortedSet<PeerAddress> peers, BigInteger myutility) {
        SortedSet<PeerAddress> newSimilarSet = new TreeSet<PeerAddress>();
        ArrayList<PeerAddress> tempPeers = new ArrayList<PeerAddress>();
        tempPeers.addAll(peers);

        for (int i = 0; i < NUMBER_OF_GRADIENT_PEERS; i++) {
            if (!tempPeers.isEmpty()) {
                PeerAddress similarPeer = getSimilarPeer(tempPeers, myutility, true);
                newSimilarSet.add(similarPeer);
                tempPeers.remove(similarPeer);
            }
        }

        return newSimilarSet;


    }

    /**
     * Search for similar peer in order to update Similar set.
     *
     * @param cyclonSample
     * @return Similar Peer according to Utility
     */
    private static BigInteger getSimilarPeerWithIDs(ArrayList<BigInteger> cyclonSample, BigInteger selfUtility, boolean similar) {
        BigInteger similarPeer = cyclonSample.get(0);
        if (cyclonSample.size() == 1) {
            return cyclonSample.get(0);
        } else {
            for (int j = 1; j < cyclonSample.size(); j++) {
                if (similar) {
                    if (utilityFunction(cyclonSample.get(j), similarPeer, selfUtility)) {
                        similarPeer = cyclonSample.get(j);
                    }
                } else {
                    if (utilityFunctionReverse(cyclonSample.get(j), similarPeer, selfUtility)) {
                        similarPeer = cyclonSample.get(j);
                    }
                }
            }
        }
        return similarPeer;

    }

    /**
     * Compares two peers provided by cyclon and returns the one most similar to
     * our utility.
     *
     * @param a
     * @param b
     * @return True if A more similar to B, else False.
     */
    private static boolean utilityFunction(BigInteger a, BigInteger b, BigInteger selfUtility) {
        if (a.compareTo(selfUtility) > 0 && b.compareTo(selfUtility) < 0) {
            return true;
        } else if (Math.abs(a.intValue() - selfUtility.intValue()) < Math.abs(b.intValue() - selfUtility.intValue())) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean utilityFunctionReverse(BigInteger a, BigInteger b, BigInteger selfUtility) {
        boolean result;
        if (a.compareTo(selfUtility) > 0 && b.compareTo(selfUtility) < 0) {
            result = true;
        } else if (Math.abs(a.intValue() - selfUtility.intValue()) < Math.abs(b.intValue() - selfUtility.intValue())) {
            result = true;
        } else {
            result = false;
        }
        if (result) {
            result = false;
        } else {
            result = true;
        }
        return result;
    }

    /**
     * Compares two peers provided by cyclon and returns the one most similar to
     * our utility.
     *
     * @param a
     * @param b
     * @return The value of similarity.
     */
    private BigInteger getUtilitySimilarity(BigInteger peerUtility, BigInteger selfUtility) {
        return new BigInteger(Integer.toString(selfUtility.intValue() - peerUtility.intValue()));
    }
}
