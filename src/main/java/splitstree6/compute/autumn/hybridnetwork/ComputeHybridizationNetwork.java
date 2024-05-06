/*
 *  ComputeHybridizationNetwork.java Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package splitstree6.compute.autumn.hybridnetwork;


import jloda.fx.window.NotificationManager;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import org.apache.commons.collections4.map.LRUMap;
import splitstree6.compute.autumn.*;
import splitstree6.compute.autumn.hybridnumber.ComputeHybridNumber;
import splitstree6.data.TaxaBlock;
import splitstree6.utils.ProgressMover;

import java.io.IOException;
import java.util.*;

/**
 * computes minimal hybridization networks for two multifurcating trees
 * Daniel Huson, 4.2011
 */
public class ComputeHybridizationNetwork {
	private final static int LARGE = 10000;
	public static final boolean checking = false;
	public boolean verbose = false;

	private int numberOfLookups = 0;

	private final LRUMap<String, Pair<Integer, Collection<Root>>> lookupTable = new LRUMap<>(1000000);

	private ProgressListener progress;

	/**
	 * computes the hybrid number for two multifurcating trees
	 *
	 * @return reduced trees
	 */
	public static PhyloTree[] apply(TaxaBlock taxaBlock, PhyloTree tree1, PhyloTree tree2, ProgressListener progress, Single<Integer> hybridizationNumber) throws IOException {
		var upperBound = ComputeHybridNumber.apply(tree1, tree2, progress);

		var computeHybridizationNetwork = new ComputeHybridizationNetwork();
		var networks = computeHybridizationNetwork.run(tree1, tree2, upperBound, hybridizationNumber, progress);

		var label2taxonId = new HashMap<String, Integer>();
		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			label2taxonId.put(taxaBlock.getLabel(t), t);
		}

		var n = 0;
		for (var tree : networks) {
			tree.setName("H" + (++n));
			var seen = new HashSet<String>();
			for (var v : tree.nodes()) {
				var label = tree.getLabel(v);
				if (label != null) {
					if (seen.contains(label))
						NotificationManager.showWarning("Tree " + tree.getName() + ": Multiple occurrence of leaf label: " + label);
					else
						seen.add(label);
					var id = label2taxonId.get(label);
					if (id == -1)
						System.err.println("Label not found: " + label);
					else
						tree.addTaxon(v, id);
				}
			}
		}
		return networks;
	}

	/**
	 * run the algorithm
	 *
	 * @return reduced trees
	 */
	private PhyloTree[] run(PhyloTree tree1, PhyloTree tree2, int upperBound, Single<Integer> hybridizationNumber, ProgressListener progress) throws IOException {
		this.progress = progress;
		var startTime = System.currentTimeMillis();
		verbose = ProgramProperties.get("verbose-HL", false);
		var allTaxa = new TaxaBlock();
		var roots = PreProcess.apply(tree1, tree2, allTaxa);
		var root1 = roots.getFirst();
		var root2 = roots.getSecond();

		if (root1.getOutDegree() == 1 && root1.getFirstOutEdge().getTarget().getOutDegree() > 0) {
			var tmp = (Root) root1.getFirstOutEdge().getTarget();
			root1.deleteNode();
			root1 = tmp;
		}

		if (root2.getOutDegree() == 1 && root2.getFirstOutEdge().getTarget().getOutDegree() > 0) {
			var tmp = (Root) root2.getFirstOutEdge().getTarget();
			root2.deleteNode();
			root2 = tmp;
		}

		var onlyTree1 = Cluster.setminus(root1.getTaxa(), root2.getTaxa());
		var onlyTree2 = Cluster.setminus(root2.getTaxa(), root1.getTaxa());

		if (root1.getTaxa().cardinality() == onlyTree1.cardinality())
			throw new IOException("None of the taxa in second tree are contained in first tree");
		if (root2.getTaxa().cardinality() == onlyTree2.cardinality())
			throw new IOException("None of the taxa in first tree are contained in second tree");

		if (onlyTree1.cardinality() > 0) {
			System.err.println("Killing all taxa only present in first tree: " + onlyTree1.cardinality());
			for (var t : BitSetUtils.members(onlyTree1)) {
				RemoveTaxon.apply(root1, 1, t);
			}
		}

		if (onlyTree2.cardinality() > 0) {
			System.err.println("Killing all taxa only present in second tree: " + onlyTree2.cardinality());
			for (var t : BitSetUtils.members(onlyTree2)) {
				RemoveTaxon.apply(root2, 2, t);
			}
		}

		// run the refine algorithm
		if (verbose)
			System.err.println("Computing common refinement of both trees");
		Refine.apply(root1, root2);

		if (tree1.getRoot() == null || tree2.getRoot() == null) {
			throw new IOException("Can't compute hybridization networks, at least one of the trees is empty or unrooted");
		}

		// we maintain both trees in lexicographic order for ease of comparison
		root1.reorderSubTree();
		root2.reorderSubTree();

		System.err.println("Computing hybridization networks using Autumn algorithm");
		this.progress.setTasks("Computing hybridization networks", "(Unknown how long this will really take)");
		var result = new TreeSet<>(new NetworkComparator());
		int h;
		try (var progressMover = new ProgressMover(progress)) {
			h = computeRec(root1, root2, false, getAllAliveTaxa(root1, root2), upperBound, result, ">");
			fixOrdering(result);
		}

		if (false) {
			var maafs = MAAFUtils.computeAllMAAFs(result);
			System.err.println("MAAFs before:");
			for (Root root : maafs) {
				System.err.println(root.toStringNetworkFull());
			}
		}
		int numberOfDuplicatesRemoved = MAAFUtils.removeDuplicateMAAFs(result, false);
		if (numberOfDuplicatesRemoved > 0)
			System.err.println("MAAF duplicates removed: " + numberOfDuplicatesRemoved);
		if (false) {
			var maafs = MAAFUtils.computeAllMAAFs(result);
			System.err.println("MAAFs after:");
			for (var root : maafs) {
				System.err.println(root.toStringNetworkFull());
			}
		}

		fixOrdering(result);

		BitSet missingTaxa = Cluster.union(onlyTree1, onlyTree2);
		if (missingTaxa.cardinality() > 0) {
			if (verbose)
				System.err.println("Reattaching killed taxa: " + missingTaxa.cardinality());
			for (var r : result) {
				for (var t : BitSetUtils.members(missingTaxa)) {
					RemoveTaxon.unapply(r, t);
				}
			}
		}

		System.err.println("Hybridization number: " + h);
		hybridizationNumber.set(h);
		System.err.println("Total networks: " + result.size());
		if (verbose)
			System.err.println("Time: " + ((System.currentTimeMillis() - startTime) / 1000) + " secs");

		if (verbose)
			System.err.println("(Size lookup table: " + lookupTable.size() + ", number of times used: " + numberOfLookups + ")");
		lookupTable.clear();
		System.gc();

		if (false) {
			System.err.println("Networks:");
			for (var root : result) {
				System.err.println(root.toStringNetworkFull());
			}
		}

		var list = PostProcess.apply(result.toArray(new Root[0]), allTaxa, false);
		return list.toArray(new PhyloTree[0]);
	}

	/**
	 * this is called between recursive calls of the algorithm to cache networks already computed
	 *
	 * @return cached networks or newly computed networks
	 */
	private int cacheComputeRec(Root root1, Root root2, boolean isReduced, BitSet candidateHybrids, int k, Collection<Root> totalResults, String depth) throws IOException {
		if (true) // use caching
		{
			var key = root1.toStringTree() + root2.toStringTree() + (candidateHybrids != null ? StringUtils.toString(candidateHybrids) : "");
			var cachedResults = lookupTable.get(key);
			if (cachedResults != null) {
				totalResults.addAll(cachedResults.getSecond());

				if (cachedResults.getFirst() <= k) {
					numberOfLookups++;
					totalResults.addAll(cachedResults.getSecond());
					return cachedResults.getFirst();
				}

				return cachedResults.getFirst();
			} else {
				var newResults = new TreeSet<>(new NetworkComparator());
				var h = computeRec(root1, root2, isReduced, candidateHybrids, k, newResults, depth);

				if (h > 0)
					lookupTable.put(key, new Pair<>(h, newResults));
				totalResults.addAll(newResults);
				return h;
			}
		} else {
			return computeRec(root1, root2, isReduced, candidateHybrids, k, totalResults, depth);
		}
	}

	/**
	 * recursively compute the hybrid number
	 *
	 * @param isReduced @return hybrid number
	 */
	private int computeRec(Root root1, Root root2, boolean isReduced, BitSet candidateHybridsOriginal, int k, Collection<Root> totalResults, String depth) throws IOException {
		if (verbose) {
			System.err.println(depth + "---------- ComputeRec:");
			System.err.println(depth + "Tree1: " + root1.toStringFullTreeX());
			System.err.println(depth + "Tree2: " + root2.toStringFullTreeX());
		}

		progress.checkForCancel();

		// root1.reorderSubTree();
		//  root2.reorderSubTree();
		if (checking) {
			root1.checkTree();
			root2.checkTree();
			if (!root2.getTaxa().equals(root1.getTaxa()))
				throw new RuntimeException("Unequal taxon sets: X=" + StringUtils.toString(root1.getTaxa()) + " vs " + StringUtils.toString(root2.getTaxa()));
		}

		if (!isReduced) {
			// 1. try to perform a subtree reduction:
			{
				final var placeHolderTaxon = new Single<Integer>();
				var reducedSubtreePairs = new LinkedList<Pair<Root, Root>>();

				switch (SubtreeReduction.apply(root1, root2, reducedSubtreePairs, placeHolderTaxon)) {
					case ISOMORPHIC:
						var isomorphicTree = MergeIsomorphicInducedTrees.apply(root1, root2);
						if (verbose) {
							System.err.println(depth + "Trees are isomorphic");
							System.err.println(depth + "Isomorphic tree: " + (isomorphicTree != null ? isomorphicTree.toStringFullTreeX() : "null"));
						}
						totalResults.add(isomorphicTree);
						return 0; // two trees are isomorphic, no hybrid node needed
					case REDUCED:  // a reduction was performed, cannot maintain lexicographical ordering in removal loop below
						var subTrees = new LinkedList<Root>();
						for (Pair<Root, Root> pair : reducedSubtreePairs) {
							subTrees.add(MergeIsomorphicInducedTrees.apply(pair.getFirst(), pair.getSecond()));
						}
						if (verbose) {
							System.err.println(depth + "Trees are reducible:");
							System.err.println(depth + "Tree1-reduced: " + root1.toStringFullTreeX());
							System.err.println(depth + "Tree2-reduced: " + root2.toStringFullTreeX());
							for (var root : subTrees) {
								System.err.println(depth + "Merged reduced subtree: " + root.toStringFullTreeX());
							}
						}

						BitSet candidateHybrids;
						if (false)
							candidateHybrids = getAllAliveTaxa(root1, root2);  // need to reconsider all possible hybrids
						else {
							candidateHybrids = (BitSet) candidateHybridsOriginal.clone();
							candidateHybrids.set(placeHolderTaxon.get(), true);
						}

						var currentResults = new TreeSet<>(new NetworkComparator());

						var h = cacheComputeRec(root1, root2, false, candidateHybrids, k, currentResults, depth + " >");
						var merged = MergeNetworks.apply(currentResults, subTrees);
						if (verbose) {
							for (var r : merged) {
								System.err.println(depth + "Result-merged: " + r.toStringNetworkFull());
							}
						}
						totalResults.addAll(fixOrdering(merged));
						return h;
					case IRREDUCIBLE:
						if (verbose)
							System.err.println(depth + "Trees are subtree-irreducible");
						break;
				}
			}

			// 2. try to perform a cluster reduction:
			{
				final var placeHolderTaxon = new Single<Integer>();
				var clusterTrees = ClusterReduction.apply(root1, root2, placeHolderTaxon);

				if (clusterTrees != null) {
					var resultBottomPair = new TreeSet<>(new NetworkComparator());
					var h = cacheComputeRec(clusterTrees.getFirst(), clusterTrees.getSecond(), true, candidateHybridsOriginal, k, resultBottomPair, depth + " >");

					// for the top pair, we should reconsider the place holder in the top pair as a possible place holder
					var candidateHybrids = (BitSet) candidateHybridsOriginal.clone();

					candidateHybrids.set(placeHolderTaxon.get(), true);

					var resultTopPair = new TreeSet<>(new NetworkComparator());
					h += cacheComputeRec(root1, root2, false, candidateHybrids, k - h, resultTopPair, depth + " >");

					var currentResults = new TreeSet<>(new NetworkComparator());

					for (var r : resultBottomPair) {
						currentResults.addAll(MergeNetworks.apply(resultTopPair, List.of(r)));
					}
					if (verbose) {
						System.err.println(depth + "Cluster reduction applied::");
						System.err.println(depth + "Tree1-reduced: " + root1.toStringFullTreeX());
						System.err.println(depth + "Tree2-reduced: " + root2.toStringFullTreeX());
						System.err.println(depth + "Subtree-1:     " + clusterTrees.getFirst().toStringFullTreeX());
						System.err.println(depth + "Subtree-2:     " + clusterTrees.getSecond().toStringFullTreeX());

						for (var r : resultBottomPair) {
							System.err.println(depth + "Results for reduced-trees: " + r.toStringNetworkFull());
						}

						for (var r : resultTopPair) {
							System.err.println(depth + "Results for sub-trees: " + r.toStringNetworkFull());
						}

						for (var r : currentResults) {
							System.err.println(depth + "Merged cluster-reduced networks: " + r.toStringNetworkFull());
						}
					}
					totalResults.addAll(currentResults);
					clusterTrees.getFirst().deleteSubTree();
					clusterTrees.getSecond().deleteSubTree();

					return h;
				}
			}
		} else {
			if (verbose)
				System.err.println(depth + "Trees are already reduced");
		}

		if (k <= 0) // 1, if only interested in number or in finding only one network, 0 else
			return LARGE;

		var hBest = LARGE;
		var leaves1 = getAllAliveLeaves(root1);

        /*
        if (leaves1.size() <= 2) // try 2 rather than one...
        {
            totalResults.add(MergeNetworks.update(root1,root2)); // todo: this needs to be fixed
            return 0;
        }
        */

		for (var leaf2remove : leaves1) {
			var taxa2remove = leaf2remove.getTaxa();
			if (taxa2remove.cardinality() != 1)
				throw new IOException(depth + "Leaf taxa size: " + taxa2remove.cardinality());

			var hybridTaxon = taxa2remove.nextSetBit(0);

			if (candidateHybridsOriginal.get(hybridTaxon)) {
				if (verbose) {
					System.err.println(depth + "Removing: " + hybridTaxon);
					System.err.println(depth + "candidateHybrids: " + StringUtils.toString(candidateHybridsOriginal));
					System.err.println(depth + "Tree1: " + root1.toStringFullTreeX());
					System.err.println(depth + "Tree2: " + root2.toStringFullTreeX());
				}

				var root1x = root1.copySubNetwork();
				var root2x = root2.copySubNetwork();
				RemoveTaxon.apply(root1x, 1, hybridTaxon);
				RemoveTaxon.apply(root2x, 2, hybridTaxon);    // now we keep removed taxa as separate sets

				if (verbose) {
					System.err.println(depth + "Tree1-x: " + root1x.toStringFullTreeX());
					System.err.println(depth + "Tree2-x: " + root2x.toStringFullTreeX());
				}

				Refine.apply(root1x, root2x);

				if (verbose) {
					System.err.println(depth + "Tree1-x-refined: " + root1x.toStringFullTreeX());
					System.err.println(depth + "Tree2-x-refined: " + root2x.toStringFullTreeX());
				}

				Collection<Root> currentResults = new TreeSet<>(new NetworkComparator());
				candidateHybridsOriginal.set(hybridTaxon, false);

				var h = cacheComputeRec(root1x, root2x, false, candidateHybridsOriginal, k - 1, currentResults, depth + " >") + 1;
				candidateHybridsOriginal.set(hybridTaxon, true);

				if (h < k)
					k = h;

				// System.err.println("Subproblem with " + Basic.toString(taxa2remove) + " removed, h=" + h);

				if (h < hBest && h <= k) {
					hBest = h;
					totalResults.clear();
				}
				if (h == hBest && h <= k) {
					if (verbose) {
						for (Root r : currentResults) {
							System.err.println(depth + "Result: " + r.toStringNetworkFull());
						}
					}

					// add the hybrid node:
					currentResults = copyAll(currentResults);
					AddHybridNode.apply(currentResults, hybridTaxon);
					totalResults.addAll(fixOrdering(currentResults));
				}
				root1x.deleteSubTree();
				root2x.deleteSubTree();
			}
		}
		return hBest;
	}

	/**
	 * get all alive leaves below the given root
	 *
	 * @return leaves
	 */
	private List<Root> getAllAliveLeaves(Root root) {
		var leaves = new LinkedList<Root>();
		if (root.getTaxa().cardinality() > 0) {
			if (root.getOutDegree() == 0)
				leaves.add(root);
			else {
				var queue = new LinkedList<Root>();
				queue.add(root);
				while (!queue.isEmpty()) {
					root = queue.poll();
					for (var e : root.outEdges()) {
						var w = (Root) e.getTarget();
						if (w.getTaxa().cardinality() > 0) {
							if (w.getOutDegree() == 0)
								leaves.add(w);
							else
								queue.add(w);
						}
					}
				}
			}
		}
		return leaves;
	}

	/**
	 * gets all alive taxa. Checks that both trees have the same set of alive taxa
	 *
	 * @return all alive taxa
	 */
	public BitSet getAllAliveTaxa(Root root1, Root root2) throws IOException {
		if (!root1.getTaxa().equals(root2.getTaxa()))
			throw new IOException("Trees have different sets of alive taxa: " + StringUtils.toString(root1.getTaxa()) + " vs "
								  + StringUtils.toString(root2.getTaxa()));
		return (BitSet) root1.getTaxa().clone();
	}

	/**
	 * reorder the children in all networks
	 */
	private Collection<Root> fixOrdering(Collection<Root> networks) {
		for (var root : networks) {
			// if (verbose)
			//    System.err.println("Orig ordering: " + root.toStringNetworkFull());
			root.reorderNetwork();
			// if (verbose)
			//     System.err.println("New ordering: " + root.toStringNetworkFull());
		}
		return networks;
	}

	private static Collection<Root> copyAll(Collection<Root> list) {
		var copy = new TreeSet<>(new NetworkComparator());
		for (var r : list) {
			copy.add(r.copySubNetwork());
		}
		return copy;
	}
}
