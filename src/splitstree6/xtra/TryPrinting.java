/*
 * TryPrinting.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.xtra;
//Code from: https://coderanch.com/t/709329/java/JavaFX-approach-dividing-text-blob

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import jloda.fx.util.Print;

public class TryPrinting extends Application {

	public static void main(String[] args) {
		launch();
	}

	public void start(Stage stage) {
		var textArea = new TextArea(createTextBlob());

		var printButton = new Button("Print");                         // Print Button
		printButton.setOnAction(e -> Print.printText(stage, textArea.getText()));

		var borderPane = new BorderPane();
		borderPane.setTop(printButton);
		borderPane.setCenter(textArea);

		stage.setScene(new Scene(borderPane));
		stage.setWidth(600);
		stage.setHeight(600);
		stage.setTitle("TryPrinting");
		stage.show();
	}

	private String createTextBlob() {
		var buf = new StringBuilder();
		for (int i = 1; i <= 100; i++)
			buf.append(String.format("Line %03d%n", i));
		return buf.toString();
	}
}
