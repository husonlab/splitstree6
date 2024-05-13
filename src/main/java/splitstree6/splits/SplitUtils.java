/*
 *  SplitUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.splits;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.algorithms.PQTree;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.Pair;
import splitstree6.utils.TreesUtils;

import java.util.*;

/**
 * some utilities for splits
 */
public class SplitUtils {

	/**
	 * is given set consecutive in the ordering?
	 *
	 * @param set      set of integers
	 * @param ordering ordering of integers
	 * @return true, if set occurs consecutively in ordering
	 */
	public static boolean isCompatibleWithOrdering(BitSet set, ArrayList<Integer> ordering) {
		var inside = false;
		var count = 0;
		for (var t : ordering) {
			if (set.get(t)) {
				if (!inside)
					inside = true;
				if (++count == set.cardinality())
					return true;
			} else {
				if (inside)
					return false;
			}
		}
		return false;
	}

	/**
	 * normalize cycle so that it is lexicographically smallest
	 *
	 * @return normalized cycle
	 */
	public static int[] normalizeCycle(int[] cycle) {
		var posOf1 = -1;
		for (var i = 1; i < cycle.length; i++) {
			if (cycle[i] == 1) {
				posOf1 = i;
				break;
			}
		}
		final var posPrev = (posOf1 == 1 ? cycle.length - 1 : posOf1 - 1);
		final var posNext = (posOf1 == cycle.length - 1 ? 1 : posOf1 + 1);
		if (cycle[posPrev] > cycle[posNext]) { // has correct orientation, ensure that taxon 1 is at first position
			if (posOf1 != 1) {
				var tmp = new int[cycle.length];
				var i = posOf1;
				for (var j = 1; j < tmp.length; j++) {
					tmp[j] = cycle[i];
					if (++i == cycle.length)
						i = 1;
				}
				return tmp;
			} else
				return cycle;
		} else // change orientation, as well
		{
			var tmp = new int[cycle.length];
			var i = posOf1;
			for (var j = 1; j < tmp.length; j++) {
				tmp[j] = cycle[i];
				if (--i == 0)
					i = cycle.length - 1;
			}
			return tmp;
		}
	}

	/**
	 * gets  all taxa that are included in one specified side of one split and also one specified side of the other split.
	 *
	 * @param splitP the index of split "P", 1-based
	 * @param sideP  the "side" of the split P that should be considered
	 * @param splitQ the index of the other split "Q", 1-based
	 * @param sideQ  the "side" of the split Q that should be considered
	 */
	public static BitSet intersect2(ASplit splitP, boolean sideP, ASplit splitQ, boolean sideQ) {
		final BitSet result = new BitSet();
		result.or(sideP ? splitP.getA() : splitP.getB());
		result.and(sideQ ? splitQ.getA() : splitQ.getB());
		return result;
	}


	public static void rotateCycle(int[] cycle, int first) {
		final var tmp = new int[2 * cycle.length - 1];
		System.arraycopy(cycle, 0, tmp, 0, cycle.length);
		System.arraycopy(cycle, 1, tmp, cycle.length, cycle.length - 1);
		for (var i = 1; i < tmp.length; i++) {
			if (tmp[i] == first) {
				for (int j = 1; j < cycle.length; j++) {
					cycle[j] = tmp[i++];
				}
				return;
			}
		}
	}

	/**
	 * compute all the splits in a tree
	 *
	 * @return bit set of taxa found in tree
	 */
	public static BitSet computeSplits(BitSet taxaInTree, final PhyloTree tree, final Collection<ASplit> splits) {
		if (taxaInTree == null)
			taxaInTree = TreesUtils.getTaxa(tree);

		if (tree.getRoot() == null)
			throw new RuntimeException("Tree is empty or no root");
		else {
			var biPartitionSplitMap = new HashMap<BiPartition, ASplit>();
			for (var entry : TreesUtils.extractClusters(tree).entrySet()) {
				var v = entry.getKey();
				var cluster = entry.getValue();
				if (v != tree.getRoot()) {
					var complement = BitSetUtils.minus(taxaInTree, cluster);
					if (cluster.cardinality() > 0 && complement.cardinality() > 0) {
						var e = v.getFirstInEdge();
						var weight = tree.getWeight(e);
						var biPartition = new BiPartition(cluster, complement);
						var split = biPartitionSplitMap.computeIfAbsent(biPartition, k -> new ASplit(cluster, complement, 0));
						split.setWeight(split.getWeight() + weight); // this ensures that complementary clusters get mapped to same split
						if (tree.hasEdgeConfidences()) {
							split.setConfidence(tree.getConfidence(e));
						}
					}
				}
			}
			splits.clear();
			splits.addAll(biPartitionSplitMap.values());
		}
		return taxaInTree;
	}

	static public int[] computeCycle(int ntax, List<ASplit> splits) {
		var pqTree = new PQTree(BitSetUtils.asBitSet(BitSetUtils.range(1, ntax + 1)));
		var clusters = new ArrayList<Pair<Double, BitSet>>();
		for (var split : splits) {
			if (!split.isTrivial()) {
				clusters.add(new Pair<>(split.getWeight() * split.size(), split.getPartNotContaining(1)));
			}
		}
		clusters.stream().sorted(Comparator.comparingDouble(a -> -a.getFirst())).map(Pair::getSecond).forEach(pqTree::accept);
		var ordering = pqTree.extractAnOrdering();
		var array1based = new int[ordering.size() + 1];
		var index = 0;
		for (var value : ordering) {
			array1based[++index] = value;
		}
		return array1based;
	}

	public static NodeArray<ASplit> buildIncompatibilityGraph(List<ASplit> splits, Graph graph) {
		NodeArray<ASplit> nodeSplitMap = graph.newNodeArray();
		var split2node = new Node[splits.size()];

		for (var s = 0; s < splits.size(); s++) {
			var v = graph.newNode();
			split2node[s] = v;
			nodeSplitMap.put(v, splits.get(s));
		}
		for (var s = 0; s < splits.size(); s++) {
			for (var t = s + 1; t < splits.size(); t++)
				if (!BiPartition.areCompatible(splits.get(s), splits.get(t))) {
					graph.newEdge(split2node[s], split2node[t]);
				}
		}
		return nodeSplitMap;
	}
}
