/*
 *  MarksPresenter.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import jloda.fx.control.RichTextLabel;
import jloda.fx.undo.UndoManager;
import jloda.fx.undo.UndoableRedoableCommandList;
import jloda.fx.util.BasicFX;
import splitstree6.window.MainWindow;

public class MarksPresenter {
	private final InvalidationListener selectionListener;

	public MarksPresenter(MainWindow mainWindow, UndoManager undoManager, MarksController controller) {

		controller.getFillColorPicker().valueProperty().addListener((v, o, n) -> {
			if (n != null) {
				var undoList = new UndoableRedoableCommandList("add marks");

				for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
					var oldLabel = taxon.getDisplayLabelOrName();
					var newLabel = "<mark fill=\"" + BasicFX.toStringCSS(n) + "\">" + taxon.getDisplayLabelOrName();
					undoList.add(() -> taxon.setDisplayLabel(oldLabel), () -> taxon.setDisplayLabel(newLabel));
				}
				if (undoList.size() > 0)
					undoManager.doAndAdd(undoList);
			}
		});

		controller.getClearColorButton().setOnAction(e -> {
			var undoList = new UndoableRedoableCommandList("clear marks");

			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				var oldLabel = taxon.getDisplayLabelOrName();
				var newLabel = RichTextLabel.removeMark(taxon.getDisplayLabelOrName());
				undoList.add(() -> taxon.setDisplayLabel(oldLabel), () -> taxon.setDisplayLabel(newLabel));
			}
			if (undoList.size() > 0)
				undoManager.doAndAdd(undoList);
		});

		selectionListener = e -> {
			controller.getTitledPane().setDisable(mainWindow.getTaxonSelectionModel().size() == 0);
			controller.getFillColorPicker().setDisable(mainWindow.getTaxonSelectionModel().size() == 0);
			controller.getClearColorButton().setDisable(mainWindow.getTaxonSelectionModel().size() == 0);
			controller.getFillColorPicker().setValue(null);
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));
		selectionListener.invalidated(null);
	}
}
