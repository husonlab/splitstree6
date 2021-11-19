/*
 *  Viewer.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2view;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.fx.window.NotificationManager;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.TreesNexusOutput;
import splitstree6.options.OptionIO;
import splitstree6.view.ConsoleView;
import splitstree6.view.trees.next.Next;
import splitstree6.view.trees.treepages.TreePagesView;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * trees viewer selection
 * Daniel Huson, 11.2021
 */
public class Viewer extends Trees2View {
	public enum ViewType {SingleTree, TreePages, DensiTree, Tanglegram, Console}

	private final ObjectProperty<ViewType> optionView = new SimpleObjectProperty<>(this, "optionView", ViewType.TreePages);

	@Override
	public List<String> listOptions() {
		return List.of(optionView.getName());
	}

	public Viewer() {
		super();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, ViewBlock viewBlock) throws IOException {
		viewBlock.setInputBlockName(TreesBlock.BLOCK_NAME);

		// if a view already is set in the tab, simply update its data, otherwise set it up and put it into the tab:

		switch (getOptionView()) {
			case TreePages -> {
				if (viewBlock.getView() instanceof TreePagesView view) {
					Platform.runLater(() -> view.setTrees(inputData.getTrees()));
				} else {
					Platform.runLater(() -> {
						var mainWindow = getNode().getOwner().getMainWindow();
						var view = new TreePagesView(mainWindow, "TreePages", viewBlock.getViewTab());
						try {
							OptionIO.parseOptions(viewBlock.initializationLinesProperty(), view);
						} catch (IOException e) {
							NotificationManager.showError("Error parsing options");
						}
						viewBlock.setView(view);
						view.getTrees().setAll(inputData.getTrees());
					});
				}
			}
			case Tanglegram -> {
				Platform.runLater(() -> {
					var mainWindow = getNode().getOwner().getMainWindow();
					var view = new Next(mainWindow);
					viewBlock.setView(view);
				});
			}
			case SingleTree, DensiTree -> throw new IOException("Not implemented: " + getOptionView());
			case Console -> {
				if (viewBlock.getView() instanceof ConsoleView consoleView) {
					try {
						OptionIO.parseOptions(viewBlock.initializationLinesProperty(), viewBlock.getView());
					} catch (IOException e) {
						NotificationManager.showError("Error parsing options");
					}
					Platform.runLater(() -> {
						try (var w = new StringWriter()) {
							(new TreesNexusOutput()).write(w, taxaBlock, inputData);
							consoleView.setText(w.toString());
						} catch (IOException ex) {
							NotificationManager.showError("Internal error: " + ex);
						}
					});
					return;
				} else {
					var mainWindow = getNode().getOwner().getMainWindow();
					var view = new ConsoleView(mainWindow, inputData.getName() + " text");
					try (var w = new StringWriter()) {
						(new TreesNexusOutput()).write(w, taxaBlock, inputData);
						view.setText(w.toString());
					}
					viewBlock.setView(view);
					viewBlock.getViewTab().setView(view);
				}
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
