/*
 * InputEditorTab.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tabs.inputeditor;

import javafx.application.Platform;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.inputeditor.InputEditorView;
import splitstree6.window.MainWindow;

/**
 * input editor tab
 * Daniel Huson, 11.2021
 */
public class InputEditorTab extends ViewTab {
	public static final String NAME = "Input Editor`";

	private final InputEditorView inputEditorView;

	/**
	 * constructor
	 */
	public InputEditorTab(MainWindow mainWindow) {
		super(mainWindow, null, false);
		if (false) {
			inputEditorView = null;
			setText(NAME);
		} else {
			this.inputEditorView = new InputEditorView(mainWindow, this);
			Platform.runLater(() -> {
				setView(inputEditorView);
				inputEditorView.getController().getCodeArea().requestFocus();
			});
		}
		inputEditorView.getController().getCodeArea().textProperty().addListener(e -> setEmpty(inputEditorView.getController().getCodeArea().getText().isEmpty()));
	}

	public void importFromFile(String fileName) {
		inputEditorView.importFromFile(fileName);
		Platform.runLater(() -> {
			inputEditorView.getController().getCodeArea().getUndoManager().forgetHistory();
		});
	}
}

