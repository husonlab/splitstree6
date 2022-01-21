/*
 *  TryRichText.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.xtra;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;

public class TryRichText extends Application {
	@Override
	public void start(Stage primaryStage) throws Exception {

		var richText = new RichTextLabel();
		var textField = new TextField();

		var bold = new ToggleButton("Bold");
		bold.selectedProperty().addListener((v, o, n) -> richText.setBold(n));
		var italic = new ToggleButton("Italic");
		italic.selectedProperty().addListener((v, o, n) -> richText.setItalic(n));

		var fontFamily = new TextField();
		fontFamily.setOnAction(e -> richText.setFontFamily(fontFamily.getText()));

		textField.setOnAction(e -> {
			richText.setText(textField.getText());
			richText.setBold(bold.isSelected());
			richText.setItalic(italic.isSelected());
			richText.setFontFamily(fontFamily.getText());

			for (var type : RichTextLabel.Event.listTypes()) {
				var event = RichTextLabel.getPrefixElement(richText.getText(), type);
				if (event != null) {
					System.err.println(richText.getText().substring(event.pos(), event.segmentStart()));
				}
			}
		});


		var rotate = new Button("Rotate");
		rotate.setOnAction(e -> richText.setRotate(richText.getRotate() + 20));
		var unRotate = new Button("Un-Rotate");
		unRotate.setOnAction(e -> richText.setRotate(richText.getRotate() - 20));

		var root = new BorderPane();
		root.setTop(textField);
		root.setCenter(richText);
		root.setBottom(new HBox(rotate, unRotate));

		root.setRight(new VBox(bold, italic, fontFamily));


		var scene = new Scene(root, 300, 300);
		primaryStage.setScene(scene);
		primaryStage.sizeToScene();
		primaryStage.show();
	}
}
