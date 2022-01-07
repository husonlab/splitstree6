/*
 *  TextTabsManager.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import jloda.fx.control.SplittableTabPane;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.data.TaxaBlock;
import splitstree6.io.nexus.NexusExporter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.displaydatablock.DisplayData;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.io.IOException;
import java.io.StringWriter;

public class TextTabsManager {
	private final MainWindow mainWindow;
	private final SplittableTabPane tabPane;
	private final Workflow workflow;
	private final NexusExporter nexusExporter = new NexusExporter();

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

	private String createText(WorkflowNode node) {
		if (node instanceof DataNode dataNode) {
			var dataBlock = dataNode.getDataBlock();
			if (dataBlock instanceof TaxaBlock taxaBlock) {
				try (var w = new StringWriter()) {
					nexusExporter.export(w, taxaBlock);
					return w.toString();
				} catch (IOException ignored) {
				}
			} else {
				TaxaBlock taxaBlock;
				if (workflow.getInputDataNode() != null && workflow.getInputDataNode().getDataBlock() == dataBlock) {
					taxaBlock = workflow.getInputTaxonBlock();
				} else {
					taxaBlock = workflow.getWorkingTaxaBlock();
				}
				try (var w = new StringWriter()) {
					nexusExporter.setPrependTaxa(false);
					nexusExporter.export(w, taxaBlock, dataBlock);
					return w.toString();
				} catch (IOException ignored) {
				}
			}
		} else if (node instanceof AlgorithmNode algorithmNode) {
			var algorithm = algorithmNode.getAlgorithm();
			try (var w = new StringWriter()) {
				nexusExporter.export(w, algorithm);
				return w.toString();
			} catch (IOException ignored) {
			}

		}
		return node.getName();
	}

	public void showDataNodeTab(DataNode node, boolean show) {
		final ViewTab tab;
		if (nodeTabMap.containsKey(node))
			tab = nodeTabMap.get(node);
		else {
			tab = new ViewTab(mainWindow, node, true);
			var view = new DisplayData(mainWindow, node, node.getTitle() + " text", false);
			tab.setView(view);
			tab.setOnCloseRequest(t -> nodeTabMap.remove(node));
			nodeTabMap.put(node, tab);
		}
		if (show) {
			if (!tabPane.getTabs().contains(tab)) {
				tabPane.getTabs().add(tab);
			}
			tabPane.getSelectionModel().select(tab);
			Platform.runLater(() -> ((DisplayTextView) tab.getView()).replaceText(createText(node)));
		} else
			tabPane.getTabs().remove(tab);
	}
}
