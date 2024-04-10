/*
 *  EnsureWorldMap.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.worldmap;

import javafx.application.Platform;
import javafx.scene.control.Tab;
import splitstree6.data.CharactersBlock;
import splitstree6.data.ViewBlock;
import splitstree6.tabs.IDisplayTab;
import splitstree6.window.MainWindow;

public class EnsureWorldMap {
	public static void apply(MainWindow mainWindow) {
		if (mainWindow != null) {
			var tab = mainWindow.getController().getMainTabPane().getTabs().stream()
					.filter(t -> t instanceof IDisplayTab).map(t -> (IDisplayTab) t)
					.filter(t -> t.getPresenter() instanceof WorldMapPresenter).findAny();
			if (tab.isPresent()) {
				mainWindow.getController().getMainTabPane().getSelectionModel().select((Tab) tab.get());
			} else {
				var viewBlock = new ViewBlock();
				viewBlock.setInputBlockName(CharactersBlock.BLOCK_NAME);
				var dataNode = mainWindow.getWorkflow().newDataNode(viewBlock);
				mainWindow.getWorkflow().getInputDataFilterNode().getChildren().add(dataNode);
				Platform.runLater(() -> {
					var isDirty = mainWindow.isDirty();
					var view = new WorldMapView(mainWindow, "World map", viewBlock.getViewTab());
					viewBlock.setView(view);
					viewBlock.setNode(dataNode);
					mainWindow.setDirty(isDirty);
				});
			}
		}
	}
}
