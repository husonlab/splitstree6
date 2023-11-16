/*
 * TextTabsManager.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.window;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import jloda.fx.control.SplittableTabPane;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.displaydatablock.DisplayData;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

public class TextTabsManager {
	private final MainWindow mainWindow;
	private final SplittableTabPane tabPane;
	private final Workflow workflow;

	private final ObservableMap<WorkflowNode, ViewTab> nodeTabMap = FXCollections.observableHashMap();

	public TextTabsManager(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		this.workflow = mainWindow.getWorkflow();

		tabPane = mainWindow.getController().getMainTabPane();

		workflow.nodes().addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasRemoved()) {
					for (var node : e.getRemoved()) {
						var tab = nodeTabMap.get(node);
						if (tab != null) {
							tabPane.getTabs().remove(tab);
						}
						nodeTabMap.remove(node);
					}
				}
			}
		});
	}

	public void showDataNodeTab(DataNode node, boolean show) {
		final ViewTab tab;
		if (nodeTabMap.containsKey(node))
			tab = nodeTabMap.get(node);
		else {
			tab = new ViewTab(mainWindow, node, true);
			tab.setGraphic(MaterialIcons.graphic("dataset"));
			var view = new DisplayData(mainWindow, node, node.getTitle(), false);
			tab.setView(view);
			tab.setText(view.getName());
			tab.setOnCloseRequest(t -> nodeTabMap.remove(node));
			nodeTabMap.put(node, tab);
		}
		if (show) {
			if (!tabPane.getTabs().contains(tab)) {
				tabPane.getTabs().add(tab);
			}
			tabPane.getSelectionModel().select(tab);
			Platform.runLater(() -> ((DisplayData) tab.getView()).getDisplayDataController().getApplyButton().getOnAction().handle(null));
		} else
			tabPane.getTabs().remove(tab);

		node.validProperty().addListener((v, o, n) -> {
			if (!n)
				((DisplayData) tab.getView()).replaceText("");
			else {
				((DisplayData) tab.getView()).getDisplayDataController().getApplyButton().getOnAction().handle(null);
			}
		});
	}

	public void updateDataNodeTabIfShowing(DataNode node) {
		if (nodeTabMap.containsKey(node)) {
			var tab = nodeTabMap.get(node);
			Platform.runLater(() -> ((DisplayData) tab.getView()).getDisplayDataController().getApplyButton().getOnAction().handle(null));
		}
	}
}
