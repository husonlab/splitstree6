/*
 * ShowSplits.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.NotificationManager;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.SplitsNexusOutput;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.view.splits.viewer.SplitsView;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * splits viewer selection
 * Daniel Huson, 11.2021
 */
public class ShowSplits extends Splits2View {
	public enum ViewType {SplitsNetwork, SplitsText}

	private final ObjectProperty<ViewType> optionView = new SimpleObjectProperty<>(this, "optionView", ViewType.SplitsNetwork);

	@Override
	public List<String> listOptions() {
		return List.of(optionView.getName());
	}

	public ShowSplits() {
		super();
	}

	@Override
    public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock inputData, ViewBlock viewBlock) {
		viewBlock.setInputBlockName(SplitsBlock.BLOCK_NAME);
		viewBlock.getViewTab().setGraphic(ResourceManagerFX.getIconAsImageView("SplitsNetworkViewer16.gif", 16));

        // if a view already is set in the tab, simply update its data, otherwise set it up and put it into the tab:

        switch (getOptionView()) {
			case SplitsNetwork -> {
				if (!(viewBlock.getView() instanceof SplitsView)) {
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						var view = new SplitsView(mainWindow, "Splits Network", viewBlock.getViewTab());
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
			case SplitsText -> {
				if (!(viewBlock.getView() instanceof DisplayTextView)) {
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						viewBlock.setName("Splits Text");
						var view = new DisplayTextView(mainWindow, inputData.getName() + " Text", false);
						viewBlock.setView(view);
					});
				}
				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof DisplayTextView view) {
						view.getUndoManager().clear();
						try (var w = new StringWriter()) {
							(new SplitsNexusOutput()).write(w, taxaBlock, inputData);
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
