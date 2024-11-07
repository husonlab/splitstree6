/*
 * NodeLabelDialog.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.utils;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.control.TextField;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.undo.UndoManager;

/**
 * show the node label dialog
 * Daniel Huson, 11.2024
 */
public class NodeLabelDialog {

	public static boolean apply(UndoManager undoManager, Stage owner, RichTextLabel label) {
		var oldText = label.getText();
		var editLabelDialog = new EditLabelDialog(owner, label);
		var result = editLabelDialog.showAndWait();
		if (result.isPresent() && !result.get().equals(oldText)) {
			undoManager.doAndAdd("Edit Label", () -> label.setText(oldText), () -> label.setText(result.get()));
			return true;
		}
		return false;
	}

	public static void apply(UndoManager undoManager, RichTextLabel label, Runnable runAfter) {
		var bounds = label.getBoundsInLocal();
		var location = label.localToScreen(bounds.getMinX(), bounds.getMinY());
		var textField = new TextField(label.getText());
		textField.setFont(Font.font(Font.getDefault().getName(), label.getScale() * 14));
		textField.setPrefColumnCount(Math.max(10, Math.min(80, label.getRawText().length())));
		if (false) {
			textField.setPrefWidth(50);
			textField.setMinWidth(TextField.USE_PREF_SIZE);
			textField.setMaxWidth(TextField.USE_PREF_SIZE);
		}
		if (label.getParent() instanceof Group group) {
			var local = group.screenToLocal(location.getX(), location.getY());
			textField.setTranslateX(local.getX());
			textField.setTranslateY(local.getY());
			group.getChildren().add(textField);
		}

		textField.setOnAction(e -> {
			var oldText = label.getText();
			var newText = textField.getText();
			if (!newText.equals(oldText)) {
				undoManager.doAndAdd("Edit Label", () -> label.setText(oldText), () -> label.setText(newText));
			}
			Platform.runLater(() -> {
				if (textField.getParent() instanceof Group group) {
					group.getChildren().remove(textField);
				}
			});
			if (runAfter != null)
				Platform.runLater(runAfter);
		});
	}
}
