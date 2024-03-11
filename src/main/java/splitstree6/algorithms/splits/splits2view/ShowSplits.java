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

package splitstree6.algorithms.splits.splits2view;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import jloda.fx.icons.MaterialIcons;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.view.splits.viewer.SplitsView;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

import java.util.List;

/**
 * splits viewer selection
 * Daniel Huson, 11.2021
 */
public class ShowSplits extends Splits2View {
	public enum ViewType {SplitsNetwork}

	private final ObjectProperty<ViewType> optionView = new SimpleObjectProperty<>(this, "optionView", ViewType.SplitsNetwork);
	private final ChangeListener<Boolean> validListener;

	@Override
	public List<String> listOptions() {
		return List.of(optionView.getName());
	}

	@Override
	public String getShortDescription() {
		return "Provides interactive visualizations of split networks.";
	}

	public ShowSplits() {
		super();

		validListener = (v, o, n) -> {
			if (getNode() != null && getNode().getPreferredChild() != null && ((DataNode) getNode().getPreferredChild()).getDataBlock() instanceof ViewBlock viewBlock) {
				if (viewBlock.getView() != null)
					viewBlock.getView().getRoot().setDisable(!n);
			}
		};
	}

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
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock inputData, ViewBlock viewBlock) {
		viewBlock.setInputBlockName(SplitsBlock.BLOCK_NAME);
		Platform.runLater(() -> viewBlock.getViewTab().setGraphic(MaterialIcons.graphic("wysiwyg")));

		// if a view already is set in the tab, simply update its data, otherwise set it up and put it into the tab:

		switch (getOptionView()) {
			case SplitsNetwork -> {
				if (!(viewBlock.getView() instanceof SplitsView)) {
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						var view = new SplitsView(mainWindow, ViewType.SplitsNetwork.name(), viewBlock.getViewTab());
						viewBlock.setView(view);
					});
				}
				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof SplitsView view) {
						view.getUndoManager().clear();
						view.setSplitsBlock(null); // this is neccessary to trigger update
						view.setSplitsBlock(inputData);
					}
				});
			}
		}
		viewBlock.updateShortDescription();
	}

	public ViewType getOptionView() {
		return optionView.get();
	}

	public ObjectProperty<ViewType> optionViewProperty() {
		return optionView;
	}

	public void setOptionView(ViewType optionView) {
		this.optionView.set(optionView);
	}
}
