/*
 *  AskToDiscardChanges.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import splitstree6.main.Version;

/**
 * ask whether to discard changes
 * Daniel Huson, 6.2023
 */
public class AskToDiscardChanges {
	/**
	 * show the dialog
	 *
	 * @param stage   the window
	 * @param message Something like Overwrite or Discard
	 * @return true, if discard confirmed
	 */
	public static boolean apply(Stage stage, String message) {
		var alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.initModality(Modality.APPLICATION_MODAL);
		alert.initOwner(stage);

		alert.setTitle("Confirm " + message + " of Changes - " + Version.NAME);
		alert.setHeaderText("There are unsaved changes.");
		alert.setContentText(message + " existing changes?");

		var buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
		var buttonTypeOk = new ButtonType(message, ButtonBar.ButtonData.OK_DONE);

		alert.getButtonTypes().setAll(buttonTypeOk, buttonTypeCancel);

		var result = alert.showAndWait();
		return result.isPresent() && result.get() == buttonTypeOk;
	}
}
