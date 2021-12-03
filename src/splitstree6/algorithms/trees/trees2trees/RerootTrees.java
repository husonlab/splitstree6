/*
 *  RerootTrees.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.AlgorithmNode;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;

/**
 * report trees by midpoint or outgroup
 * Daniel Huson, 11.2021
 */
public class RerootTrees extends Trees2Trees implements IFilter {
	public enum RootBy {Off, MidPoint, OutGroup}

	private final ObjectProperty<RootBy> optionRootBy = new SimpleObjectProperty<>(this, "optionRootBy", RootBy.MidPoint);

	private final ObjectProperty<String[]> optionOutGroupTaxa = new SimpleObjectProperty<>(new String[0]);

	private final InvalidationListener selectionInvalidationListener;

	public List<String> listOptions() {
		return List.of(optionRootBy.getName(), "optionOutGroupTaxa");
	}

	public RerootTrees() {
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
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) throws IOException {
		switch (getOptionRootBy()) {
			case Off -> {
				optionOutGroupTaxa.set(new String[0]);

				outputData.getTrees().setAll(inputData.getTrees());
			}
			case MidPoint -> {
				optionOutGroupTaxa.set(new String[0]);

				outputData.getTrees().clear();

				for (PhyloTree orig : inputData.getTrees()) {
					final PhyloTree tree = new PhyloTree();
					tree.copy(orig);
					if (tree.getRoot() == null) {
						tree.setRoot(tree.getFirstNode());
						tree.redirectEdgesAwayFromRoot();
					}
					// todo: ask about internal node labels
					RerootingUtils.rerootByMidpoint(false, tree);
					outputData.getTrees().add(tree);
					outputData.setRooted(true);
				}
			}
			case OutGroup -> {
				selectionInvalidationListener.invalidated(null);

				final BitSet outGroupTaxonSet = new BitSet();
				for (var taxon : getOptionOutGroupTaxa()) {
					int index = taxaBlock.indexOf(taxon);
					if (index >= 0)
						outGroupTaxonSet.set(index);
				}

				outputData.getTrees().clear();

				for (PhyloTree orig : inputData.getTrees()) {
					if (orig.getNumberOfNodes() > 0) {
						final PhyloTree tree = new PhyloTree();
						tree.copy(orig);
						if (tree.getRoot() == null) {
							tree.setRoot(tree.getFirstNode());
							tree.redirectEdgesAwayFromRoot();
						}
						if (outGroupTaxonSet.cardinality() > 0)
							// todo: ask about internal node labels
							RerootingUtils.rerootByOutGroup(false, tree, outGroupTaxonSet);
						outputData.getTrees().add(tree);
					}
				}
				outputData.setRooted(true);
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
}
