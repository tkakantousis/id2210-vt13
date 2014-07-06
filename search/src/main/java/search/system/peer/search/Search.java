package search.system.peer.search;

import common.configuration.SearchConfiguration;
import common.peer.PeerAddress;
import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import se.sics.kompics.web.WebRequest;
import se.sics.kompics.web.WebResponse;
import search.simulator.snapshot.Snapshot;
import search.system.peer.AddIndexText;
import search.system.peer.IndexPort;
import tman.system.peer.tman.TManSample;
import tman.system.peer.tman.TManSamplePort;

/**
 * Component that performs index add/search/update and nodes partitioning.
 *
 * @author jdowling
 */
public final class Search extends ComponentDefinition {

    private static final Logger logger = LoggerFactory.getLogger(Search.class);
    private static final int sendEntriesToLeaderStart = 20000;
    private static final int sendEntriesToLeaderPeriod = 5000;
    private static final int SEARCH_PEERS = 3;
    Positive<IndexPort> indexPort = positive(IndexPort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    Negative<Web> webPort = negative(Web.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Negative<LeaderPort> leaderPort = negative(LeaderPort.class);
    Positive<AddIndexToLeaderPort> indexToLeaderPort = positive(AddIndexToLeaderPort.class);
    //Negative<CyclonPartnersPort> partnersPort = negative(CyclonPartnersPort.class);
    Positive<TManSamplePort> tmanSamplePort = positive(TManSamplePort.class);
    List<PeerAddress> neighbours = new ArrayList<PeerAddress>();
    List<PeerAddress> cyclonSample = new ArrayList<PeerAddress>();
    int[] indexStore = new int[1000];
    List<Integer> pendingEntries = new ArrayList<Integer>();
    List<Integer> buffer = new ArrayList<Integer>();
    private int entryID = -1;
    private int localEntryID = 0;
    private PeerAddress self;
    private long period;
    private double num;
    private int selfPartitionID = -1;
    private SearchConfiguration searchConfiguration;
    private PeerAddress leader = null;
    // Apache Lucene used for searching
    StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
    Directory index = new RAMDirectory();
    IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);
    private int lastMissingIndexEntry = 0;
    private int maxIndexEntry = -1;
    private Map<Integer, String> entriesToLeader = new HashMap<Integer, String>();
    private Random random = new Random();
    private int searchQueryCounter = 0;
    //Store search results in format <QueryID, <PartitionID, List of results from partition>>
    private Map<String, Map<Integer, Set<String>>> pendingResults = new HashMap<String, Map<Integer, Set<String>>>();
    private Map<String, WebRequest> webRequestPerReqID = new HashMap<String, WebRequest>();
    private Map<String, Integer> timesRequestWasChecked = new HashMap<String, Integer>();
    // When you partition the index you need to find new nodes
    // This is a routing table maintaining a list of pairs in each partition.
    private Map<Integer, List<PeerDescriptor>> routingTable;
    Comparator<PeerDescriptor> peerAgeComparator = new Comparator<PeerDescriptor>() {
        @Override
        public int compare(PeerDescriptor t, PeerDescriptor t1) {
            if (t.getAge() > t1.getAge()) {
                return 1;
            } else {
                return -1;
            }
        }
    };
//-------------------------------------------------------------------	

    public Search() {

        subscribe(handleInit, control);
        subscribe(handleWebRequest, webPort);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleTManSample, tmanSamplePort);
        subscribe(handleAddIndexText, indexPort);
        subscribe(handleGetLeaderResponse, indexToLeaderPort);
        subscribe(handleUpdateIndexTimeout, timerPort);
        subscribe(handleSendEntriesToLeaderTimeout, timerPort);
        subscribe(handleGetLeaderRequest, timerPort);
        subscribe(handleGetEntriesFromGradientTimeout, timerPort);
        subscribe(handleEntriesToSearchEvent, indexToLeaderPort);
        subscribe(handleEntriesSentACK, networkPort);
        subscribe(handleGetEntriesFromGradientMessage, networkPort);
        subscribe(handleGetMaxEntryFromGradientRequest, networkPort);
        subscribe(handleGetMaxEntryFromGradientResponse, networkPort);
        subscribe(handleMissingIndexEntriesRequest, networkPort);
        subscribe(handleMissingIndexEntriesResponse, networkPort);
        subscribe(handleGetEntriesFromGradientResponseMessage, networkPort);
        subscribe(handleEmptyBufferTimeout, timerPort);
        subscribe(handleGetEntryFromPartitionsRequestMessage, networkPort);
        subscribe(handleGetEntryFromPartitionsResponseMessage, networkPort);
        subscribe(handleSendWebresponseTimeout, timerPort);
    }
//-------------------------------------------------------------------	
    Handler<SearchInit> handleInit = new Handler<SearchInit>() {
        public void handle(SearchInit init) {
            self = init.getSelf();
            num = init.getNum();
            searchConfiguration = init.getConfiguration();
            period = searchConfiguration.getPeriod();
            routingTable = new HashMap<Integer, List<PeerDescriptor>>(searchConfiguration.getNumPartitions());
            random = new Random(System.currentTimeMillis());
            selfPartitionID = self.getPeerAddress().getId() % searchConfiguration.getNumPartitions();
            pendingEntries = Collections.synchronizedList(pendingEntries);
            buffer = Collections.synchronizedList(buffer);
            neighbours = Collections.synchronizedList(neighbours);
            cyclonSample = Collections.synchronizedList(cyclonSample);
            entriesToLeader = Collections.synchronizedMap(entriesToLeader);
            pendingResults = Collections.synchronizedMap(pendingResults);
            webRequestPerReqID = Collections.synchronizedMap(webRequestPerReqID);
            timesRequestWasChecked = Collections.synchronizedMap(timesRequestWasChecked);
            for (int i = 0; i < indexStore.length; i++) {
                indexStore[i] = -1;
            }
            Snapshot.cyclonEntriesMessages.put(self, 0);
            Snapshot.gradientEntriesMessages.put(self, 0);

            /**
             * ** Start timer to exchange index entries
             */
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(4000, 8000);
            rst.setTimeoutEvent(new UpdateIndexTimeout(rst));
            trigger(rst, timerPort);

            /**
             * Start timer to send entries to Leader
             */
            SchedulePeriodicTimeout rst2 = new SchedulePeriodicTimeout(sendEntriesToLeaderStart, sendEntriesToLeaderPeriod);
            rst2.setTimeoutEvent(new SendEntriesToLeaderTimeout(rst2));
            trigger(rst2, timerPort);

            SchedulePeriodicTimeout rst3 = new SchedulePeriodicTimeout(10000, 1000);
            rst3.setTimeoutEvent(new GetLeaderRequest(rst3));
            trigger(rst3, timerPort);

            /**
             * Start timer to get entries from gradient peer
             */
            SchedulePeriodicTimeout rst4 = new SchedulePeriodicTimeout(4000, 4000);
            rst4.setTimeoutEvent(new GetEntriesFromGradientTimeout(rst4));
            trigger(rst4, timerPort);

            /**
             * Start timer to empty buffer
             */
            SchedulePeriodicTimeout rst5 = new SchedulePeriodicTimeout(500000, 5000);
            rst5.setTimeoutEvent(new EmptyBufferTimeout(rst5));
            trigger(rst5, timerPort);

            /**
             * Start timer to send web response
             */
            SchedulePeriodicTimeout rst6 = new SchedulePeriodicTimeout(1000, 1000);
            rst6.setTimeoutEvent(new SendWebresponseTimeout(rst6));
            trigger(rst6, timerPort);

            Snapshot.updateNum(self, num);
            Snapshot.addPeerInfo(self, selfPartitionID);
        }
    };
    Handler<WebRequest> handleWebRequest = new Handler<WebRequest>() {
        public void handle(WebRequest event) {
            if (event.getDestination() != self.getPeerAddress().getId()) {
                return;
            }
            String[] args = null;
            args = event.getTarget().split("-");

            logger.debug("Handling Webpage Request");
            WebResponse response = null;
            if (args[0].compareToIgnoreCase("search") == 0) {
                //response = new WebResponse(searchPageHtml(args[1]), event, 1, 1);
                String reqID = self.getPeerId().toString() + "-" + searchQueryCounter;
                searchQueryCounter++;
                webRequestPerReqID.put(reqID, event);
                //logger.info("Peer:" + self + "new webRequestPerReqID:" + webRequestPerReqID);
                searchPagePartitions(args[1], reqID);
            } else if (args[0].compareToIgnoreCase("add") == 0) {
                response = new WebResponse(addEntryHtml(args[1]), event, 1, 1);
                trigger(response, webPort);
            } else if (args[0].compareToIgnoreCase("searchhtml") == 0) {
                response = new WebResponse(searchPageHtml(args[1]), event, 1, 1);
                trigger(response, webPort);
            } else if (args[0].compareToIgnoreCase("size") == 0) {
                response = new WebResponse("Index size:" + Integer.toString(getIndexStoreSize()), event, 1, 1);
                trigger(response, webPort);
            }

        }
    };

    private String searchPageHtml(String title) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
        sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
        sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
        sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
        sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
        sb.append("<title>Kompics P2P Bootstrap Server</title>");
        sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
        sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
        sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
        sb.append("ID2210 (Decentralized Search for Piratebay)</h2><br>");
        try {
            query(sb, title, "title");
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            sb.append(ex.getMessage());
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            sb.append(ex.getMessage());
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Adds local query results and sends query to peers in partitions.
     *
     * @param title
     */
    private void searchPagePartitions(String title, String reqID) {
        //****** SEND QUERY TO PARTITIONS *************

        //First add entries in local memory
        String localResults = "";
        try {
            StringBuilder sb = new StringBuilder();
            localResults = query(sb, title, "title");
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
        }
        Map<Integer, Set<String>> partitionResults = new HashMap<Integer, Set<String>>();
        Set<String> localResultsList = new HashSet<String>();
        localResultsList.add(localResults);
        partitionResults.put(selfPartitionID, new HashSet<String>());
        partitionResults.get(selfPartitionID).addAll(localResultsList);
        pendingResults.put(reqID, new HashMap<Integer, Set<String>>());
        pendingResults.get(reqID).putAll(partitionResults);
        timesRequestWasChecked.put(reqID, 0);

        //Send request to random nodes from routing table
        for (int partitionID : routingTable.keySet()) {
            if (!routingTable.get(partitionID).isEmpty()) {

                for (int i = 0; i < routingTable.get(partitionID).size(); i++) {
                    if (i >= SEARCH_PEERS) {
                        break;
                    }
                    //logger.info("Peer:" + self.getPeerId() + ", send query to:" + routingTable.get(partitionID).get(i).getPeerAddress() + "-" + partitionID);
                    trigger(new GetPartitionEntriesRequest(self, routingTable.get(partitionID).get(i).getPeerAddress(), reqID, title), networkPort);

                }
            }
        }
    }

    private String addEntryHtml(String title) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
        sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
        sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
        sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
        sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
        sb.append("<title>Adding an Entry</title>");
        sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
        sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
        sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
        sb.append("ID2210 Uploaded Entry</h2><br>");
        entriesToLeader.put(localEntryID, title);
        logger.info("Peer:" + self + ", added entry:" + entriesToLeader);
        localEntryID++;
        sb.append("Entry: ").append(title);
        sb.append("</body></html>");
        return sb.toString();
    }

    private synchronized void addEntry(String title, int id) throws IOException {
        IndexWriter w = new IndexWriter(index, config);
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));
        // You may need to make the StringField searchable by NumericRangeQuery. See:
        // http://stackoverflow.com/questions/13958431/lucene-4-0-indexwriter-updatedocument-for-numeric-term
        // http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/document/IntField.html
        doc.add(new IntField("id", id, Field.Store.YES));
        w.addDocument(doc);
        w.close();

        if (id > maxIndexEntry) {
            maxIndexEntry = id;
        }

    }

    private String query(StringBuilder sb, String querystr, String searchType) throws ParseException, IOException {
        // the "title" arg specifies the default field to use when no field is explicitly specified in the query.
        Query q = new QueryParser(Version.LUCENE_42, searchType, analyzer).parse(querystr);
        IndexSearcher searcher = null;
        IndexReader reader = null;
        if (index.listAll().length > 0) {
            try {

                reader = DirectoryReader.open(index);
                searcher = new IndexSearcher(reader);


            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Search.class
                        .getName()).log(Level.SEVERE, null, ex);
                //System.exit(
                //        -1);
            }


            int hitsPerPage = 100;
            TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);

            searcher.search(q, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;

            // display results
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                sb.append(d.get("id")).append("\t").append(d.get("title")).append(" - ");
            }

            // reader can only be closed when there
            // is no need to access the documents any more.
            reader.close();
        }
        return sb.toString();
    }

    List<IndexEntry> getMissingIndexEntries(Range range) {

        List<IndexEntry> res = new ArrayList<IndexEntry>();
        IndexSearcher searcher = null;
        IndexReader reader = null;
        try {
            ScoreDoc[] hits = null;
            try {
                reader = DirectoryReader.open(index);
                searcher = new IndexSearcher(reader);
                hits = getExistingDocsInRange(range.getLower(), range.getUpper(), reader, searcher);

            } catch (Exception e) {
            }
            if (hits != null) {
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d;
                    try {
                        d = searcher.doc(docId);
                        int indexId = Integer.parseInt(d.get("id"));
                        String text = d.get("title");
                        res.add(new IndexEntry(indexId, text));


                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Search.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();


                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Search.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }

        }

        return res;
    }

    ScoreDoc[] getExistingDocsInRange(int min, int max, IndexReader reader,
            IndexSearcher searcher) throws IOException {
        reader = DirectoryReader.open(index);
        searcher = new IndexSearcher(reader);

        // The line below is dangerous - we should bound the number of entries returned
        // so that it doesn't consume too much memory.
        int hitsPerPage = 100;//max - min > 0 ? max - min : 1;
        Query query = NumericRangeQuery.newIntRange("id", min, max, true, true);
        TopDocs topDocs = searcher.search(query, hitsPerPage, new Sort(new SortField("id", Type.INT)));
        return topDocs.scoreDocs;
    }

    /**
     * Called by null null null null null null null null null null null null
     * null null null null null null null null null null null null null null
     * null null null null null null null null null null null null null null
     * null null null null null null null null null null null null null null
     * null null null null null null null null null null null null null null
     * null null null null     {@link #handleMissingIndexEntriesRequest(MissingIndexEntries.Request) 
     * handleMissingIndexEntriesRequest}
     *
     * @return List of IndexEntries at this node great than max
     */
    List<IndexEntry> getEntriesGreaterThan(int max) {
        List<IndexEntry> res = new ArrayList<IndexEntry>();

        IndexReader reader = null;
        IndexSearcher searcher = null;

        try {
            ScoreDoc[] hits = null;
            try {

                hits = getExistingDocsInRange(max, maxIndexEntry,
                        reader, searcher);

            } catch (Exception e) {
            }
            if (hits != null) {
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d;
                    try {
                        reader = DirectoryReader.open(index);
                        searcher = new IndexSearcher(reader);
                        d = searcher.doc(docId);
                        int indexId = Integer.parseInt(d.get("id"));
                        if ((leader != null && leader.getPeerId().compareTo(self.getPeerId()) == 0) || buffer/*.subList(buffer.size() / 2, buffer.size())*/.contains(indexId)) {
                            String text = d.get("title");
                            res.add(new IndexEntry(indexId, text));


                        } else {
                            //logger.info("Peer:" + self + ", skipping entry:"+indexId);
                        }
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Search.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();


                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Search.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }

        }
        return res;
    }
    /**
     * Send missing index entries back to requester.
     */
    Handler<MissingIndexEntries.Request> handleMissingIndexEntriesRequest = new Handler<MissingIndexEntries.Request>() {
        @Override
        public void handle(MissingIndexEntries.Request event) {
            //logger.info("Peer:" + self + ", rcv from:" + event.getPeerSource() + ", missingEntries:" + event.getMissingRanges());
            List<IndexEntry> missingEntries = new ArrayList<IndexEntry>();
            for (Range r : event.getMissingRanges()) {
                List<IndexEntry> missing = getMissingIndexEntries(r);
                if (event.getPeerSource().getPeerId().compareTo(Snapshot.minPeer) == 0) {
                    //logger.info("Peer:" + self + ", range:" + r + ", missing:" + missing);
                }
                missingEntries.addAll(missing);
            }
            if (!missingEntries.isEmpty()) {
                //logger.info("Peer:" + self + ", send to:" + event.getPeerSource() + ", missing:" + missingEntries);
                if (Snapshot.totalEntriesAdded > 0 && Snapshot.getTotalEntriesPerPeer().get(self) < Snapshot.numberOfEntries) {
                    int entries = Snapshot.cyclonEntriesMessages.get(self) + 1;
                    Snapshot.cyclonEntriesMessages.put(self, entries);
                }
                trigger(new MissingIndexEntries.Response(self, event.getPeerSource(), missingEntries), networkPort);
            }
        }
    };
    /**
     * Μerge the missing index entries in your lucene index.
     */
    Handler<MissingIndexEntries.Response> handleMissingIndexEntriesResponse = new Handler<MissingIndexEntries.Response>() {
        @Override
        public synchronized void handle(MissingIndexEntries.Response event) {
            List<IndexEntry> missingEntries = new ArrayList<IndexEntry>();
            missingEntries.addAll(event.getEntries());
            //logger.info("Peer:" + self + ", from:" + event.getPeerSource() + ", indexSize:" + getIndexStoreSize());

            for (IndexEntry entry : missingEntries) {
                if (!indexStoreContains(entry.getIndexId())) {
                    try {
                        //logger.info("Peer:" + self + ", adding entry:" + entry.getIndexId());
                        addEntry(entry.getText(), entry.getIndexId());
                        updateIndexPointers(entry.getIndexId());


                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Search.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }
            Snapshot.addEntries(self, getIndexStoreSize());
        }
    };
    Handler<UpdateIndexTimeout> handleUpdateIndexTimeout = new Handler<UpdateIndexTimeout>() {
        @Override
        public void handle(UpdateIndexTimeout event) {
            //If Gradient component has failed, throw exception in order to die.
            if (cyclon.simulator.snapshot.Snapshot.getDeadLeaders().contains(self.getPeerId())) {

                int a = 5 / 0;
                return;
            }

            if (!cyclonSample.isEmpty()) {
                PeerAddress dest = null;
                int counter = 0;
                while (dest == null) {
                    int randomPeer = random.nextInt(cyclonSample.size());
                    dest = cyclonSample.get(randomPeer);
                    //logger.info("Peer:" + self + ", neighbours:" + neighbours + ", randomPeer:" + randomPeer + ", dest:" + dest);
                    counter++;
                    if (counter > 1) {
                        //System.out.println("counter:" + counter);
                    }
                }

                List<Range> missingIndexEntries = getMissingRangesFromIndexStore(true);//getMissingRanges();

                // Send a MissingIndexEntries.Request for the missing index entries to dest
                if (missingIndexEntries != null && !missingIndexEntries.isEmpty()) {
                    if (Snapshot.totalEntriesAdded > 0 && Snapshot.getTotalEntriesPerPeer().get(self) < Snapshot.numberOfEntries) {
                        int entries = Snapshot.cyclonEntriesMessages.get(self) + 1;
                        Snapshot.cyclonEntriesMessages.put(self, entries);

                    }
                    trigger(new MissingIndexEntries.Request(self, dest, missingIndexEntries), networkPort);
                }
            }

        }
    };
    /**
     * Send maxIndexEntry to requester.
     */
    Handler<GetMaxEntryFromGradientRequest> handleGetMaxEntryFromGradientRequest = new Handler<GetMaxEntryFromGradientRequest>() {
        @Override
        public void handle(GetMaxEntryFromGradientRequest event) {
            //Reply with maxIndexEntry
            trigger(new GetMaxEntryFromGradientResponse(self, event.getPeerSource(), maxIndexEntry), networkPort);
        }
    };
    /**
     * Get maxEntryID from random peers.
     */
    Handler<GetMaxEntryFromGradientResponse> handleGetMaxEntryFromGradientResponse = new Handler<GetMaxEntryFromGradientResponse>() {
        @Override
        public void handle(GetMaxEntryFromGradientResponse event) {
            //Check if event ID is greater than maxIndexEntry
            //logger.info("Peer:" + self + ", from:" + event.getPeerSource() + ", new entryID:" + entryID + ", rcvd:" + event.getEntryId() + ", mymaxEntryID:" + maxIndexEntry);
            if (event.getEntryId() > entryID) {
                entryID = event.getEntryId();
                //maxIndexEntry = entryID;
            }
        }
    };
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            // receive a new list of neighbours
            ArrayList<PeerAddress> cyclonSampleTemp = new ArrayList<PeerAddress>();
            cyclonSampleTemp.addAll(event.getSample());
            //logger.info("Peer:" + self + ", cyclonSample1:" + cyclonSample);
            neighbours.clear();
            cyclonSample.clear();

            if (cyclonSampleTemp.size() > 0) {
                //logger.info("Peer:" + self + ", cyclonBefore:" + cyclonSample);
                ArrayList<PeerAddress> cyclonToRemove = new ArrayList<PeerAddress>();
                for (PeerAddress peer : cyclonSampleTemp) {
                    if (cyclon.simulator.snapshot.Snapshot.getDeadLeaders().contains(peer.getPeerId())) {
                        cyclonSampleTemp.remove(peer);
                        break;
                    }
                }
                neighbours.addAll(cyclonSampleTemp);

                for (PeerAddress peer : cyclonSampleTemp) {
                    if (peer.getPeerAddress().getId() % searchConfiguration.getNumPartitions() != selfPartitionID) {
                        cyclonToRemove.add(peer);
                    }
                }
                if (!cyclonToRemove.isEmpty()) {
                    for (PeerAddress peerToRemove : cyclonToRemove) {
                        cyclonSampleTemp.remove(peerToRemove);
                    }
                }
                cyclonSample.addAll(cyclonSampleTemp);
            }

            //Send filtered sample to Gradient
            trigger(new CyclonSampleToGradientEvent(cyclonSample), leaderPort);
            // update routing tables
            for (PeerAddress p : neighbours) {
                //*********** TODO: Decide if peerID(BigInteger) or Address Id to use *************
                if (p != null && p.getPeerAddress() != null) {
                    int partition = p.getPeerAddress().getId() % searchConfiguration.getNumPartitions();
                    List<PeerDescriptor> nodes = routingTable.get(partition);
                    if (nodes == null) {
                        nodes = new ArrayList<PeerDescriptor>();
                        routingTable.put(partition, nodes);
                    }
                    // Note - this might replace an existing entry in Lucene
                    PeerDescriptor pd = new PeerDescriptor(p);
                    if (!nodes.contains(pd)) {
                        nodes.add(pd);
                    }
                    // keep the freshest descriptors in this partition
                    Collections.sort(nodes, peerAgeComparator);
                    List<PeerDescriptor> nodesToRemove = new ArrayList<PeerDescriptor>();
                    for (int i = nodes.size(); i > searchConfiguration.getMaxNumRoutingEntries(); i--) {
                        nodesToRemove.add(nodes.get(i - 1));
                    }
                    nodes.removeAll(nodesToRemove);
                }
            }
//            logger.info("Peer:" + self + ", routingTable:" + routingTable);
        }
    };
    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            // receive a new list of neighbours
            //ArrayList<PeerAddress> sampleNodes = event.getSample();
            // Pick a node or more, and exchange index with them
        }
    };
    Handler<GetLeaderResponse> handleGetLeaderResponse = new Handler<GetLeaderResponse>() {
        @Override
        public synchronized void handle(GetLeaderResponse event) {
            if (event.getLeader() != null) {
                leader = event.getLeader();
                Snapshot.updateLeader(leader, selfPartitionID);
                //logger.info("Peer:" + self + " got leader from Gradient:" + event.getLeader());
            }
        }
    };
    Handler<GetLeaderRequest> handleGetLeaderRequest = new Handler<GetLeaderRequest>() {
        @Override
        public void handle(GetLeaderRequest event) {
            trigger(new GetLeaderEvent(false), leaderPort);
        }
    };
    /**
     * Leader adds entries sent by other peers.
     */
    Handler<EntriesToSearchEvent> handleEntriesToSearchEvent = new Handler<EntriesToSearchEvent>() {
        @Override
        public synchronized void handle(EntriesToSearchEvent event) {
            Map<Integer, String> entriesToAdd = new HashMap<Integer, String>();
            entriesToAdd.putAll(event.getEntries());
            //logger.info("Peer:" + self + " Got entries to add to Lucene:" + entriesToAdd.size());
            for (Integer id : entriesToAdd.keySet()) {
                try {
                    //logger.info("Peer:" + self + " leader adding ENTRY:"+entriesToAdd.get(id),","+entryID);
                    entryID++;
                    addEntry(entriesToAdd.get(id), entryID);
                    updateIndexPointers(entryID);

                    Snapshot.addEntries(self, getIndexStoreSize());


                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Search.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
            //logger.info("Peer:" + self + " indexStore LEADER:" + getIndexStoreSize());
        }
    };
//-------------------------------------------------------------------	    
    Handler<AddIndexText> handleAddIndexText = new Handler<AddIndexText>() {
        @Override
        public void handle(AddIndexText event) {
            entriesToLeader.put(localEntryID, event.getText());
            localEntryID++;
        }
    };
    /**
     * Periodically send buffered added entries to Leader.
     */
    Handler<SendEntriesToLeaderTimeout> handleSendEntriesToLeaderTimeout = new Handler<SendEntriesToLeaderTimeout>() {
        @Override
        public void handle(SendEntriesToLeaderTimeout event) {
            if (!entriesToLeader.isEmpty() && leader != null) {
                //Send Entries to leader
                //logger.info("Peer:" + self + ", sending to leader entries:" + entriesToLeader);
                Map<Integer, String> temp = new HashMap<Integer, String>();
                temp.putAll(entriesToLeader);
                for (Integer currId : pendingEntries) {
                    temp.remove(currId);
                }
                pendingEntries.addAll(temp.keySet());
                trigger(new EntriesToLeaderMessage(self, leader, temp), networkPort);

                //TODO: ENABLE THIS IF NO BETTER SOLUTION IS FOUNDentriesToLeader.clear();
                //entriesToLeader.clear();
            } else if (!entriesToLeader.isEmpty() && leader == null) {
                trigger(new GetLeaderEvent(true), leaderPort);
            }
        }
    };
    /**
     * Acknowledged added entries.
     */
    Handler<EntriesSentACK> handleEntriesSentACK = new Handler<EntriesSentACK>() {
        @Override
        public void handle(EntriesSentACK event) {
            Map<Integer, String> temp = new HashMap<Integer, String>();
            temp.putAll(event.getEntries());
            //Remove Entries successfully send
            for (Integer id : temp.keySet()) {

                entriesToLeader.remove(id);
                pendingEntries.remove(id);

            }
        }
    };
    /**
     * Receive request from Gradient Peer and send him missing entries.
     */
    Handler<GetEntriesFromGradient> handleGetEntriesFromGradientMessage = new Handler<GetEntriesFromGradient>() {
        @Override
        public void handle(GetEntriesFromGradient event) {
            //Send entries from Buffer than are greater that event's lastMissingEntry
            int peerLastMissingEntry = event.getLastMissingEntry();
            List<IndexEntry> entriesToSend = getEntriesGreaterThan(peerLastMissingEntry);

            if (entriesToSend != null && !entriesToSend.isEmpty()) {
                //logger.info("Peer:" + self + " send to:" + event.getPeerSource() + ", entries:" + entriesToSend);
                trigger(new GetEntriesFromGradientResponseMessage(self, event.getPeerSource(), entriesToSend), networkPort);

                if (Snapshot.totalEntriesAdded > 0 && Snapshot.getTotalEntriesPerPeer().get(self) < Snapshot.numberOfEntries) {
                    int entries = Snapshot.gradientEntriesMessages.get(self) + 1;
                    Snapshot.gradientEntriesMessages.put(self, entries);
                }
            }

        }
    };
    /**
     * Receives requested entries and adds them to Lucene.
     */
    Handler<GetEntriesFromGradientResponseMessage> handleGetEntriesFromGradientResponseMessage = new Handler<GetEntriesFromGradientResponseMessage>() {
        @Override
        public synchronized void handle(GetEntriesFromGradientResponseMessage event) {
            //Check if an entry is already in indexStore
            List<IndexEntry> entries = new ArrayList<IndexEntry>();
            entries.addAll(event.getEntries());
            for (IndexEntry entry : entries) {
                if (!indexStoreContains(entry.getIndexId())) {
                    try {
                        addEntry(entry.getText(), entry.getIndexId());
                        updateIndexPointers(entry.getIndexId());
                        buffer.add(entry.getIndexId());
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Search.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                } 
            }
            Snapshot.addEntries(self, getIndexStoreSize());
            
        }
    };
    /**
     * Receive query results from partitions.
     */
    Handler<GetPartitionEntriesResponse> handleGetEntryFromPartitionsResponseMessage = new Handler<GetPartitionEntriesResponse>() {
        @Override
        public void handle(GetPartitionEntriesResponse event) {
            //Add results to searchResults map
            int senderPartitionID = event.getSource().getId() % searchConfiguration.getNumPartitions();
           
            if (pendingResults.get(event.getRequestID()) != null) {
                if (!pendingResults.get(event.getRequestID()).containsKey(senderPartitionID)) {
                    Map<Integer, Set<String>> partitionResults = new HashMap<Integer, Set<String>>();
                    partitionResults.put(senderPartitionID, new HashSet<String>());
                    pendingResults.get(event.getRequestID()).putAll(partitionResults);
                }

                pendingResults.get(event.getRequestID()).get(senderPartitionID).add(event.getResults());

            }
            
        }
    };
    /**
     * Receive query, search in own index and return results.
     */
    Handler<GetPartitionEntriesRequest> handleGetEntryFromPartitionsRequestMessage = new Handler<GetPartitionEntriesRequest>() {
        @Override
        public void handle(GetPartitionEntriesRequest event) {
            try {
                //SEARCH LUCENE AND SEND RESULTS
                StringBuilder sb = new StringBuilder();
                
                query(sb, event.getQuery(), "title");
                trigger(new GetPartitionEntriesResponse(self, event.getPeerSource(), sb.toString(), event.getRequestID()), networkPort);
            } catch (ParseException ex) {
                java.util.logging.Logger.getLogger(Search.class
                        .getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Search.class
                        .getName()).log(Level.SEVERE, null, ex);
            }

        }
    };
    /**
     * Periodically send request to Gradient that will contact similar set and
     * retrieve missing entries.
     */
    Handler<GetEntriesFromGradientTimeout> handleGetEntriesFromGradientTimeout = new Handler<GetEntriesFromGradientTimeout>() {
        @Override
        public void handle(GetEntriesFromGradientTimeout event) {
            List<Range> ranges = getMissingRangesFromIndexStore(false);
            if (ranges.isEmpty()) {
                if (Snapshot.totalEntriesAdded > 0 && Snapshot.getTotalEntriesPerPeer().get(self) < Snapshot.numberOfEntries) {
                    int entries = Snapshot.gradientEntriesMessages.get(self) + 1;
                    Snapshot.gradientEntriesMessages.put(self, entries);
                }
                //logger.info("Peer:" + self + ", send request, entries:" + Snapshot.gradientEntriesMessages.get(self));
                trigger(new GetEntriesFromGradientRequest(/*ranges.get(ranges.size() - 1).getLower(), */maxIndexEntry + 1), leaderPort);
            } else {
                if (Snapshot.totalEntriesAdded > 0 && Snapshot.getTotalEntriesPerPeer().get(self) < Snapshot.numberOfEntries) {
                    int entries = Snapshot.gradientEntriesMessages.get(self) + 1;
                    Snapshot.gradientEntriesMessages.put(self, entries);
                }
                //logger.info("Peer:" + self + ", send request, entries:" + Snapshot.gradientEntriesMessages.get(self));
                trigger(new GetEntriesFromGradientRequest(ranges.get(ranges.size() - 1).getLower()), leaderPort);

            }
        }
    };
    /**
     * Clears buffer that stores recently added entries from Gradient peers.
     */
    Handler<EmptyBufferTimeout> handleEmptyBufferTimeout = new Handler<EmptyBufferTimeout>() {
        @Override
        public void handle(EmptyBufferTimeout event) {
            //logger.info("Peer:" + self + " clearing buffer:" + buffer);
            buffer.clear();
        }
    };
   
    Handler<SendWebresponseTimeout> handleSendWebresponseTimeout = new Handler<SendWebresponseTimeout>() {
        @Override
        public synchronized void handle(SendWebresponseTimeout event) {

            StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
            sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
            sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
            sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
            sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
            sb.append("<title>Kompics P2P Bootstrap Server</title>");
            sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
            sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
            sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
            sb.append("ID2210 (Decentralized Search for Piratebay)</h2><br>");
            //Check
            //for every request id in pendingresults
            for (String reqID : pendingResults.keySet()) {
                //If we got results from all partitions return results to client
                //and remove entry from pendingResults Map
                if (pendingResults.get(reqID).size() == searchConfiguration.getNumPartitions()) {
                    if (timesRequestWasChecked.get(reqID) > 5) {
                        sb.append("BAD LUCK!");
                    } else {
                        for (Integer partitionid : pendingResults.get(reqID).keySet()) {
                            sb.append("Partition: ")
                                    .append(partitionid)
                                    .append(" - ")
                                    .append(pendingResults.get(reqID).get(partitionid))
                                    .append("<br>========================<br>");
                        }
                    }
                    sb.append("</body></html>");
                    WebResponse response = new WebResponse(sb.toString(), webRequestPerReqID.get(reqID), 1, 1);
                    trigger(response, webPort);
                    //logger.info("Peer:" + self + ", pendingResBefore:" + pendingResults);
                    pendingResults.remove(reqID);
                    //logger.info("Peer:" + self + "to remove from webRequestPerReqID:" + webRequestPerReqID + ", reqID:" + reqID);
                    webRequestPerReqID.remove(reqID);
                    timesRequestWasChecked.remove(reqID);
                    logger.info("Peer:" + self + ", pendingResAfter:" + pendingResults);
                } else {
                    int updatedcounter = timesRequestWasChecked.get(reqID) + 1;
                    timesRequestWasChecked.put(reqID, updatedcounter);
                }
            }



        }
    };

    /**
     * Index Exchange TimerTimer
     *
     * @param period : Time
     */
    private void startTimer(int period) {
        ScheduleTimeout st = new ScheduleTimeout(period);
        st.setTimeoutEvent(new UpdateIndexTimeout(st));
        trigger(st, timerPort);
    }

    private synchronized void updateIndexPointers(int id) {
        indexStore[id] = id;
    }

    private synchronized int getIndexStoreSize() {
        int size = 0;
        for (int i = 0; i < maxIndexEntry + 1; i++) {
            if (indexStore[i] > -1) {
                size++;
            }
        }
        return size;
    }

    /**
     * Returns number of stored indexes.
     * @param arr
     * @return 
     */
    private static int getIndexStoreSize(int arr[]) {
        int size = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > -1) {
                size++;
            }
        }
        return size;
    }

    private synchronized List<Range> getMissingRangesFromIndexStore(boolean fromUpdateIndex) {
        List<Range> ranges = new ArrayList<Range>();
        boolean foundStart = false;
        Range r = null;
        for (int i = 0; i <= maxIndexEntry; i++) {

            if (indexStore[i] == -1) {
                if (!foundStart) {
                    r = new Range(0, 0);
                    r.setLower(i);
                    r.setUpper(i);
                    foundStart = true;
                } else {
                    r.setUpper(i);
                }
            } else if ((indexStore[i] > -1) && r != null) {
                Range r2 = new Range(0, 0);
                r2.setLower(r.getLower());
                r2.setUpper(r.getUpper());
                ranges.add(r2);
                foundStart = false;
                r = null;
            }

        }

//        if (fromUpdateIndex) {
//            ranges.add(new Range(maxIndexEntry + 1, Integer.MAX_VALUE));
//        }
        return ranges;
    }

    private boolean indexStoreContains(int id) {
        for (int i = 0; i < indexStore.length; i++) {
            if (indexStore[i] == id) {
                return true;
            }
        }
        return false;
    }
}
