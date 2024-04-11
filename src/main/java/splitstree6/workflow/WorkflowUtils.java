/*
 *  WorkflowUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.workflow;

import javafx.application.Platform;
import splitstree6.algorithms.characters.characters2characters.CharactersTaxaFilter;
import splitstree6.data.CharactersBlock;
import splitstree6.data.ViewBlock;
import splitstree6.tabs.IDisplayTab;
import splitstree6.view.alignment.AlignmentView;
import splitstree6.view.alignment.AlignmentViewPresenter;
import splitstree6.view.worldmap.WorldMapPresenter;
import splitstree6.view.worldmap.WorldMapView;

public class WorkflowUtils {
	/**
	 * if the input data is a characters block, use this to setup the alignment viewer
	 */
	public static void ensureAlignmentView(Workflow workflow) {
		var mainWindow = workflow.getMainWindow();
		if (mainWindow != null && workflow.getInputDataFilterNode() != null && workflow.getInputDataFilterNode().getAlgorithm() instanceof CharactersTaxaFilter) {
			var tab = mainWindow.getController().getMainTabPane().getTabs().stream()
					.filter(t -> t instanceof IDisplayTab).map(t -> (IDisplayTab) t)
					.filter(t -> t.getPresenter() instanceof AlignmentViewPresenter).findAny();
			if (tab.isEmpty()) {
				var tabs = mainWindow.getController().getMainTabPane().getTabs();
				var previous = !tabs.isEmpty() ? tabs.get(tabs.size() - 1) : null;
				var viewBlock = new ViewBlock();
				viewBlock.setInputBlockName(CharactersBlock.BLOCK_NAME);
				var dataNode = workflow.newDataNode(viewBlock);
				workflow.getInputDataFilterNode().getChildren().add(dataNode);
				Platform.runLater(() -> {
					var isDirty = mainWindow.isDirty();
					var alignmentView = new AlignmentView(mainWindow, "Alignment", viewBlock.getViewTab());
					viewBlock.setView(alignmentView);
					viewBlock.setNode(dataNode);
					if (previous != null) {
						mainWindow.getController().getMainTabPane().getTabs().remove(previous);
						mainWindow.getController().getMainTabPane().getTabs().add(previous);
						Platform.runLater(() -> mainWindow.getController().getMainTabPane().getSelectionModel().select(previous));
					}
					mainWindow.setDirty(isDirty);
				});
			}
		}
	}

	/**
	 * if the input contains traits data with longitude and latitude data, then show the world map
	 *
	 * @param workflow
	 */
	public static void ensureWorldMapView(Workflow workflow, boolean allowOpenWithoutLatLongdata) {
		var mainWindow = workflow.getMainWindow();
		var workingTaxa = workflow.getWorkingTaxaBlock();
		if (workingTaxa != null) {
			var traitsBlock = workingTaxa.getTraitsBlock();
			if (traitsBlock != null && traitsBlock.isSetLatitudeLongitude() || allowOpenWithoutLatLongdata) {
				var tab = mainWindow.getController().getMainTabPane().getTabs().stream()
						.filter(t -> t instanceof IDisplayTab).map(t -> (IDisplayTab) t)
						.filter(t -> t.getPresenter() instanceof WorldMapPresenter).findAny();
				if (tab.isEmpty()) {
					var tabs = mainWindow.getController().getMainTabPane().getTabs();
					var previous = !tabs.isEmpty() ? tabs.get(tabs.size() - 1) : null;

					var viewBlock = new ViewBlock();
					viewBlock.setInputBlockName(CharactersBlock.BLOCK_NAME);
					var dataNode = mainWindow.getWorkflow().newDataNode(viewBlock);
					mainWindow.getWorkflow().getInputDataFilterNode().getChildren().add(dataNode);
					Platform.runLater(() -> {
						var isDirty = mainWindow.isDirty();
						var view = new WorldMapView(mainWindow, "World map", viewBlock.getViewTab());
						viewBlock.setView(view);
						viewBlock.setNode(dataNode);
						if (previous != null) {
							mainWindow.getController().getMainTabPane().getTabs().remove(previous);
							mainWindow.getController().getMainTabPane().getTabs().add(previous);
							Platform.runLater(() -> mainWindow.getController().getMainTabPane().getSelectionModel().select(previous));
						}
						mainWindow.setDirty(isDirty);
					});
				}
			}
		}
	}

}
