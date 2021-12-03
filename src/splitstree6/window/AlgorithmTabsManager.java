/*
 *  AlgorithmTabsManager.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.window;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import jloda.fx.control.SplittableTabPane;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaEditor;
import splitstree6.tabs.algorithms.AlgorithmTab;
import splitstree6.tabs.algorithms.taxaedit.TaxaEditTab;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.Workflow;

public class AlgorithmTabsManager {
	private final MainWindow mainWindow;
	private final SplittableTabPane tabPane;
	private final ObservableMap<WorkflowNode, AlgorithmTab> nodeTabMap = FXCollections.observableHashMap();

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
							if (algorithmNode.getAlgorithm().getClass().equals(TaxaEditor.class))
								tab = new TaxaEditTab(mainWindow, algorithmNode);
							else
								tab = new AlgorithmTab(mainWindow, algorithmNode);
							tab.setOnCloseRequest(t -> showTab(algorithmNode, false));
							node.validProperty().addListener((v, o, n) -> {
							});
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
					tabPane.getTabs().add(tab);
				}
				tabPane.getSelectionModel().select(tab);
			} else
				tabPane.getTabs().remove(tab);
		}
	}
}
