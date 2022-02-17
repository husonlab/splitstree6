/*
 * ShowTrees.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2view;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.NotificationManager;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.TreesNexusOutput;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.view.trees.tanglegram.TanglegramView;
import splitstree6.view.trees.treepages.TreePagesView;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * trees viewer selection
 * Daniel Huson, 11.2021
 */
public class ShowTrees extends Trees2View {
	public enum ViewType {SingleTree, TreePages, DensiTree, Tanglegram, Text}

	private final ObjectProperty<ViewType> optionView = new SimpleObjectProperty<>(this, "optionView", ViewType.TreePages);

	@Override
	public List<String> listOptions() {
		return List.of(optionView.getName());
	}

	public ShowTrees() {
		super();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, ViewBlock viewBlock) throws IOException {
		viewBlock.setInputBlockName(TreesBlock.BLOCK_NAME);
		Platform.runLater(() -> viewBlock.getViewTab().setGraphic(ResourceManagerFX.getIconAsImageView("TreeViewer16.gif", 16)));

		// if a view already is set in the tab, simply update its data, otherwise set it up and put it into the tab:

		switch (getOptionView()) {
			case TreePages -> {
					if (viewBlock.getView() != null)
						viewBlock.getView().clear();
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						var view = new TreePagesView(mainWindow, "Tree Pages", viewBlock.getViewTab());
						viewBlock.setView(view);
					});

				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof TreePagesView view) {
						view.getUndoManager().clear();
						view.getTrees().setAll(inputData.getTrees());
						view.setReticulated(inputData.isReticulated());
					}
				});
			}
			case Tanglegram -> {
					if (viewBlock.getView() != null)
						viewBlock.getView().clear();
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						var view = new TanglegramView(mainWindow, "Tanglegram", viewBlock.getViewTab());
						viewBlock.setView(view);
					});

				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof TanglegramView view) {
						view.getUndoManager().clear();
						view.getTrees().setAll(inputData.getTrees());
						view.setReticulated(inputData.isReticulated());
					}
				});
			}
			case SingleTree, DensiTree -> throw new IOException("Not implemented: " + getOptionView());
			case Text -> {
					if (viewBlock.getView() != null)
						viewBlock.getView().clear();
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						var view = new DisplayTextView(mainWindow, "Trees Text", false);
						viewBlock.setView(view);
					});

				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof DisplayTextView view) {
						view.getUndoManager().clear();
						try (var w = new StringWriter()) {
							(new TreesNexusOutput()).write(w, taxaBlock, inputData);
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
