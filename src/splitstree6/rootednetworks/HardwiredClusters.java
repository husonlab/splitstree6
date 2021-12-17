/*
 *  HardwiredClusters.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.rootednetworks;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.data.parts.ASplit;

import java.util.*;

/**
 * utilities for hardwired clusters
 * Daniel Huson, 12.2021
 */
public class HardwiredClusters {
	/**
	 * compute matrix
	 */
	public static int[] computeCircularOrdering(PhyloTree[] trees, Map<String, Integer> taxon2ID) {
		if (taxon2ID.size() > 2) {
			double[][] distMat = null;

			var taxaTrees = new BitSet[2];
			if (trees.length == 2) {
				taxaTrees[0] = BitSetUtils.asBitSet(trees[0].getTaxa());
				taxaTrees[1] = BitSetUtils.asBitSet(trees[1].getTaxa());
			}

			var taxaNotInTrees = new BitSet[2];
			var newTrees = new PhyloTree[2];

			if (trees.length == 2 && (taxaTrees[0].size() != taxon2ID.size() || taxaTrees[1].size() != taxon2ID.size())) {
				for (var s = 0; s < 2; s++) {
					newTrees[s] = (PhyloTree) trees[s].clone();
					taxaNotInTrees[java.lang.Math.abs(s - 1)] = new BitSet();

					for (var t : BitSetUtils.members(taxaTrees[s])) {
						boolean contains = (taxaTrees[java.lang.Math.abs(s - 1)]).get(t);
						if (!contains) {
							taxaNotInTrees[java.lang.Math.abs(s - 1)].set(t);
						}
					}
				}

				// we restrict the trees to the common taxa

				for (var s = 0; s < 2; s++) {
					for (var t : BitSetUtils.members(taxaNotInTrees[java.lang.Math.abs(s - 1)])) {
						Node toDelete = null;
						for (Node node : newTrees[s].nodes()) {
							if (node.getOutDegree() == 0 && IteratorUtils.asList(newTrees[s].getTaxa(node)).contains(t)) {
								toDelete = node;
								break;
							}
						}
						if (toDelete != null)
							newTrees[s].deleteNode(toDelete);
						//System.err.println("taxon " + taxon + " " + newTrees[s].toBracketString());
					}
					var weird = true;  //todo : problem with delete!
					while (weird) {
						weird = false;
						for (Node node : newTrees[s].nodes()) {
							if (node.getOutDegree() == 0 && newTrees[s].getLabel(node) == null) {
								newTrees[s].deleteNode(node);
								weird = true;
							}
						}
					}
				}

				// we extract the clusters from the modified trees

				var clustersAll = collectAllHardwiredClusters(newTrees[0]);
				clustersAll.addAll(collectAllHardwiredClusters(newTrees[1]));

				var splits = getSplitSystem(clustersAll, taxon2ID);
				distMat = setMatForDiffSys(distMat, taxon2ID.size(), splits, false);
			} else {
				for (var tree : trees) {
					//System.err.println("tree ");
					var clusters = collectAllHardwiredClusters(tree);
					var splits = getSplitSystem(clusters, taxon2ID);
					distMat = setMatForDiffSys(distMat, taxon2ID.size(), splits, false);
				}
			}

			// get the order using NN

			var ntax = taxon2ID.size();
			var ordering = NeighborNetCycle.computeNeighborNetCycle(ntax, distMat);
			if (trees.length == 2) {
				if (taxaNotInTrees[0] != null && taxaNotInTrees[1] != null) {
					var takeAway = taxaNotInTrees[0].size() + taxaNotInTrees[1].size();
					var newOrdering = new int[ordering.length - takeAway];
					var index = 0;
					for (var t : ordering) {
						if (!taxaNotInTrees[0].get(t) && !taxaNotInTrees[1].get(t)) {
							newOrdering[index] = t;
							index++;
						}
					}
					return newOrdering;
				} else
					return ordering;
			} else
				return ordering;
		} else {
			var ordering = new int[taxon2ID.size() + 1];
			for (var i = 1; i <= taxon2ID.size(); i++)
				ordering[i] = i;
			return ordering;
		}
	}

	/**
	 * collects all clusters contained in the tree.
	 */
	public static Set<Set<String>> collectAllHardwiredClusters(PhyloTree tree) {
		var clusters = new HashSet<Set<String>>();
		collectAllHardwiredClustersRec(tree, tree.getRoot(), clusters);
		return clusters;
	}

	private static Set<String> collectAllHardwiredClustersRec(PhyloTree tree, Node v, Set<Set<String>> clusters) {
		//reached a leave
		if (v.getOutDegree() == 0) {
			var set = new HashSet<String>();
			set.add(tree.getLabel(v));
			clusters.add(set);
			return set;
		}
		//recursion
		else {
			TreeSet<String> set = new TreeSet<String>();
			for (Edge f : v.outEdges()) {
				var w = f.getTarget();
				set.addAll(collectAllHardwiredClustersRec(tree, w, clusters));
			}
			clusters.add(set);
			return set;
		}
	}

	/**
	 * get the split system for a tanglegram, use taxon IDs of the Map from all trees
	 *
	 * @return split system for this tanglegram
	 */
	private static Collection<ASplit> getSplitSystem(Set<Set<String>> clusters, Map<String, Integer> taxon2ID) {
		final var splits = new ArrayList<ASplit>();
		final BitSet activeTaxa = new BitSet();
		for (String s : taxon2ID.keySet()) {
			activeTaxa.set(taxon2ID.get(s));
		}
		for (Set<String> currCluster : clusters) {
			//System.err.println("currCluster " + currCluster);

			final BitSet sideA = new BitSet();

			for (String aCurrCluster : currCluster) {
				if (taxon2ID.get(aCurrCluster) != null)
					sideA.set(taxon2ID.get(aCurrCluster));
			}

			// todo: this is surely a bug:
			final BitSet sideB = (BitSet) activeTaxa.clone();
			sideB.andNot(sideA);
			if (sideA.cardinality() > 0 && sideB.cardinality() > 0) {
				final var split = new ASplit(sideA, sideB);

				//System.err.println("split " + split);

				if (!splits.contains(split)) {
					splits.add(split);
				}
			}
		}
		return splits;
	}

	/**
	 * initialize new matrix in first call, after that only add distances when called with new split system
	 *
	 * @return new distance matrix
	 */
	private static double[][] setMatForDiffSys(double[][] D, int ntax, Collection<ASplit> splits, boolean firstTree) {
		if (D == null) {
			var max_num_nodes = 3 * ntax - 5;
			D = new double[max_num_nodes][max_num_nodes];
			for (var i = 1; i <= ntax; i++)
				for (var j = i + 1; j <= ntax; j++) {
					var weight = 0.0;
					for (var split : splits) {
						if (split.separates(i, j))
							weight++;
					}
					if (firstTree) {
						D[i][j] = D[j][i] = (1000 * weight);
					} else {
						D[i][j] = D[j][i] = weight;
					}
				}
		} else {
			for (var i = 1; i <= ntax; i++)
				for (var j = i + 1; j <= ntax; j++) {
					var weight = 0.0;
					for (var split : splits) {
						if (split.separates(i, j))
							weight++;
					}
					D[i][j] = D[j][i] = D[i][j] + weight;
				}
		}
		return D;
	}
}
