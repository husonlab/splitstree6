/*
 * AlgorithmTabsManager.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.control.Tab;
import jloda.fx.control.SplittableTabPane;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.algorithms.trees.trees2trees.TreesFilter;
import splitstree6.tabs.algorithms.AlgorithmTab;
import splitstree6.tabs.algorithms.taxafilter.TaxaFilterTab;
import splitstree6.tabs.algorithms.treefilter.TreeFilterTab;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.Workflow;

public class AlgorithmTabsManager {
	private final MainWindow mainWindow;
	private final SplittableTabPane tabPane;
	private final ObservableMap<WorkflowNode, Tab> nodeTabMap = FXCollections.observableHashMap();

	public AlgorithmTabsManager(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		Workflow workflow = mainWindow.getWorkflow();

		tabPane = mainWindow.getController().getAlgorithmTabPane();

		workflow.nodes().addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var node : e.getAddedSubList()) {
						if (node instanceof AlgorithmNode algorithmNode) {
							AlgorithmTab tab;
							if (algorithmNode.getAlgorithm().getClass().equals(TaxaFilter.class))
								tab = new TaxaFilterTab(mainWindow, algorithmNode);
							else if (algorithmNode.getAlgorithm().getClass().equals(TreesFilter.class))
								tab = new TreeFilterTab(mainWindow, algorithmNode);
							else
								tab = new AlgorithmTab(mainWindow, algorithmNode);
							tab.setOnCloseRequest(t -> showTab(algorithmNode, false));
							nodeTabMap.put(node, tab);
						}
					}
				} else if (e.wasRemoved()) {
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

	public void showTab(AlgorithmNode node, boolean show) {
		var tab = nodeTabMap.get(node);
		if (tab != null) {
			if (show) {
				mainWindow.getPresenter().getSplitPanePresenter().ensureAlgorithmsTabPaneIsOpen();
				if (!tabPane.getTabs().contains(tab)) {
					var newTab = new Tab();
					newTab.setText(tab.getText());
					newTab.setGraphic(tab.getGraphic());
					var content = tab.getContent();
					tab.setContent(null);
					newTab.setContent(content);
					nodeTabMap.put(node, newTab);
					tabPane.getTabs().add(newTab);
				}
				if (true)
					tabPane.getSelectionModel().select(tab);
			} else
				tabPane.getTabs().remove(tab);
		}
	}
}
