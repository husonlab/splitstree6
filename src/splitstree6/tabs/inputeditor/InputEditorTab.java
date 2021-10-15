/*
 *  WorkflowTreeView.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.inputeditor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

public class InputEditorTab extends Tab implements IDisplayTab {
	public static final String NAME = "Input Editor";
	private final InputEditorTabController controller;
	private final InputEditorTabPresenter presenter;

	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	/**
	 * constructor
	 */
	public InputEditorTab(MainWindow mainWindow) {
		var loader = new ExtendedFXMLLoader<InputEditorTabController>(this.getClass());

		controller = loader.getController();
		presenter = new InputEditorTabPresenter(mainWindow, this);

		setContent(loader.getRoot());

		//empty.bind();

		setText(NAME);
		setClosable(false);
	}

	@Override
	public UndoManager getUndoManager() {
		return null;
	}

	@Override
	public ReadOnlyBooleanProperty isEmptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return null;
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	public InputEditorTabController getController() {
		return controller;
	}

	/**
	 * go to given line and given col
	 *
	 * @param col if col<=1 or col>line length, will select the whole line, else selects line starting at given col
	 */
	public void gotoLine(long lineNumber, int col) {
		if (col < 0)
			col = 0;
		else if (col > 0)
			col--; // because col is 1-based

		lineNumber = Math.max(1, lineNumber);
		final String text = controller.getCodeArea().getText();
		int start = 0;
		for (int i = 1; i < lineNumber; i++) {
			start = text.indexOf('\n', start + 1);
			if (start == -1) {
				System.err.println("No such line number: " + lineNumber);
				return;
			}
		}
		start++;
		if (start < text.length()) {
			int end = text.indexOf('\n', start);
			if (end == -1)
				end = text.length();
			if (start + col < end)
				start = start + col;
			controller.getScrollPane().requestFocus();
			controller.getCodeArea().selectRange(start, end);
		}
	}
}

