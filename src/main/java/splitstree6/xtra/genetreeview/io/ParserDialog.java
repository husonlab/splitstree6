/*
 *  ParserDialog.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class ParserDialog extends Stage {

	protected final Label introLabel;
	protected final TextArea textInputArea;
	protected final Button startButton;
	protected final Button cancelButton;
	protected final Label infoLabel;
	protected final VBox vBoxOverall;
	protected final HBox hBoxBottom;
	protected final VBox vBoxInput;
	protected final HBox hBoxInput;
	protected final BooleanProperty parsedProperty = new SimpleBooleanProperty(false);

	public ParserDialog(Stage parentStage, String featureName, int expectedNumberOfValues) {
		this.initStyle(parentStage.getStyle());
		this.setTitle("ParserDialog");
		this.initModality(Modality.APPLICATION_MODAL);
		this.initOwner(parentStage);

		textInputArea = new TextArea();
		introLabel = new Label("Enter " + featureName + " in current tree order:");
		introLabel.setWrapText(true);
		infoLabel = new Label("");
		startButton = new Button("Import");
		startButton.setOnAction(e -> {
			String textInput = textInputArea.getText();
			ArrayList<String> values = extractFeatureValues(textInput, expectedNumberOfValues);
			if (values != null) {
				if (parseFeatureToModel(featureName, values))
					this.close();
				else infoLabel.setText("Import failed");
			} else {
				infoLabel.setText("Import failed");
			}
		});
		cancelButton = new Button("Cancel");
		cancelButton.setOnAction(e -> this.close());

		vBoxOverall = new VBox(10);
		vBoxOverall.setPadding(new Insets(10));
		vBoxInput = new VBox(10);
		vBoxInput.setPadding(new Insets(10));
		hBoxInput = new HBox();
		hBoxInput.getChildren().add(textInputArea);
		vBoxInput.getChildren().add(hBoxInput);
		hBoxBottom = new HBox();
		hBoxBottom.getChildren().addAll(startButton, cancelButton, infoLabel);
		hBoxBottom.setSpacing(5);
		vBoxOverall.getChildren().addAll(introLabel, vBoxInput, hBoxBottom);

		Scene scene = new Scene(vBoxOverall, 300, 200);
		this.setScene(scene);
		this.show();
	}

	ArrayList<String> extractFeatureValues(String textInput, int expectedLength) {
		String[] values;
		if (textInput.split("\t").length == expectedLength) values = textInput.split("\t");
		else if (textInput.split(",").length == expectedLength) values = textInput.split(",");
		else if (textInput.split(";").length == expectedLength) values = textInput.split(";");
		else if (textInput.split("\n").length == expectedLength) values = textInput.split("\n");
		else if (textInput.split(" ").length == expectedLength) values = textInput.split(" ");
		else return null;
		for (int i = 0; i < expectedLength; i++) {
			values[i] = values[i].trim();
		}
		return new ArrayList<>(Arrays.stream(values).toList());
	}

	abstract boolean parseFeatureToModel(String featureName, ArrayList<String> values);

	public BooleanProperty getParsedProperty() {
		return parsedProperty;
	}
}
