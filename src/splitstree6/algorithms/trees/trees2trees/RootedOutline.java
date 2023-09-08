/*
 * TreeSelector.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.graph.Node;
import jloda.graph.io.GraphGML;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloSplitsGraph;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.splits.splits2splits.SplitsFilter;
import splitstree6.algorithms.utils.GreedyCircular;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.writers.splits.NexusWriter;
import splitstree6.io.writers.view.GMLWriter;
import splitstree6.layout.splits.algorithms.PhylogeneticOutline;
import splitstree6.splits.ASplit;
import splitstree6.splits.SplitUtils;
import splitstree6.splits.TreesUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.BitSet;
import java.util.List;
import java.util.function.Function;

/**
 * Greedy rooted outline. This
 * Daniel Huson, 9.2023
 */
public class RootedOutline extends Trees2Trees {

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputTreesBlock) throws IOException {
		outputTreesBlock.setPartial(treesBlock.isPartial());
		outputTreesBlock.setReticulated(treesBlock.isReticulated());
		for (var tree : treesBlock.getTrees()) {
			if (tree.isReticulated()) {
				var rootedTaxaBlock = new TaxaBlock(taxaBlock);
				rootedTaxaBlock.addTaxonByName("ROOT__");
				var rootId = rootedTaxaBlock.getNtax();
				var rootedSplits = new SplitsBlock();
				try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
					for (var cluster : nodeClusterMap.values()) {
						var split = new ASplit(cluster, rootedTaxaBlock.getNtax());
						if (!rootedSplits.getSplits().contains(split))
							rootedSplits.getSplits().add(split);
					}
				}
				SplitsBlockUtilities.addAllTrivial(rootedTaxaBlock.getNtax(), rootedSplits);

				var circularSplits = new SplitsBlock();
				circularSplits.getSplits().addAll(GreedyCircular.apply(progress, rootedTaxaBlock.getTaxaSet(), rootedSplits.getSplits(),
						rootedSplits.hasConfidenceValues() ? ASplit::getConfidence : ASplit::getWeight));

				if (false) {
					try (var w = new StringWriter()) {
						var writer = new NexusWriter();
						writer.optionPrependTaxaProperty().set(true);
						writer.write(w, rootedTaxaBlock, circularSplits);
						System.err.println(w.toString());
					}
				}

				var graph = new PhyloTree();
				graph.setName(tree.getName() + "-outline");
				PhylogeneticOutline.apply(progress, true, rootedTaxaBlock, circularSplits, graph, null, null, null, 0, 0);
				if (false) {
					try (var w = new StringWriter()) {
						GraphGML.writeGML(graph, "rooted", "rooted", true, 1, w,
								List.of("label", "taxa"),
								(label, v) -> {
									if (label.equals("label"))
										return graph.getLabel(v);
									else if (label.equals("taxa") && graph.getNumberOfTaxa(v) != 0)
										return StringUtils.toString(graph.getTaxa(v), ",");
									else return null;
								},
								List.of("split"),
								(label, e) -> (label.equals("split") ? "" + graph.getSplit(e) : null));
						System.err.println(w);
					}
				}
				var topNode = graph.nodeStream().filter(v -> graph.getNumberOfTaxa(v) > 0 && graph.getTaxon(v) == rootId).findAny();
				if (topNode.isPresent()) {
					var root = topNode.get().getFirstAdjacentEdge().getOpposite(topNode.get());
					graph.deleteNode(topNode.get());
					redirectEdgesAwayFromNode(graph, root, new BitSet());
					graph.setRoot(root);
					if (false) {
						System.err.println((new NewickIO()).toBracketString(graph, false));
					}

					for (var v : graph.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1 && graph.getNumberOfTaxa(v) == 0).toList()) {
						graph.delDivertex(v);
					}
					for (var e : graph.edges()) {
						graph.setReticulate(e, e.getTarget().getInDegree() > 1);
					}
					if (false) {
						try (var w = new StringWriter()) {
							GraphGML.writeGML(graph, "redirected", "redirected", true, 1, w, List.of("label"), (label, v) ->
											label.equals("label") ? graph.getLabel(v) : null, List.of("split"),
									(label, e) -> (label.equals("split") ? "" + graph.getSplit(e) : null));
							System.err.println(w);
						}
					}
					if (false) {
						System.err.println((new NewickIO()).toBracketString(graph, false));
					}
					outputTreesBlock.getTrees().add(graph);
				}
			} else {
				outputTreesBlock.getTrees().add(new PhyloTree(tree));
			}
		}
	}

	private static void redirectEdgesAwayFromNode(PhyloSplitsGraph graph, Node v, BitSet splits) {
		for (var e : v.adjacentEdges()) {
			var s = graph.getSplit(e);
			if (!splits.get(s)) {
				splits.set(s);
				redirectEdgesAwayFromNode(graph, e.getOpposite(v), splits);
				splits.set(s, false);
				if (e.getTarget() == v) {
					e.reverse();
				}
			}
		}

	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isPartial();
	}
}
