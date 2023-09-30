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
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import jloda.fx.util.ResourceManagerFX;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import splitstree6.view.displaytext.highlighters.Highlighter;

public class DisplayTextViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Button findButton;

	@FXML
	private Button findAndReplaceButton;

	@FXML
	private ToggleButton wrapTextToggle;

	@FXML
	private ToggleButton lineNumbersToggle;

	@FXML
	private VBox topVBox;

	@FXML
	public Button increaseFontButton;

	@FXML
	public Button decreaseFontButton;

	private CodeArea codeArea;

	private VirtualizedScrollPane<CodeArea> scrollPane;

	private Highlighter highlighter;

	@FXML
	private void initialize() {
		codeArea = new CodeArea();
		codeArea.requestFollowCaret();

		final var url = ResourceManagerFX.getCssURL("styles.css");
		if (url != null) {
			codeArea.getStylesheets().add(url.toExternalForm());
		}

		//codeArea.getStyleClass().add("text");
		codeArea.getStyleClass().add("viewer-background");

		codeArea.setPadding(new Insets(5, 2, 5, 2));
		scrollPane = new VirtualizedScrollPane<>(codeArea);
		borderPane.setCenter(scrollPane);
		codeArea.prefWidthProperty().bind(borderPane.widthProperty());

		highlighter = new Highlighter(codeArea);
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

	public Button getFindButton() {
		return findButton;
	}

	public Button getFindAndReplaceButton() {
		return findAndReplaceButton;
	}

	public ToggleButton getWrapTextToggle() {
		return wrapTextToggle;
	}

	public ToggleButton getLineNumbersToggle() {
		return lineNumbersToggle;
	}

	public VBox getTopVBox() {
		return topVBox;
	}

	public CodeArea getCodeArea() {
		return codeArea;
	}

	public VirtualizedScrollPane<CodeArea> getScrollPane() {
		return scrollPane;
	}

	public Button getIncreaseFontButton() {
		return increaseFontButton;
	}

	public Button getDecreaseFontButton() {
		return decreaseFontButton;
	}
}
