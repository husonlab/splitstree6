/*
 * DisplayTextViewController.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.displaytext;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import jloda.fx.icons.MaterialIcons;
import splitstree6.view.displaytext.highlighters.Highlighter;

public class DisplayTextViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private ToolBar toolBar;


	@FXML
	private ToggleButton wrapTextToggle;

	@FXML
	private ToggleButton lineNumbersToggle;

	@FXML
	private Button selectButton;
	@FXML
	private VBox topVBox;


	private MyTextArea codeArea;

	private Node scrollPane;

	private Highlighter highlighter;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(wrapTextToggle, "wrap_text");
		MaterialIcons.setIcon(lineNumbersToggle, "format_list_numbered");
		MaterialIcons.setIcon(selectButton, "select_all");

		codeArea = new MyTextArea();
		codeArea.requestFollowCaret();

		codeArea.getStylesheets().add(DisplayTextViewController.class.getResource("display_text_styles.css").toExternalForm());

		codeArea.getStyleClass().add("viewer-background");

		codeArea.setPadding(new Insets(5, 2, 5, 2));
		scrollPane = codeArea.getEnclosingNode();
		borderPane.setCenter(scrollPane);
		codeArea.prefWidthProperty().bind(borderPane.widthProperty());
		highlighter = codeArea.getHighlighter();
	}

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public BorderPane getBorderPane() {
		return borderPane;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public ToggleButton getWrapTextToggle() {
		return wrapTextToggle;
	}

	public ToggleButton getLineNumbersToggle() {
		return lineNumbersToggle;
	}

	public Button getSelectButton() {
		return selectButton;
	}

	public VBox getTopVBox() {
		return topVBox;
	}

	public MyTextArea getCodeArea() {
		return codeArea;
	}

	public Node getScrollPane() {
		return scrollPane;
	}
}
