/*
 *  Marks.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.marks;

import javafx.scene.layout.Pane;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.window.MainWindow;


public class Marks extends Pane {
	private final MarksController controller;
	private final MarksPresenter presenter;


	public Marks(MainWindow mainWindow, UndoManager undoManager) {
		var loader = new ExtendedFXMLLoader<MarksController>(MarksController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new MarksPresenter(mainWindow, undoManager, controller);
	}

}
