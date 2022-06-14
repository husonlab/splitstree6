/*
 * RerootOrLadderizeTrees.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeIntArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.Pair;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.AlgorithmNode;

import java.util.*;

/**
 * report trees by midpoint or outgroup, and/or ladderize
 * Daniel Huson, 11.2021
 */
public class RerootOrLadderizeTrees extends Trees2Trees implements IFilter {
	public enum RootBy {Off, MidPoint, OutGroup}

	public enum Ladderize {Off, Up, Down, Random}

	private final ObjectProperty<RootBy> optionRootBy = new SimpleObjectProperty<>(this, "optionRootBy", RootBy.Off);

	private final ObjectProperty<String[]> optionOutGroupTaxa = new SimpleObjectProperty<>(this, "optionOutGroupTaxa", new String[0]);

	private final ObjectProperty<Ladderize> optionLadderize = new SimpleObjectProperty<>(this, "optionLadderize", Ladderize.Off);

	private final BooleanProperty optionHasBranchSupportValues = new SimpleBooleanProperty(this, "optionHasBranchSupportValues", false);

	private final InvalidationListener selectionInvalidationListener;

	public List<String> listOptions() {
		return List.of(optionRootBy.getName(), optionOutGroupTaxa.getName(), optionLadderize.getName(), optionHasBranchSupportValues.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionHasBranchSupportValues.getName()))
			return "If internal node labels are numbers then will attempt to relabel correctly during rerooting";
		else
			return null;
	}

	public RerootOrLadderizeTrees() {
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
				optionOutGroupTaxa.set(new String[0]);
				for (PhyloTree orig : inputData.getTrees()) {
					final var tree = new PhyloTree();
					tree.copy(orig);
					if (tree.getRoot() == null) {
						tree.setRoot(tree.getFirstNode());
						tree.redirectEdgesAwayFromRoot();
					}
					// todo: ask about internal node labels
					RerootingUtils.rerootByMidpoint(getOptionHasBranchSupportValues(), tree);
					trees.add(tree);
					outputData.setRooted(true);
				}
			}
			case OutGroup -> {
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
							RerootingUtils.rerootByOutGroup(getOptionHasBranchSupportValues(), tree, outGroupTaxonSet);
						trees.add(tree);
					}
				}
				outputData.setRooted(true);
			}
		}

		if (getOptionLadderize() == Ladderize.Up || getOptionLadderize() == Ladderize.Down) {
			for (var tree : trees) {
				var node2height = tree.newNodeIntArray();
				LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					if (v.isLeaf()) {
						node2height.put(v, 1);
					} else {
						node2height.put(v, v.childrenStream().mapToInt(node2height::get).max().orElse(0) + 1);
						v.rearrangeAdjacentEdges(orderEdges(v, node2height, getOptionLadderize() == Ladderize.Up));
					}
				});
			}
		} else if (getOptionLadderize() == Ladderize.Random) {
			for (var tree : trees) {
				LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					var newOrder = new LinkedList<Edge>();
					for (Iterator<Edge> it = IteratorUtils.randomize(v.adjacentEdges().iterator(), v.getId()); it.hasNext(); ) {
						newOrder.add(it.next());
					}
					v.rearrangeAdjacentEdges(newOrder);
				});
			}
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

	public Ladderize getOptionLadderize() {
		return optionLadderize.get();
	}

	public ObjectProperty<Ladderize> optionLadderizeProperty() {
		return optionLadderize;
	}

	public void setOptionLadderize(Ladderize optionLadderize) {
		this.optionLadderize.set(optionLadderize);
	}

	public boolean getOptionHasBranchSupportValues() {
		return optionHasBranchSupportValues.get();
	}

	public BooleanProperty optionHasBranchSupportValuesProperty() {
		return optionHasBranchSupportValues;
	}

	public void setOptionHasBranchSupportValues(boolean optionHasBranchSupportValues) {
		this.optionHasBranchSupportValues.set(optionHasBranchSupportValues);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isReticulated();
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
