/*
 *  TextDisplayTabController.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.textdisplay;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;

public class TextDisplayController {

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

	private CodeArea codeArea;

	private VirtualizedScrollPane<CodeArea> scrollPane;

	@FXML
	private void initialize() {
		codeArea = new CodeArea();
		scrollPane = new VirtualizedScrollPane<>(codeArea);
		borderPane.setCenter(scrollPane);
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
}
