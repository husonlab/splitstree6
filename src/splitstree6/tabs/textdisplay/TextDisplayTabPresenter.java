/*
 *  TextDisplayTabPresenter.java Copyright (C) 2021 Daniel H. Huson
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


import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

public class TextDisplayTabPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final IDisplayTab tab;

	public TextDisplayTabPresenter(MainWindow mainWindow, IDisplayTab tab) {
		this.mainWindow = mainWindow;
		this.tab = tab;
	}

	public void setup() {
		var controller = mainWindow.getController();

		controller.getCopyMenuItem().setOnAction(null);

		controller.getUndoMenuItem().setOnAction(e -> tab.getUndoManager().undo());
		controller.getUndoMenuItem().disableProperty().bind(tab.getUndoManager().undoableProperty().not());
		controller.getRedoMenuItem().setOnAction(e -> tab.getUndoManager().redo());
		controller.getRedoMenuItem().disableProperty().bind(tab.getUndoManager().redoableProperty().not());

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		controller.getGotoLineMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(null);
		controller.getSelectNoneMenuItem().setOnAction(null);

		controller.getSelectBracketsMenuItem().setOnAction(null);

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		controller.getZoomInMenuItem().setOnAction(null);
		controller.getZoomOutMenuItem().setOnAction(null);

		controller.getWrapTextMenuItem().setOnAction(null);
	}

}
