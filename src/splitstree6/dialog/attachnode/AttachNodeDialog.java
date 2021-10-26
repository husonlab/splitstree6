/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  AttachNodeDialog.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.dialog.attachnode;

import javafx.geometry.Point2D;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.window.NotificationManager;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;


public class AttachNodeDialog {
	public AttachNodeDialog(Workflow workflow, DataNode dataNode, Point2D screenLocation) {
		var loader = new ExtendedFXMLLoader<AttachNodeDialogController>(AttachNodeDialogController.class);
		var controller = loader.getController();

		new AttachNodeDialogPresenter(dataNode, controller);

		var dialog = new Dialog<ButtonType>();
		dialog.setDialogPane(controller.getDialogPane());
		dialog.setTitle("Attach Algorithm");
		dialog.setX(screenLocation.getX() + 20);
		dialog.setY(screenLocation.getY() + 20);

		var cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

		var attachButtonType = new ButtonType("Attach", ButtonBar.ButtonData.OK_DONE);
		//dialog.getDialogPane().lookupButton(attachButtonType).disableProperty().bind(controller.getAlgorithmCBox().valueProperty().isNull());

		dialog.getDialogPane().getButtonTypes().addAll(cancelButtonType, attachButtonType);

		dialog.showAndWait()
				.filter(response -> response == attachButtonType)
				.ifPresent(response -> attachAlgorithm(workflow, dataNode, controller.getAlgorithmCBox().getValue()));
	}

	private void attachAlgorithm(Workflow workflow, DataNode dataNode, Algorithm algorithm) {
		try {
			if (workflow.isRunning())
				throw new RuntimeException("Workflow is currently running");
			var targetDataNode = workflow.newDataNode((DataBlock) algorithm.getToClass().getConstructor().newInstance());
			workflow.newAlgorithmNode(algorithm, workflow.getWorkingTaxaNode(), dataNode, targetDataNode);
			NotificationManager.showInformation("Attached algorithm: " + algorithm.getName());
		} catch (Exception ex) {
			NotificationManager.showError("Attach algorithm failed: " + ex);
		}

	}


}
