/*
 * RerootOrReorderTrees.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeIntArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloGraph;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.AlgorithmNode;

import java.util.*;

/**
 * reportFairProportions trees by midpoint or outgroup, and/or ladderize
 * Daniel Huson, 11.2021
 */
public class RerootOrReorderTrees extends Trees2Trees implements IFilter {
	public enum RootBy {Off, MidPoint, OutGroup}

	public enum RearrangeBy {Off, RotateChildren, RotateSubTrees, ReverseChildren, ReverseSubTrees}

	public enum Reorder {Off, ByTaxa, Lexicographically, ReverseOrder, LadderizedUp, LadderizedDown, LadderizedRandom}

	private final ObjectProperty<RootBy> optionRootBy = new SimpleObjectProperty<>(this, "optionRootBy", RootBy.Off);

	private final ObjectProperty<RearrangeBy> optionRearrangeBy = new SimpleObjectProperty<>(this, "optionRearrangeBy", RearrangeBy.Off);

	private final ObjectProperty<Reorder> optionReorder = new SimpleObjectProperty<>(this, "optionReorder", Reorder.Off);

	private final ObjectProperty<String[]> optionOutGroupTaxa = new SimpleObjectProperty<>(this, "optionOutGroupTaxa", new String[0]);


	private final InvalidationListener selectionInvalidationListener;

	public List<String> listOptions() {
		return List.of(optionRootBy.getName(), optionRearrangeBy.getName(), optionReorder.getName(), optionOutGroupTaxa.getName());
	}

	public RerootOrReorderTrees() {
		this.selectionInvalidationListener = observable -> {
			if (getNode() != null) {
				setOptionOutGroupTaxa(getNode().getOwner().getMainWindow().getTaxonSelectionModel().getSelectedItems().stream().map(Taxon::getName).toArray(String[]::new));
			}
		};
	}

	@Override
	public void setNode(AlgorithmNode node) {
		super.setNode(node);
		if (node != null && node.getOwner() != null && node.getOwner().getMainWindow() != null) {
			node.getOwner().getMainWindow().getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionInvalidationListener));
			Platform.runLater(() -> selectionInvalidationListener.invalidated(null));
		}
	}

	@Override
	public boolean isActive() {
		return getOptionRootBy() != RootBy.Off;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) {
		var trees = outputData.getTrees();
		trees.clear();

		switch (getOptionRootBy()) {
			case Off -> {
				optionOutGroupTaxa.set(new String[0]);
				trees.addAll(inputData.getTrees());
			}
			case MidPoint -> {
				// todo: parallelize
				optionOutGroupTaxa.set(new String[0]);
				for (PhyloTree orig : inputData.getTrees()) {
					final var tree = new PhyloTree();
					tree.copy(orig);
					if (tree.getRoot() == null) {
						tree.setRoot(tree.getFirstNode());
						tree.redirectEdgesAwayFromRoot();
					}
					RerootingUtils.rerootByMidpoint(tree);
					trees.add(tree);
					outputData.setRooted(true);
				}
			}
			case OutGroup -> {
				// todo: parallelize

				selectionInvalidationListener.invalidated(null);

				final var outGroupTaxonSet = new BitSet();
				for (var taxon : getOptionOutGroupTaxa()) {
					int index = taxaBlock.indexOf(taxon);
					if (index >= 0)
						outGroupTaxonSet.set(index);
				}

				for (var originalTree : inputData.getTrees()) {
					if (originalTree.getNumberOfNodes() > 0) {
						final PhyloTree tree = new PhyloTree(originalTree);
						if (tree.getRoot() == null) {
							tree.setRoot(tree.getFirstNode());
							tree.redirectEdgesAwayFromRoot();
						}
						if (outGroupTaxonSet.cardinality() > 0)
							RerootingUtils.rerootByOutgroup(tree, outGroupTaxonSet);
						trees.add(tree);
					}
				}
				outputData.setRooted(true);
			}
		}

		if (getOptionRearrangeBy() != RearrangeBy.Off) {

			if (inputData.isReticulated()) {
				NotificationManager.showWarning("Rearrange: can't change rooted networks");
			}

			selectionInvalidationListener.invalidated(null);

			final var outGroupTaxonSet = new BitSet();
			for (var taxon : getOptionOutGroupTaxa()) {
				int index = taxaBlock.indexOf(taxon);
				if (index >= 0)
					outGroupTaxonSet.set(index);
			}
			if (outGroupTaxonSet.cardinality() > 0) {
				var inputTrees = new ArrayList<>(outputData.getTrees());
				outputData.clear();

				for (var tree : inputTrees) {
					Node v = findNodeAboveAll(tree, outGroupTaxonSet);
					if (v != null) {
						switch (getOptionRearrangeBy()) {
							case ReverseChildren -> {
								v.rearrangeAdjacentEdges(CollectionUtils.reverse(IteratorUtils.asList(v.outEdges())));
							}
							case ReverseSubTrees -> {
								tree.postorderTraversal(v, w -> w.rearrangeAdjacentEdges(CollectionUtils.reverse(IteratorUtils.asList(w.outEdges()))));
							}
							case RotateChildren -> {
								var list = IteratorUtils.asList(v.outEdges());
								list.add(list.remove(0));
								v.rearrangeAdjacentEdges(list);
							}
							case RotateSubTrees -> {
								tree.postorderTraversal(v, w -> {
									var list = IteratorUtils.asList(w.outEdges());
									if (list.size() > 1) {
										list.add(list.remove(0));
										w.rearrangeAdjacentEdges(list);
									}
								});
							}
						}
					}
					outputData.getTrees().add(tree);
				}
			}
		}

		if (getOptionReorder() != Reorder.Off) {
			if (inputData.isReticulated()) {
				NotificationManager.showWarning("Reorder: can't change rooted networks");
			}

			if (getOptionReorder() == Reorder.LadderizedUp || getOptionReorder() == Reorder.LadderizedDown) {
				try {
					ExecuteInParallel.apply(trees, tree -> {
						try (var node2height = tree.newNodeIntArray()) {
							LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
								if (v.isLeaf()) {
									node2height.put(v, 1);
								} else {
									node2height.put(v, v.childrenStream().mapToInt(node2height::get).max().orElse(0) + 1);
									v.rearrangeAdjacentEdges(orderEdges(v, node2height, getOptionReorder() == Reorder.LadderizedUp));
								}
							});
						}
					}, ProgramExecutorService.getNumberOfCoresToUse());
				} catch (Exception ignored) {
				}
			} else if (getOptionReorder() == Reorder.LadderizedRandom) {
				try {
					var random = new Random(666);
					ExecuteInParallel.apply(trees, tree -> {
						LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
							v.rearrangeAdjacentEdges(new ArrayList<>(IteratorUtils.asList(IteratorUtils.randomize(v.outEdges(), random))));
						});
					}, ProgramExecutorService.getNumberOfCoresToUse());
				} catch (Exception ignored) {
				}
			} else if (getOptionReorder() == Reorder.ByTaxa || getOptionReorder() == Reorder.Lexicographically) {
				try {
					ExecuteInParallel.apply(trees, tree -> {
						try (NodeArray<String> nodeLabelMap = tree.newNodeArray()) {
							LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
								nodeLabelMap.put(v, determineBestLabel(nodeLabelMap, v, getOptionReorder(), taxaBlock));
								var list = new ArrayList<Pair<String, Edge>>();
								for (var e : v.outEdges()) {
									list.add(new Pair<>(nodeLabelMap.get(e.getTarget()), e));
								}
								list.sort(Comparator.comparing(Pair::getFirst));
								var newOrder = new ArrayList<Edge>(list.size());
								for (var pair : list) {
									newOrder.add(pair.getSecond());
								}
								v.rearrangeAdjacentEdges(newOrder);
							});
						}
					}, ProgramExecutorService.getNumberOfCoresToUse());
				} catch (Exception ignored) {
				}
			} else if (getOptionReorder() == Reorder.ReverseOrder) {
				try {
					ExecuteInParallel.apply(trees, tree -> {
						LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
							var list = new ArrayList<Edge>(v.getOutDegree());
							for (var e = v.getLastOutEdge(); e != null; e = v.getPrevOutEdge(e)) {
								list.add(e);
							}
							v.rearrangeAdjacentEdges(list);
						});
					}, ProgramExecutorService.getNumberOfCoresToUse());
				} catch (Exception ignored) {
				}
			}
		}
	}

	private Node findNodeAboveAll(PhyloTree tree, BitSet outGroupTaxonSet) {
		var above = new Single<Node>();
		try (NodeArray<BitSet> nodeBelowMap = tree.newNodeArray()) {
			tree.postorderTraversal(v -> {
				if (above.get() == null) {
					var below = BitSetUtils.asBitSet(tree.getTaxa(v));
					for (var w : v.children()) {
						below.or(nodeBelowMap.get(w));
					}
					nodeBelowMap.put(v, below);
					if (above.get() == null && BitSetUtils.contains(below, outGroupTaxonSet)) {
						above.set(v);
					}
				}
			});
		}
		// move above any reticulations:
		if (above.get() != null) {
			var a = above.get();
			while (a != null) {
				if (a.getInDegree() > 1) {
					above.set(a.getParent());
				}
				a = a.getParent();
			}
		}
		return above.get();
	}

	private String determineBestLabel(NodeArray<String> nodeLabelMap, Node v, Reorder optionReorder, TaxaBlock taxaBlock) {
		if (v.isLeaf()) {
			if (optionReorder == Reorder.ByTaxa) {
				return "%08d".formatted(((PhyloGraph) v.getOwner()).getTaxon(v));
			} else
				return taxaBlock.getLabel(((PhyloGraph) v.getOwner()).getTaxon(v));
		} else {
			var set = new TreeSet<String>();
			for (var w : v.children()) {
				set.add(nodeLabelMap.get(w));
			}
			return set.first();
		}
	}

	public RootBy getOptionRootBy() {
		return optionRootBy.get();
	}

	public ObjectProperty<RootBy> optionRootByProperty() {
		return optionRootBy;
	}

	public void setOptionRootBy(RootBy optionRootBy) {
		this.optionRootBy.set(optionRootBy);
	}

	public String[] getOptionOutGroupTaxa() {
		return optionOutGroupTaxa.get();
	}

	public ObjectProperty<String[]> optionOutGroupTaxaProperty() {
		return optionOutGroupTaxa;
	}

	public void setOptionOutGroupTaxa(String[] optionOutGroupTaxa) {
		this.optionOutGroupTaxa.set(optionOutGroupTaxa);
	}

	public Reorder getOptionReorder() {
		return optionReorder.get();
	}

	public ObjectProperty<Reorder> optionReorderProperty() {
		return optionReorder;
	}

	public void setOptionReorder(Reorder optionReorder) {
		this.optionReorder.set(optionReorder);
	}

	public RearrangeBy getOptionRearrangeBy() {
		return optionRearrangeBy.get();
	}

	public ObjectProperty<RearrangeBy> optionRearrangeByProperty() {
		return optionRearrangeBy;
	}

	public void setOptionRearrangeBy(RearrangeBy optionRearrangeBy) {
		this.optionRearrangeBy.set(optionRearrangeBy);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return true;
	}

	/**
	 * order edges to ladderize
	 */
	private List<Edge> orderEdges(Node v, NodeIntArray node2height, boolean left) {
		var sorted = new ArrayList<Pair<Integer, Edge>>();

		for (var f : v.outEdges()) {
			var w = f.getTarget();
			sorted.add(new Pair<>((left ? -1 : 1) * node2height.get(w), f));
		}
		sorted.sort(Comparator.comparing(Pair::getFirst));

		var result = new LinkedList<Edge>();
		for (Pair<Integer, Edge> pair : sorted) {
			result.add(pair.getSecond());
		}
		return result;
	}
}
