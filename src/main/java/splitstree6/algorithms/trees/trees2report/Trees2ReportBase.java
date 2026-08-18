/*
 *  Trees2ReportBase.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2report;

import javafx.application.Platform;
import jloda.fx.util.AService;
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

		// The view is optional. Guard it so that a report can be computed headless - from a
		// tool, a test or a language binding - where there is no toolkit, no view tab and no
		// workflow node. Same shape of fix as D-1 and D-8: JavaFX where a guard belongs.
		var showInView = AService.isToolkitRunning() && reportBlock.getViewTab() != null && reportBlock.getView() != null;

		if (showInView)
			Platform.runLater(() -> {
				reportBlock.getViewTab().setText(getName());
				reportBlock.getView().getUndoManager().clear();
			});

		// The selected taxa are a GUI concept; headless there is no selection.
		var mainWindow = (getNode() != null && getNode().getOwner() != null ? getNode().getOwner().getMainWindow() : null);
		Collection<Taxon> selectedTaxa = (mainWindow != null ? mainWindow.getTaxonSelectionModel().getSelectedItems() : List.of());
		if (getOptionApplyTo() == ApplyTo.OneTree && getOptionWhichTree() >= 1 && getOptionWhichTree() <= treesBlock.getNTrees()) {
			var tree = treesBlock.getTree(getOptionWhichTree());
			treesBlock = new TreesBlock();
			treesBlock.getTrees().add(tree);
		}

		var text = runAnalysis(progress, taxaBlock, treesBlock, selectedTaxa);

		// setText mutates an ObservableList, so keep it on the FX thread whenever there is a
		// view - that path is then exactly as it was. Headless there is no thread to wait
		// for and no view to update, and the caller still needs the result, so set it here.
		if (showInView)
			Platform.runLater(() -> {
				reportBlock.getViewTab().setText(getName());
				reportBlock.setText(text);
				reportBlock.getView().replaceText(text);
			});
		else
			reportBlock.setText(text);
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
