/*
 * ConsensusTree.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.SimpleObjectProperty;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.Pair;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.*;

/**
 * computes a rooted consensus tree from a list of trees
 * Daniel Huson, 2.2018
 */
public class RootedConsensusTree extends Trees2Trees {
	public enum Consensus {Majority, Strict, Greedy}

	private final SimpleObjectProperty<Consensus> optionConsensus = new SimpleObjectProperty<>(this, "optionConsensus", Consensus.Majority);

	@Override
	public List<String> listOptions() {
		return List.of(optionConsensus.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionConsensus.getName()))
			return "Consensus method to use";
		else
			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) throws IOException {
		child.setRooted(true);
		child.setReticulated(false);
		child.setPartial(false);
		if (parent.getNTrees() <= 1)
			child.getTrees().addAll(parent.getTrees());
		else {
			var tree = computeRootedConsensusTree(parent.getTrees(), getOptionConsensus());
			tree.setName(getOptionConsensus().name());
			child.getTrees().add(tree);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
	}

	public static PhyloTree computeRootedConsensusTree(Collection<PhyloTree> trees, Consensus consensus) {

		var clusterCountWeightMap = new HashMap<BitSet, Pair<Integer, Double>>();
		for (var tree : trees) {
			try (NodeArray<BitSet> nodeClusterMap = tree.newNodeArray()) {
				tree.postorderTraversal(tree.getRoot(), v -> {
					if (v != tree.getRoot()) {
						var cluster = new BitSet();
						if (v.isLeaf()) {
							cluster.set(tree.getTaxon(v));
						} else {
							for (var w : v.children()) {
								cluster.or(nodeClusterMap.get(w));
							}
						}
						nodeClusterMap.put(v, cluster);
						var pair = clusterCountWeightMap.getOrDefault(cluster, new Pair<>(0, 0.0));
						pair.set(pair.getFirst() + 1, pair.getSecond() + tree.getWeight(v.getFirstInEdge()));
						clusterCountWeightMap.put(cluster, pair);
					}
				});
			}
		}
		var clusterWeightList = new ArrayList<Pair<BitSet, Double>>();
		if (consensus == Consensus.Greedy) {
			var list = new ArrayList<Pair<Integer, BitSet>>();
			for (var entry : clusterCountWeightMap.entrySet()) {
				list.add(new Pair<>(entry.getValue().getFirst(), entry.getKey()));
			}
			list.sort((a, b) -> {
				if (a.getFirst() > b.getFirst())
					return -1;
				else if (a.getFirst() < b.getFirst())
					return 1;
				else
					return BitSetUtils.compare(a.getSecond(), b.getSecond());
			});
			var selected = new HashSet<BitSet>();
			for (var pair : list) {
				var cluster = pair.getSecond();
				if (isCompatibleWithAll(cluster, selected)) {
					selected.add(cluster);
					clusterWeightList.add(new Pair<>(cluster, clusterCountWeightMap.get(cluster).getSecond() / trees.size()));
				}
			}
		} else {
			var threshold = (consensus == Consensus.Strict ? trees.size() - 1 : 0.5 * trees.size());

			for (var cluster : clusterCountWeightMap.keySet()) {
				if (clusterCountWeightMap.get(cluster).getFirst() > threshold)
					clusterWeightList.add(new Pair<>(cluster, clusterCountWeightMap.get(cluster).getSecond() / trees.size()));
			}
		}
		clusterWeightList.sort((a, b) -> -Integer.compare(a.getFirst().cardinality(), b.getFirst().cardinality()));

		var taxa = BitSetUtils.asBitSet(trees.iterator().next().getTaxa());
		var tree = new PhyloTree();
		try (NodeArray<BitSet> nodeClusterMap = tree.newNodeArray()) {
			var root = tree.newNode();
			tree.setRoot(root);
			nodeClusterMap.put(root, taxa);

			for (var pair : clusterWeightList) {
				var v = root;
				{
					var changed = true;
					while (changed) {
						changed = false;
						for (var w : v.children()) {
							if (BitSetUtils.contains(nodeClusterMap.get(w), pair.getFirst())) {
								v = w;
								changed = true;
								break;
							}
						}
					}
					var w = tree.newNode();
					tree.setWeight(tree.newEdge(v, w), pair.getSecond());
					nodeClusterMap.put(w, pair.getFirst());
				}
			}
			for (var v : tree.leaves()) {
				var cluster = nodeClusterMap.get(v);
				if (cluster.cardinality() == 1) {
					tree.addTaxon(v, cluster.nextSetBit(1));
				} else if (cluster.cardinality() > 1) {
					for (var t : BitSetUtils.members(nodeClusterMap.get(v))) {
						var w = tree.newNode();
						tree.newEdge(v, w);
						tree.addTaxon(w, t);
					}
				}
			}
		}
		return tree;
	}

	public static boolean isCompatibleWithAll(BitSet a, Collection<BitSet> clusters) {
		if (a != null) {
			for (var b : clusters) {
				if (!(!a.intersects(b) || BitSetUtils.contains(a, b) || BitSetUtils.contains(b, a)))
					return false;
			}
		}
		return true;
	}

	public Consensus getOptionConsensus() {
		return optionConsensus.get();
	}

	public SimpleObjectProperty<Consensus> optionConsensusProperty() {
		return optionConsensus;
	}

	public void setOptionConsensus(Consensus optionConsensus) {
		this.optionConsensus.set(optionConsensus);
	}

}
