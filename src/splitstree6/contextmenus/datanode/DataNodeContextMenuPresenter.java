/*
 *  DataNodeContextMenuPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.contextmenus.datanode;

import javafx.geometry.Point2D;
import splitstree6.dialog.attachnode.AttachNodeDialog;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

public class DataNodeContextMenuPresenter {

	public DataNodeContextMenuPresenter(MainWindow mainWindow, Point2D screenLocation, DataNodeContextMenuController controller, DataNode dataNode) {
		var workflow = mainWindow.getWorkflow();

		controller.getEditMenuItem().setOnAction(e -> mainWindow.getTextTabsManager().showTab(dataNode, true));

		controller.getExportMenuItem().setOnAction(e -> {
			System.err.println("Export: not implemented");
		});

		controller.getAttachAlgorithmMenuItem().setOnAction(e -> {
			new AttachNodeDialog(workflow, dataNode, screenLocation);
		});
		controller.getAttachAlgorithmMenuItem().disableProperty().bind(workflow.runningProperty());


	}
}
