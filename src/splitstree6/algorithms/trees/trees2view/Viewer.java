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
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Tab;
import jloda.fx.window.NotificationManager;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.TreesNexusOutput;
import splitstree6.view.ConsoleView;
import splitstree6.view.trees.multitree.MultiTreesView;
import splitstree6.window.MainWindow;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * trees viewer selection
 * Daniel Huson, 11.2021
 */
public class Viewer extends Trees2View {
	public enum ViewType {SingleTree, MultiTree, DensiTree, Tanglegram, Console}

	private final ObjectProperty<ViewType> optionView = new SimpleObjectProperty<>(this, "optionView", ViewType.Console);

	private final ObjectProperty<DisplayTab> displayTab = new SimpleObjectProperty<>();

	private final InvalidationListener invalidationListener;

	@Override
	public List<String> listOptions() {
		return List.of(optionView.getName());
	}

	public Viewer() {
		super();
		invalidationListener = e -> {
			if (getNode().getOwner().nodes().contains(getNode())) {
				getNode().getOwner().getMainWindow().addTabToMainTabPane(getDisplayTab());
			} else {
				getNode().getOwner().getMainWindow().removeTabFromMainTabPane(getDisplayTab());

			}
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, ViewBlock viewBlock) throws IOException {
		viewBlock.setInputBlockName(TreesBlock.BLOCK_NAME);

		viewBlock.setName(inputData.getNode().getTitle());

		switch (getOptionView()) {
			case MultiTree -> {
				var mainWindow = getNode().getOwner().getMainWindow();
				var view = new MultiTreesView(mainWindow, getNode().titleProperty());
				view.getTrees().setAll(inputData.getTrees());
				viewBlock.setView(view);
			}
			case SingleTree, DensiTree, Tanglegram -> {
				NotificationManager.showWarning("Not implemented: " + getOptionView());
				viewBlock.setView(null);
			}
			case Console -> {
				var mainWindow = getNode().getOwner().getMainWindow();
				var view = new ConsoleView(mainWindow, inputData.getName() + " text");
				try (var w = new StringWriter()) {
					(new TreesNexusOutput()).write(w, taxaBlock, inputData);
					view.setText(w.toString());
				}
				viewBlock.setView(view);
			}
		}

		Platform.runLater(() -> {
			if (getDisplayTab() == null) {
				var mainWindow = getNode().getOwner().getMainWindow();
				displayTab.set(new DisplayTab(mainWindow));
				mainWindow.getWorkflow().nodes().addListener(new WeakInvalidationListener(invalidationListener));
				mainWindow.addTabToMainTabPane(getDisplayTab());
			}
			getDisplayTab().setText(viewBlock.getName());
			if (viewBlock.getView() != null) {
				getDisplayTab().setContent(viewBlock.getView().getRoot());
				if (viewBlock.getView() instanceof MultiTreesView multiTreesView) {
					multiTreesView.tabPaneProperty().bind(getDisplayTab().tabPaneProperty());
				}
			} else
				getDisplayTab().setContent(null);
		});

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

	public DisplayTab getDisplayTab() {
		return displayTab.get();
	}

	public ReadOnlyObjectProperty<DisplayTab> displayTabProperty() {
		return displayTab;
	}

	public class DisplayTab extends Tab {
		private final MainWindow mainWindow;

		public DisplayTab(MainWindow mainWindow) {
			this.mainWindow = mainWindow;
		}

		public MainWindow getMainWindow() {
			return mainWindow;
		}
	}
}
