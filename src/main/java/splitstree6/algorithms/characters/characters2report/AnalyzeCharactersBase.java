/*
 *  AnalyzeCharactersBase.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2report;

import javafx.application.Platform;
import jloda.fx.util.AService;
import javafx.beans.value.ChangeListener;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.ReportBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

import java.util.Collection;
import java.util.List;

/**
 * perform an analysis on Characters that returns a text to be displayed
 * Daniel Huson, 2.2023
 */
abstract public class AnalyzeCharactersBase extends Characters2Report {

	private final ChangeListener<Boolean> validListener;

	@Override
	public List<String> listOptions() {
		return List.of();
	}

	public AnalyzeCharactersBase() {
		super();

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
	 * @param taxaBlock       current taxa
	 * @param charactersBlock current Characters
	 * @param selectedTaxa    selected taxa, if required
	 * @return text to present
	 */
	abstract String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, Collection<Taxon> selectedTaxa) throws CanceledException;

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
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, ReportBlock reportBlock) throws CanceledException {
		reportBlock.setInputBlockName(CharactersBlock.BLOCK_NAME);

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
		var text = runAnalysis(progress, taxaBlock, charactersBlock, selectedTaxa);

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
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return taxa.getNtax() > 0 && datablock.getNchar() > 0;
	}
}
