/*
 *  RootedOutline.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.more;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.graph.EdgeSet;
import jloda.graph.Node;
import jloda.graph.algorithms.IsDAG;
import jloda.graph.io.GraphGML;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloSplitsGraph;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.trees.trees2trees.Trees2Trees;
import splitstree6.algorithms.utils.GreedyCircular;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.writers.splits.NexusWriter;
import splitstree6.layout.splits.algorithms.PhylogeneticOutline;
import splitstree6.splits.ASplit;
import splitstree6.splits.TreesUtils;
import splitstree6.view.trees.tanglegram.optimize.LSATree;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Greedy rooted outline.
 *
 * @todo This is broken
 * Daniel Huson, 9.2023
 */
public class RootedOutline extends Trees2Trees {
	private final BooleanProperty optionRemoveDuplicates = new SimpleBooleanProperty(this, "optionRemoveDuplicates", false);


	@Override
	public List<String> listOptions() {
		return List.of(optionRemoveDuplicates.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputTreesBlock) throws IOException {
		outputTreesBlock.setPartial(treesBlock.isPartial());
		outputTreesBlock.setReticulated(treesBlock.isReticulated());

		var muVectors = new TreeSet<int[]>(Arrays::compare);

		progress.setMaximum(treesBlock.size());
		progress.setProgress(0);
		for (var tree : treesBlock.getTrees()) {
			if (tree.isReticulated()) {
				var rootedOutline = computeRootedOutline0(taxaBlock, tree);
				if (isOptionRemoveDuplicates()) {
					var muVector = computeMuVector(rootedOutline);
					if (!muVectors.contains(muVector))
						muVectors.add(muVector);
					else
						continue;
				}
				outputTreesBlock.getTrees().add(rootedOutline);
			} else {
				outputTreesBlock.getTrees().add(new PhyloTree(tree));
			}
			progress.incrementProgress();
		}
		progress.reportTaskCompleted();
	}

	private static PhyloTree computeRootedOutline0(TaxaBlock taxaBlock, PhyloTree tree) throws IOException {
		var rootedTaxaBlock = new TaxaBlock(taxaBlock);
		var rootedSplits = new SplitsBlock();
		// need to insert at front because the outline algorithm creates the network from the first taxon:
		insertRootTaxonAtFrontAndAddAllClusters(taxaBlock, tree, rootedTaxaBlock, rootedSplits);
		SplitsBlockUtilities.addAllTrivial(rootedTaxaBlock.getNtax(), rootedSplits);

		var rootTaxonId = 1;

		var circularSplits = new SplitsBlock();
		circularSplits.getSplits().addAll(GreedyCircular.apply(new ProgressSilent(), rootedTaxaBlock.getTaxaSet(), rootedSplits.getSplits(),
				rootedSplits.hasConfidenceValues() ? ASplit::getConfidence : ASplit::getWeight).getFirst());

		if (false) {
			try (var w = new StringWriter()) {
				var writer = new NexusWriter();
				writer.optionPrependTaxaProperty().set(true);
				writer.write(w, rootedTaxaBlock, circularSplits);
				System.err.println(w.toString());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		var graph = new PhyloTree();
		graph.setName(tree.getName() + "-outline");
		PhylogeneticOutline.apply(new ProgressSilent(), true, rootedTaxaBlock, circularSplits, graph, null, null, null, 0, 0);

		System.err.println("After outline");
		for (var v : graph.nodes()) {
			if (v.getInDegree() == 0) {
				System.err.println("Found a root: " + v);
				System.err.println("indegree: " + v.getInDegree());
				System.err.println("outdegree: " + v.getOutDegree());
				System.err.println("Has child: " + v.getFirstOutEdge().getTarget());
			}
		}

		var topNode = graph.nodeStream().filter(v -> graph.getNumberOfTaxa(v) > 0 && graph.getTaxon(v) == rootTaxonId).findAny();
		if (topNode.isPresent()) {
			var root = topNode.get().getFirstOutEdge().getTarget();
			graph.deleteNode(topNode.get());
			graph.setRoot(root);

			System.err.println("new root: " + root);
			System.err.println("indegree: " + root.getInDegree());
			System.err.println("outdegree: " + root.getOutDegree());

			removeRootTaxonFromFront(graph);

			System.err.println("After root");
			for (var v : graph.nodes()) {
				if (v.getInDegree() == 0) {
					System.err.println("Found a root: " + v);
				}
			}

			for (var v : graph.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1 && graph.getNumberOfTaxa(v) == 0).toList()) {
				graph.delDivertex(v);
			}
			for (var e : graph.edges()) {
				graph.setReticulate(e, e.getTarget().getInDegree() > 1);
			}

			LSATree.computeNodeLSAChildrenMap(tree);

			System.err.println("After postprocessing");
			for (var v : graph.nodes()) {
				if (v.getInDegree() == 0) {
					System.err.println("Found a root: " + v);
				}
			}
			return graph;
		} else
			throw new RuntimeException("Failed to compute rooted outline");
	}

	private static void insertRootTaxonAtFrontAndAddAllClusters(TaxaBlock taxaBlock, PhyloTree tree, TaxaBlock rootedTaxaBlock, SplitsBlock rootedSplits) {
		rootedTaxaBlock.addTaxonByName("ROOT__");
		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			rootedTaxaBlock.addTaxonByName(taxaBlock.get(t).getName());
		}

		try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
			for (var entry : nodeClusterMap.entrySet()) {
				var v = entry.getKey();
				var cluster = entry.getValue();
				if (v.getInDegree() == 1) {
					var e = v.getFirstInEdge();
					var set = new BitSet();
					for (var t : BitSetUtils.members(cluster)) {
						set.set(t + 1);
					}
					var split = new ASplit(set, rootedTaxaBlock.getNtax(), tree.getWeight(e), tree.getConfidence(e));
					if (!rootedSplits.getSplits().contains(split))
						rootedSplits.getSplits().add(split);
				}
			}
		}
	}

	private static void removeRootTaxonFromFront(PhyloTree graph) {
		for (var v : graph.nodes()) {
			if (graph.getNumberOfTaxa(v) > 0) {
				var taxa = new BitSet();
				for (var t : graph.getTaxa(v)) {
					if (t > 1)
						taxa.set(t - 1);
				}
				graph.clearTaxa(v);
				if (taxa.cardinality() > 0) {
					for (var t : BitSetUtils.members(taxa)) {
						graph.addTaxon(v, t);
					}
				}
			}
		}
	}


	private static PhyloTree computeRootedOutline(TaxaBlock taxaBlock, PhyloTree tree) throws IOException {
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
		circularSplits.getSplits().addAll(GreedyCircular.apply(new ProgressSilent(), rootedTaxaBlock.getTaxaSet(), rootedSplits.getSplits(),
				rootedSplits.hasConfidenceValues() ? ASplit::getConfidence : ASplit::getWeight).getFirst());

		if (false) {
			try (var w = new StringWriter()) {
				var writer = new NexusWriter();
				writer.optionPrependTaxaProperty().set(true);
				writer.write(w, rootedTaxaBlock, circularSplits);
				System.err.println(w.toString());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		var graph = new PhyloTree();
		graph.setName(tree.getName() + "-outline");
		PhylogeneticOutline.apply(new ProgressSilent(), true, rootedTaxaBlock, circularSplits, graph, null, null, null, 0, 0);
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
		System.err.println("After outline");
		for (var v : graph.nodes()) {
			if (v.getInDegree() == 0) {
				System.err.println("Found a root: " + v);
			}
		}

		var topNode = graph.nodeStream().filter(v -> graph.getNumberOfTaxa(v) > 0 && graph.getTaxon(v) == rootId).findAny();
		if (topNode.isPresent()) {
			try (var visited = graph.newEdgeSet()) {
				redirectEdgesAwayFromRootRec(graph, topNode.get(), new BitSet(), visited);
				if (visited.size() < graph.getNumberOfEdges())
					System.err.println("Didn't visit all edges: " + visited.size() + " of " + graph.getNumberOfEdges());
			}
			System.err.println("After redirecting");
			for (var v : graph.nodes()) {
				if (v.getInDegree() == 0) {
					System.err.println("Found a root: " + v);
				}
			}

			var root = topNode.get().getFirstOutEdge().getTarget();
			graph.deleteNode(topNode.get());
			graph.setRoot(root);

			System.err.println("After setting root");
			for (var v : graph.nodes()) {
				if (v.getInDegree() == 0) {
					System.err.println("Found a root: " + v);
				}
			}

			if (!IsDAG.apply(graph))
				throw new IOException("Not DAG");

			for (var v : graph.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1 && graph.getNumberOfTaxa(v) == 0).toList()) {
				graph.delDivertex(v);
			}
			for (var e : graph.edges()) {
				graph.setReticulate(e, e.getTarget().getInDegree() > 1);
			}

			if (true) {
				System.err.println((new NewickIO()).toBracketString(graph, false) + ";");
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
			return graph;
		} else
			throw new RuntimeException("Failed to compute rooted outline");
	}

	private static void redirectEdgesAwayFromRootRec(PhyloSplitsGraph graph, Node v, BitSet splits, EdgeSet visited) {
		for (var e : IteratorUtils.asList(v.adjacentEdges())) {
			if (!visited.contains(e)) {
				visited.add(e);
				var s = graph.getSplit(e);
				var w = e.getOpposite(v);
				if (!splits.get(s)) { // crossing over to other side of split
					splits.set(s, true);
					redirectEdgesAwayFromRootRec(graph, w, splits, visited);
					splits.set(s, false);
					if (e.getSource() != v) {
						e.reverse();
					}
				} else { // coming back to first side of split
					splits.set(s, false);
					redirectEdgesAwayFromRootRec(graph, w, splits, visited);
					splits.set(s, true);
					if (e.getTarget() != v) {
						e.reverse();
					}
				}
			}
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isPartial();
	}

	public static int[] computeMuVector(PhyloTree tree) {
		var mu = new int[BitSetUtils.max(BitSetUtils.asBitSet(tree.getTaxa())) + 1];
		computeMuVectorRec(tree, tree.getRoot(), mu);
		return mu;
	}

	private static void computeMuVectorRec(PhyloTree tree, Node v, int[] mu) {
		for (var t : tree.getTaxa(v)) {
			mu[t]++;
		}
		for (var w : v.children()) {
			computeMuVectorRec(tree, w, mu);
		}
	}

	public boolean isOptionRemoveDuplicates() {
		return optionRemoveDuplicates.get();
	}

	public BooleanProperty optionRemoveDuplicatesProperty() {
		return optionRemoveDuplicates;
	}

	public void setOptionRemoveDuplicates(boolean optionRemoveDuplicates) {
		this.optionRemoveDuplicates.set(optionRemoveDuplicates);
	}
}
