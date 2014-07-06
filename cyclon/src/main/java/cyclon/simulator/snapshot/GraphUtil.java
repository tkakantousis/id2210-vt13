package cyclon.simulator.snapshot;


import common.peer.PeerAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import se.sics.kompics.p2p.overlay.OverlayAddress;

public class GraphUtil {
	private int n;
	private double inDegree[];
	private int outDegree[];
	private double clustering[];
	private int[][] neighbors;
	private int diameter = 0;
	private double avgPathLength = 0;
	private double avgOut = 0;
	private double avgClustering = 0;
	private int clusterSize = 0;

	private SummaryStatistics inStats = new SummaryStatistics();
	
//-------------------------------------------------------------------
	public void init(HashMap<PeerAddress, PeerInfo> alivePeers) {
		this.n = alivePeers.size();
		this.inDegree = new double[this.n];
		this.outDegree = new int[this.n];
		this.clustering = new double[this.n];
		this.neighbors = new int[this.n][];
                this.diameter = 0;

		byte m[][] = new byte[this.n][this.n];
		int dist[][] = new int[this.n][this.n];
		PeerAddress[] a = new PeerAddress[this.n];
		HashMap<PeerAddress, Integer> map = new HashMap<PeerAddress, Integer>();

		{
			int p = 0;
			for (OverlayAddress address : alivePeers.keySet()) {
				PeerAddress src = (PeerAddress) address;
				a[p] = src;
				map.put(src, p);
				p++;
			}
		}

		// build adjacency matrix
		int d = -1;
		{
			try {
				for (int s = 0; s < a.length; s++) {
					PeerAddress src = a[s];
					ArrayList<PeerAddress> neigh = alivePeers.get(src).getPartners();
					
					
					int nn = 0;
					if (neigh != null) {
						for (PeerAddress desc : neigh) {
							PeerAddress dst = desc;
							
							if (!map.containsKey(dst))
								continue;
							
							d = map.get(dst);
							m[s][d] = 1;
							this.inDegree[d]++;
							this.outDegree[s]++;
							nn++;
						}
					}
					
					this.neighbors[s] = new int[nn];
					
					if (neigh != null) {
						nn = 0;
						for (PeerAddress desc : neigh) {
							PeerAddress dst = desc;
							if (map.containsKey(dst))
								this.neighbors[s][nn++] = map.get(dst);					
						}
					}
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		
		this.findClusterSize(m);
//		for (int i = 0; i < this.n; i++) {
//			System.out.println();
//			for (int j = 0; j < this.n; j++)
//				System.out.print(m[i][j] + " ");
//		}
//		System.out.println();

		
		// build distance matrix, clustering coefficient, average path length
		// diameter and average degrees
		{
			for (int i = 0; i < this.n; i++) {
				bfs(i, dist[i]);
	
				// we compute the clustering coefficient here
				int neigh[] = this.neighbors[i];
				if (neigh.length <= 1) {
					this.clustering[i] = 1.0;
					continue;
				}

				int edges = 0;
	
				for (int j = 0; j < neigh.length; j++) {
					for (int k = j + 1; k < neigh.length; k++) {
						if (m[neigh[j]][neigh[k]] > 0 || m[neigh[k]][neigh[j]] > 0)
							++edges;
					}
				}

				this.clustering[i] = ((edges * 2.0) / neigh.length) / (neigh.length - 1);
			}
			
			int k = 0;
			for (int i = 0; i < this.n; i++) {
				for (int j = 0; j < this.n; j++) {
					if (i == j)
						continue;
					
					if (dist[i][j] > this.diameter) {
						this.diameter = dist[i][j];
//                                                System.out.println("Diameter(" + i + "," + 
//                                                        j + ")=" + dist[i][j]);
                                        }
					
					this.avgPathLength = (this.avgPathLength * k + dist[i][j]) / (k + 1);
					k++;
				}
				
				this.inStats.addValue(this.inDegree[i]);
				this.avgOut = (this.avgOut * i + this.outDegree[i]) / (i + 1);
				this.avgClustering = (this.avgClustering * i + this.clustering[i]) / (i + 1);
			}
		}
	}
	
//-------------------------------------------------------------------
	private void bfs(int v, int d[]) {
		Queue<Integer> q = new LinkedList<Integer>();

		for (int i = 0; i < this.n; i++)
			d[i] = this.n; // also means that the node has not been visited
		
		d[v] = 0;
		q.offer(v);
		q.offer(0); // depth of v
		
		while (!q.isEmpty()) {
			int u = q.poll();
			int du = q.poll(); // depth of u

			for (int t = 0; t < this.neighbors[u].length; t++) {
				if (d[neighbors[u][t]] == n) {
					// on the first encounter, add to the queue
					d[this.neighbors[u][t]] = du + 1;
					q.offer(this.neighbors[u][t]);
					q.offer(du + 1);
				}
			}
		}
	}

//-------------------------------------------------------------------
	private void findClusterSize(byte m[][]) {
		Integer highest = Integer.MIN_VALUE;
		HashMap<Integer, ArrayList<Integer>> clusters = new HashMap<Integer, ArrayList<Integer>>();
		ArrayList<Integer> nodeNeighbors = new ArrayList<Integer>();
		LinkedList<Integer> list = new LinkedList<Integer>();
		ArrayList<Integer> nodes = new ArrayList<Integer>();
		ArrayList<Integer> allNodes = new ArrayList<Integer>();
		int selectedNodes[] = new int[this.n];
		for (int i = 0; i < this.n; i++)
			selectedNodes[i] = 0;

		int count = 0;
		int freeNode;

		while ((freeNode = this.findFreeNode(selectedNodes)) != -1) {
			selectedNodes[freeNode] = 1;
			list.add(freeNode);
			nodes.add(freeNode);
			allNodes.add(freeNode);
			
			while (!list.isEmpty()) {
				nodeNeighbors = this.findNeighbors(m, list.poll());
		
				for (int node : nodeNeighbors) {
					selectedNodes[node] = 1;
					
					if (!allNodes.contains(node)) {
						allNodes.add(node);
						nodes.add(node);
						list.add(node);
					}
				}
			}
			
			count++;
			clusters.put(count, new ArrayList<Integer>(nodes));
			nodes.clear();
		}
		
		for (Integer cluster : clusters.keySet()) {
			if (clusters.get(cluster).size() > highest)
				highest = clusters.get(cluster).size();
		}
		
		this.clusterSize = highest;
	}

//-------------------------------------------------------------------
	private int findFreeNode(int selectedNodes[]) {
		for (int i = 0; i < this.n; i++) {
			if (selectedNodes[i] == 0)
				return i;
		}
		
		return -1;			
	}

//-------------------------------------------------------------------
	private ArrayList<Integer> findNeighbors(byte m[][], int node) {
		ArrayList<Integer> nodes = new ArrayList<Integer>();
		
		for (int i = 0; i < this.n; i++) {
			if (m[node][i] == 1)
				nodes.add(i);
		}
		
		for (int j = 0; j < this.n; j++) {
			if (m[j][node] == 1 && !nodes.contains(j))
				nodes.add(j);
		}
		
		return nodes;			
	}

//-------------------------------------------------------------------
	public int getInDegree(int v) {
		if (v < this.n)
			return (int)this.inDegree[v];
		else
			return 0;
	}

//-------------------------------------------------------------------
	public int getOutDegree(int v) {
		if (v < this.n)
			return this.outDegree[v];
		else
			return 0;
	}

//-------------------------------------------------------------------
	public double getClustering(int v) {
		if (v < this.n)
			return this.clustering[v];
		else
			return 0;
	}

//-------------------------------------------------------------------
	public double getMinInDegree() {
		return this.inStats.getMin();
	}

//-------------------------------------------------------------------
	public double getMaxInDegree() {
		return this.inStats.getMax();
	}

//-------------------------------------------------------------------
	public double getMeanInDegree() {
		return this.inStats.getMean();
	}

//-------------------------------------------------------------------
	public double getInDegreeStdDev() {
		return this.inStats.getStandardDeviation();
	}

//-------------------------------------------------------------------
	public double getMeanOutDegree() {
		return this.avgOut;
	}

//-------------------------------------------------------------------
	public double getMeanClusteringCoefficient() {
		return this.avgClustering;
	}

//-------------------------------------------------------------------
	public double getMeanPathLength() {
		return this.avgPathLength;
	}

//-------------------------------------------------------------------
	public int getDiameter() {
		return this.diameter;
	}
	
//-------------------------------------------------------------------
	public int getMaxClusterSize() {
		return this.clusterSize;
	}

}
