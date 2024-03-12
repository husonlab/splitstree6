/*
 * ShowSplits.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2report;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.IViewChoice;
import splitstree6.data.ReportBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

import java.util.Collection;
import java.util.List;

/**
 * perform an analysis on trees and then provides a report
 * Daniel Huson, 2.2023
 */
abstract public class Trees2ReportBase extends Algorithm<TreesBlock, ReportBlock> implements IViewChoice {

	public enum ApplyTo {OneTree, AllTrees}

	private final ObjectProperty<ApplyTo> optionApplyTo = new SimpleObjectProperty<>(this, "optionApplyTo", ApplyTo.OneTree);
	private final IntegerProperty optionWhichTree = new SimpleIntegerProperty(this, "optionWhichTree", 1);

	private final ChangeListener<Boolean> validListener;

	@Override
	public List<String> listOptions() {
		return List.of(optionApplyTo.getName(), optionWhichTree.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		if (optionName.equals(optionApplyTo.getName()))
			return "determine whether to apply to one or all trees";
		else if (optionName.equals(optionWhichTree.getName()))
			return "the index of the tree that the method will be applied to";

		else return super.getToolTip(optionName);

	}

	public Trees2ReportBase() {
		super(TreesBlock.class, ReportBlock.class);

		validListener = (v, o, n) -> {
			if (getNode() != null && getNode().getPreferredChild() != null && ((DataNode) getNode().getPreferredChild()).getDataBlock() instanceof ReportBlock reportBlock) {
				if (reportBlock.getView() != null)
					reportBlock.getView().getRoot().setDisable(!n);
			}
		};
	}

	/**
	 * set the analysis to perform
	 *
	 * @param taxaBlock    current taxa
	 * @param treesBlock   current trese
	 * @param selectedTaxa selected taxa, if required
	 * @return text to present
	 */
	abstract String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException;

	@Override
	public void setNode(AlgorithmNode node) {
		if (getNode() != null)
			getNode().validProperty().removeListener(validListener);
		super.setNode(node);
		if (getNode() != null) {
			getNode().validProperty().addListener(validListener);
		}
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, ReportBlock reportBlock) throws CanceledException {
		reportBlock.setInputBlockName(TreesBlock.BLOCK_NAME);

		Platform.runLater(() -> {
			reportBlock.getViewTab().setText(getName());
			reportBlock.getView().getUndoManager().clear();
		});

		var mainWindow = getNode().getOwner().getMainWindow();
		if (getOptionApplyTo() == ApplyTo.OneTree && getOptionWhichTree() >= 1 && getOptionWhichTree() <= treesBlock.getNTrees()) {
			var tree = treesBlock.getTree(getOptionWhichTree());
			treesBlock = new TreesBlock();
			treesBlock.getTrees().add(tree);
		}

		var text = runAnalysis(progress, taxaBlock, treesBlock, mainWindow.getTaxonSelectionModel().getSelectedItems());

		Platform.runLater(() -> {
			reportBlock.getViewTab().setText(getName());
			reportBlock.setText(text);
			reportBlock.getView().replaceText(text);
		});
		reportBlock.updateShortDescription();
	}


	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock treesBlock) {
		return taxa.getNtax() > 0 && treesBlock.getNTrees() > 0;
	}

	public ApplyTo getOptionApplyTo() {
		return optionApplyTo.get();
	}

	public ObjectProperty<ApplyTo> optionApplyToProperty() {
		return optionApplyTo;
	}

	public void setOptionApplyTo(ApplyTo optionApplyTo) {
		this.optionApplyTo.set(optionApplyTo);
	}

	public int getOptionWhichTree() {
		return optionWhichTree.get();
	}

	public IntegerProperty optionWhichTreeProperty() {
		return optionWhichTree;
	}

	public void setOptionWhichTree(int optionWhichTree) {
		this.optionWhichTree.set(optionWhichTree);
	}

	public ChangeListener<Boolean> getValidListener() {
		return validListener;
	}
}
