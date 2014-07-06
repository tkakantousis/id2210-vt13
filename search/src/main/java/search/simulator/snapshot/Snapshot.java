package search.simulator.snapshot;

import common.peer.PeerAddress;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import search.gradient.Gradient;

public class Snapshot {
    public static final int initialNumberOfPeers = 100;
    public static int numberOfPeers = initialNumberOfPeers;
    public static int actualPeers = 0;
    public static final int numberOfEntries = 100;
    public static int totalSumOfEntries = numberOfEntries * initialNumberOfPeers;
    public static int totalEntriesAdded = 0;
    public static long firstEntryAddedAt = 0;
    public static long entriesIndexUpdated = 0;
    private static HashMap<PeerAddress, PeerInfo> peers = new HashMap<PeerAddress, PeerInfo>();
    private static SortedSet<PeerAddress> peersToConverge = new TreeSet<PeerAddress>();
    private static int counter = 0;
    private static String FILENAME = "search.out";
    private static float avgNumOfEntries = 0;
    private static boolean leaderDied = false;
    private static Map<PeerAddress, Integer> totalEntries = new HashMap<PeerAddress, Integer>();
    private static Map<Integer, Integer> partitionEntries = new HashMap<Integer, Integer>();
    private static BigInteger maxPeer = new BigInteger("0");
    public static BigInteger minPeer = new BigInteger("999999");
    private static Map<Integer, List<PeerAddress>> partitions = new HashMap<Integer, List<PeerAddress>>();
    private static Map<Integer, PeerAddress> leaders = new HashMap<Integer, PeerAddress>();
    private static Map<BigInteger, SortedSet<PeerAddress>> similarSets = new HashMap<BigInteger, SortedSet<PeerAddress>>();
    private static Map<PeerAddress, Integer> convergedPeers = new HashMap<PeerAddress, Integer>();
    private static boolean alreadyPrinted = false;
    public static int leaderMessages = 0;
    public static Map<PeerAddress, Integer> leaderSearchMessages = new HashMap<PeerAddress, Integer>();
    public static int failoverToConverge = 0;
    public static int failoverToFinish = 0;
    public static int failoverMessages = 0;
    public static Map<PeerAddress, Integer> cyclonEntriesMessages = new HashMap<PeerAddress, Integer>();
    public static Map<PeerAddress, Integer> gradientEntriesMessages = new HashMap<PeerAddress, Integer>();
    public static boolean firstEntryAdded = false;
    //-------------------------------------------------------------------

    public static void init(int numOfStripes) {
        FileIO.write("", FILENAME);
    }
//-------------------------------------------------------------------

    public static synchronized void checkSimilarSet(PeerAddress peer, SortedSet<PeerAddress> mySimilar, int gossipRound) {
        if (similarSets.containsKey(peer.getPeerId())) {
            if (!convergedPeers.containsKey(peer) && similarSets.get(peer.getPeerId()).equals(mySimilar)) {
                if (similarSets.get(peer.getPeerId()).last().getPeerId().compareTo(maxPeer) == 0 || similarSets.get(peer.getPeerId()).first().getPeerId().compareTo(peer.getPeerId()) < 0) {
                    //System.out.println("Peer:"+peer+", just converged in:"+gossipRound);
                }
                convergedPeers.put(peer, gossipRound);
                peersToConverge.remove(peer);
            }
        }

        if (!alreadyPrinted && convergedPeers.size() == numberOfPeers) {
            System.out.println("************\nConvergedPeers:" + convergedPeers.size());
            System.out.println("Gradient converged in:" + gossipRound + "\n****************");
            alreadyPrinted = true;
        }
    }

//-------------------------------------------------------------------
    public static synchronized void addEntries(PeerAddress peer, int entries) {
//        if(peer.getPeerId()==minPeer){
//            System.out.println("******************\n"+entries+"\n*********************");
//        }
        totalEntriesAdded -= totalEntries.get(peer);
        totalEntriesAdded += entries;
        totalEntries.put(peer, entries);
//        for (PeerAddress currpeer : totalEntries.keySet()) {
//            totalEntriesAdded += totalEntries.get(currpeer);
//        }

        if (totalEntriesAdded == totalSumOfEntries) {
            entriesIndexUpdated = System.currentTimeMillis();
            //System.out.println("last entry added at:" + entriesIndexUpdated);
        }
        if (!firstEntryAdded) {
            firstEntryAddedAt = System.currentTimeMillis();
            //System.out.println("first entry added at:" + firstEntryAddedAt);
            firstEntryAdded = true;
        }
    }
//-------------------------------------------------------------------

    public static synchronized void removeEntries(PeerAddress peer) {
        totalEntries.remove(peer);
    }

//-------------------------------------------------------------------
    public static synchronized void updateLeader(PeerAddress peer, int partitionID) {
        leaders.put(partitionID, peer);
    }
//-------------------------------------------------------------------

    public static synchronized void addPeerInfo(PeerAddress peer, int partitionID) {
        List<PeerAddress> peerIDs = partitions.get(partitionID);
        if (peerIDs == null) {
            peerIDs = new ArrayList<PeerAddress>();

        }
        peerIDs.add(peer);
        partitions.put(partitionID, peerIDs);
        totalEntries.put(peer, 0);
    }

    public static synchronized void removePeerInfo(PeerAddress peer) {
        for (Integer partition : partitions.keySet()) {
            if (partitions.get(partition) != null && partitions.get(partition).contains(peer)) {
                partitions.get(partition).remove(peer);
            }
        }

    }

//-------------------------------------------------------------------
    public static synchronized void addPeer(PeerAddress address) {
        peers.put(address, new PeerInfo());
        actualPeers++;
        if (address.getPeerId().compareTo(maxPeer) > 0) {
            maxPeer = address.getPeerId();
        }
        if (address.getPeerId().compareTo(minPeer) < 0) {
            minPeer = address.getPeerId();
        }

        if (peers.size() == numberOfPeers) {
            peersToConverge.addAll(peers.keySet());
            List<PeerAddress> allPeers = new ArrayList<PeerAddress>();
            allPeers.addAll(peers.keySet());
            Collections.sort(allPeers);
            similarSets = Gradient.getAllSimilarSets(allPeers);
            for (BigInteger bg : similarSets.keySet()) {
                System.out.println(bg + ":" + similarSets.get(bg));
            }
        }
    }

    public static synchronized void updateSimilarSets() {
        List<PeerAddress> allPeers = new ArrayList<PeerAddress>();
        allPeers.addAll(peers.keySet());
        Collections.sort(allPeers);
        similarSets.clear();
        similarSets = Gradient.getAllSimilarSets(allPeers);
        for (BigInteger bg : similarSets.keySet()) {
            System.out.println(bg + ":" + similarSets.get(bg));
        }
    }
//-------------------------------------------------------------------

    public static synchronized void removePeer(PeerAddress address) {
        peers.remove(address);
    }

//-------------------------------------------------------------------
    public static synchronized void updateNum(PeerAddress address, double num) {
        PeerInfo peerInfo = peers.get(address);

        if (peerInfo == null) {
            return;
        }

        peerInfo.updateNum(num);
    }

//-------------------------------------------------------------------
    public static synchronized void updateCyclonPartners(PeerAddress address, ArrayList<PeerAddress> partners) {
        PeerInfo peerInfo = peers.get(address);

        if (peerInfo == null) {
            return;
        }

        peerInfo.updateCyclonPartners(partners);
    }

//-------------------------------------------------------------------
    public static synchronized void report() {
        if (counter % 10 == 0) {
            String str = new String();
            str += "current time: " + counter + "\n";
            //str += reportNetworkState();
            //str += reportDetails();
            str += reportEntryState();
            str += "###\n";

            System.out.println(str);
            FileIO.append(str, FILENAME);
        }
        counter++;
    }

    public synchronized static String reportEntryState() {

        String str = new String();
        int numOfEntries = 0;
        for (PeerAddress peer : totalEntries.keySet()) {
            numOfEntries += totalEntries.get(peer);
        }
        if (totalEntries.size() > 0) {
            avgNumOfEntries = (float) numOfEntries / (float) totalEntries.size();
        } else {
            avgNumOfEntries = 0;
        }
        int cyclonEntriesSum = 0;
        int gradientEntriesSum = 0;
        for (PeerAddress peer : cyclonEntriesMessages.keySet()) {
            cyclonEntriesSum += cyclonEntriesMessages.get(peer);
        }
        for (PeerAddress peer : cyclonEntriesMessages.keySet()) {
            gradientEntriesSum += gradientEntriesMessages.get(peer);
        }

        long messageEntryLatency = (entriesIndexUpdated - firstEntryAddedAt) / 1000;
        int totalEntriesMessages = cyclonEntriesSum + gradientEntriesSum;
        str += "peers: " + peers.size() + "\n";
        str += "minPeer: " + minPeer + " - maxPeer: " + maxPeer + "\n";
        str += "Average number of entries: " + avgNumOfEntries + "\n";
        str += "Time to update index: " + messageEntryLatency + " sec., Messages to update index:" + totalEntriesMessages + ", Cyclon Messages: " + cyclonEntriesSum + ", Gradient Messages:" + gradientEntriesSum + "\n";
        str += "Peers converged: " + convergedPeers.size() + "\n";
        str += " Peers to Converge: " + peersToConverge + "\n";
        str += "failoverMessages: " + failoverMessages + ", failoverToConverge: " + failoverToConverge + ", failoverToFinish: " + failoverToFinish + "\n";
        str += "leader search: " + reportAvgAndMaxLeaderSearch();
        String parts = "";
        for (Integer partID : partitions.keySet()) {
            parts += partID + ":" + partitions.get(partID).size() + ", ";
        }
        String partsInfo = "";
        for (Integer partID : partitions.keySet()) {
            Collections.sort(partitions.get(partID));
            if (leaders.containsKey(partID)) {
                partsInfo += partID + "(" + leaders.get(partID) + "), msgs-" + leaderMessages + ":" + partitions.get(partID) + " size:" + partitions.get(partID).size() + "\n";
            } else {
                partsInfo += partID + ":" + partitions.get(partID) + " size:" + partitions.get(partID).size() + "\n";

            }
        }
        str += "partitions: \n" + partsInfo + "\n";
        str += "partitionEntries: \n" + updatePartitionEntries() + "\n";
        str += "###\n";
        return str;
    }

//-------------------------------------------------------------------
    private static String reportNetworkState() {
        String str = new String("---\n");
        int totalNumOfPeers = peers.size();
        str += "total number of peers: " + totalNumOfPeers + "\n";

        return str;
    }

    private static String reportAvgAndMaxLeaderSearch() {
        String str = "";
        int maxTime = 0;
        float avgTime = 0f;
        float sum = 0f;

        for (PeerAddress peer : leaderSearchMessages.keySet()) {

            if (leaderSearchMessages.get(peer) > maxTime) {
                maxTime = leaderSearchMessages.get(peer);
            }
            sum += (float) leaderSearchMessages.get(peer);
        }
        avgTime = sum / (float) leaderSearchMessages.size();
        str += "searches:" + leaderSearchMessages.size() + ", avgTime: " + avgTime + ", maxTime: " + maxTime + "\n";
        //str+="total searches: "+leaderSearchMessages+"\navgTime to Leader: "+avgTime +", maxTime to Leader: "+maxTime +"\n";
        return str;
    }

//-------------------------------------------------------------------
    private static String reportDetails() {
        PeerInfo peerInfo;
        String str = new String("---\n");

        return str;
    }

    public static void setLeaderDied(boolean leaderDied) {
        Snapshot.leaderDied = leaderDied;
    }

    public static boolean hasLeaderDied() {
        return leaderDied;
    }

    public static Map<PeerAddress, Integer> getTotalEntriesPerPeer() {
        return totalEntries;
    }

    //-------------------------------------------------------------------
    private synchronized static String updatePartitionEntries() {
        String str = "";
        for (Integer partitionID : partitions.keySet()) {
            str += partitionID + " - [";
            for (PeerAddress peer : partitions.get(partitionID)) {
                str += totalEntries.get(peer) + ", ";
            }
            str += "] \n";
        }
        return str;
    }

    public static HashMap<PeerAddress, PeerInfo> getPeers() {
        return peers;
    }
}
