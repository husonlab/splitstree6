/*
 * ShowSplits.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2report;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.ReportBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

import java.util.Collection;
import java.util.List;

/**
 * perform an analysis on splits that returns a text to be displayed
 * Daniel Huson, 2.2023
 */
abstract public class Distances2ReportBase extends Algorithm<DistancesBlock, ReportBlock> {

	private final ChangeListener<Boolean> validListener;

	@Override
	public List<String> listOptions() {
		return List.of();
	}

	public Distances2ReportBase() {
		super(DistancesBlock.class, ReportBlock.class);

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
	 * @param block        current splits
	 * @param selectedTaxa selected taxa, if required
	 * @return text to present
	 */
	abstract String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock block, Collection<Taxon> selectedTaxa);

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
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock block, ReportBlock reportBlock) {
		reportBlock.setInputBlockName(SplitsBlock.BLOCK_NAME);

		Platform.runLater(() -> {
			reportBlock.getViewTab().setText(getName());
			reportBlock.getView().getUndoManager().clear();
		});

		var mainWindow = getNode().getOwner().getMainWindow();
		var text = runAnalysis(progress, taxaBlock, block, mainWindow.getTaxonSelectionModel().getSelectedItems());

		Platform.runLater(() -> {
			reportBlock.getViewTab().setText(getName());
			reportBlock.setText(text);
			reportBlock.getView().replaceText(text);
		});
		reportBlock.updateShortDescription();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, DistancesBlock datablock) {
		return taxa.getNtax() > 0;
	}
}
