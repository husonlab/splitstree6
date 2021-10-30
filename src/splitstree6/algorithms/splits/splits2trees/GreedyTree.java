/*
 * GreedyTree.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.algorithms.splits.splits2trees;

import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.GreedyCompatible;
import splitstree6.algorithms.utils.PhyloGraphUtils;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;

import java.io.IOException;
import java.util.*;

/**
 * greedy tree
 * Daniel Huson, 12.2017
 */
public class GreedyTree extends Splits2Trees {
	@Override
	public String getCitation() {
		return "Huson et al 2012;D.H. Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splits, TreesBlock trees) throws IOException {

		progress.setTasks("Greedy Tree", "Extracting compatible splits...");
		final Map<BitSet, Double> cluster2Weight = new HashMap<>();
		for (ASplit split : splits.getSplits()) {
			cluster2Weight.put(split.getPartNotContaining(1), split.getWeight());
		}

		final BitSet[] clusters;
		{
			final ArrayList<ASplit> compatibleSplits = GreedyCompatible.apply(progress, splits.getSplits());
			clusters = new BitSet[compatibleSplits.size()];
			for (int i = 0; i < compatibleSplits.size(); i++) {
				clusters[i] = compatibleSplits.get(i).getPartNotContaining(1);
			}
		}
		Arrays.sort(clusters, (a, b) -> Integer.compare(b.cardinality(), a.cardinality()));

		final var allTaxa = taxaBlock.getTaxaSet();

		final var tree = new PhyloTree();
		tree.setRoot(tree.newNode());

		final var node2taxa = new NodeArray<BitSet>(tree);
		node2taxa.put(tree.getRoot(), allTaxa);

		// create tree:
		for (var cluster : clusters) {
			var v = tree.getRoot();
			while (BitSetUtils.contains(node2taxa.get(v), cluster)) {
				var isBelow = false;
				for (var e : v.outEdges()) {
					final var w = e.getTarget();
					if (BitSetUtils.contains(node2taxa.get(w), cluster)) {
						v = w;
						isBelow = true;
						break;
					}
				}
				if (!isBelow)
					break;
			}
			final var u = tree.newNode();
			final var f = tree.newEdge(v, u);
			tree.setWeight(f, cluster2Weight.get(cluster));
			node2taxa.put(u, cluster);
		}

		// add all labels:

		for (var t : BitSetUtils.members(allTaxa)) {
			var v = tree.getRoot();
			while (node2taxa.get(v).get(t)) {
				var isBelow = false;
				for (var e : v.outEdges()) {
					final var w = e.getTarget();
					if (node2taxa.get(w).get(t)) {
						v = w;
						isBelow = true;
						break;
					}
				}
				if (!isBelow)
					break;
			}
			tree.addTaxon(v, t);
		}
		PhyloGraphUtils.addLabels(taxaBlock, tree);

		// todo: ask about internal node labels
		RerootingUtils.rerootByMidpoint(false, tree);

		trees.setRooted(true);
		trees.setPartial(false);
		trees.getTrees().setAll(tree);
		progress.close();
	}

}
