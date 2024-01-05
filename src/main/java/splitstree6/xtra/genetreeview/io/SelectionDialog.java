/*
 *  SelectionDialog.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.io;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Objects;

public abstract class SelectionDialog extends Stage {

	protected Label introLabel;
	protected final Button startButton;
	protected final Button cancelButton;
	protected final BooleanProperty doneProperty = new SimpleBooleanProperty(false);
	protected String finalSelectedName;

	protected SelectionDialog(Stage parentStage, String selectedName, String selectionType) {
		this.initStyle(parentStage.getStyle());
		this.initModality(Modality.APPLICATION_MODAL);
		this.initOwner(parentStage);
		this.setTitle("SelectionDialog");

		introLabel = new Label("Continue with following selection:");
		introLabel.setWrapText(true);
		TextField nameTextField = new TextField(selectedName);
		Label infoLabel = new Label("");
		startButton = new Button("Start");
		startButton.setOnAction(e -> {
			String finalSelectedName = nameTextField.getText();
			if (finalSelectedName != null & !Objects.equals(finalSelectedName, "")) {
				this.finalSelectedName = finalSelectedName;
				doneProperty.set(true);
				this.close();
			} else {
				infoLabel.setText("Please provide a " + selectionType);
			}
		});
		cancelButton = new Button("Cancel");
		cancelButton.setOnAction(e -> {
			finalSelectedName = null;
			doneProperty.set(true);
			this.close();
		});

		BorderPane borderPane = new BorderPane();
		borderPane.setPadding(new Insets(10));
		VBox vBox = new VBox(10);
		vBox.setPadding(new Insets(10));
		HBox hBox = new HBox();
		hBox.getChildren().addAll(startButton, cancelButton);
		hBox.setSpacing(5);
		vBox.getChildren().addAll(nameTextField, hBox);
		borderPane.setTop(introLabel);
		borderPane.setCenter(vBox);
		borderPane.setBottom(infoLabel);

		Scene scene = new Scene(borderPane, 370, 155);
		this.setScene(scene);
		this.show();
	}

	public BooleanProperty doneProperty() {
		return doneProperty;
	}

	public String getFinalSelectedName() {
		return finalSelectedName;
	}
}
