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

package splitstree6.algorithms.network.network2view;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.NotificationManager;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.NetworkNexusOutput;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.view.network.NetworkView;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * network viewer selection
 * Daniel Huson, 4.2022
 */
public class ShowNetwork extends Network2View {
	public enum ViewType {Network, Text}

	private final ObjectProperty<ViewType> optionView = new SimpleObjectProperty<>(this, "optionView", ViewType.Network);
	private final ChangeListener<Boolean> validListener;

	@Override
	public List<String> listOptions() {
		return List.of(optionView.getName());
	}

	public ShowNetwork() {
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
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, NetworkBlock inputData, ViewBlock viewBlock) {
		viewBlock.setInputBlockName(SplitsBlock.BLOCK_NAME);
		Platform.runLater(() -> viewBlock.getViewTab().setGraphic(ResourceManagerFX.getIconAsImageView("SplitsNetworkViewer16.gif", 16)));

		// if a view already is set in the tab, simply update its data, otherwise set it up and put it into the tab:

		switch (getOptionView()) {
			case Network -> {
				if (!(viewBlock.getView() instanceof NetworkView)) {
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						var view = new NetworkView(mainWindow, ViewType.Network.name(), viewBlock.getViewTab());
						viewBlock.setView(view);
					});
				}
				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof NetworkView view) {
						view.getUndoManager().clear();
						view.setNetworkBlock(null); // this is necessary to trigger update
						view.setNetworkBlock(inputData);
					}
				});
			}
			case Text -> {
				if (!(viewBlock.getView() instanceof DisplayTextView)) {
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						viewBlock.setView(new DisplayTextView(mainWindow, "Network Text", false));
					});
				}
				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof DisplayTextView view) {
						view.getUndoManager().clear();
						try (var w = new StringWriter()) {
							(new NetworkNexusOutput()).write(w, taxaBlock, inputData);
							view.replaceText(w.toString());
						} catch (IOException ex) {
							NotificationManager.showError("Internal error: " + ex);
						}
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
