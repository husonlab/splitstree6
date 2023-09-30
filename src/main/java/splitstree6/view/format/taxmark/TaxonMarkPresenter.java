/*
 *  TaxonMarkPresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.format.taxmark;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import jloda.fx.control.RichTextLabel;
import jloda.fx.shapes.NodeShape;
import jloda.fx.undo.UndoManager;
import jloda.fx.undo.UndoableRedoableCommandList;
import jloda.fx.util.ColorUtilsFX;
import jloda.fx.util.ProgramProperties;
import jloda.util.Single;
import splitstree6.window.MainWindow;

public class TaxonMarkPresenter {
	private final InvalidationListener selectionListener;
	private final BooleanProperty emptySelection = new SimpleBooleanProperty(true);

	public TaxonMarkPresenter(MainWindow mainWindow, UndoManager undoManager, TaxonMarkController controller) {
		controller.getTitledPane().disableProperty().bind(mainWindow.emptyProperty());

		controller.getAddButton().setOnAction(e -> {
			var fill = controller.getFillColorPicker().getValue();
			var shape = controller.getShapeCBox().getValue();
			if (fill != null && shape != null) {
				var undoList = new UndoableRedoableCommandList("add marks");

				for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
					var oldLabel = taxon.getDisplayLabelOrName();
					var newLabel = "<mark shape=\"" + shape.name() + "\" fill=\"" + ColorUtilsFX.toStringCSS(fill) + "\" stroke=\"lightgray\">" + taxon.getDisplayLabelOrName();
					undoList.add(() -> taxon.setDisplayLabel(oldLabel), () -> taxon.setDisplayLabel(newLabel));
				}
				if (undoList.size() > 0)
					undoManager.doAndAdd(undoList);
			}

		});
		controller.getAddButton().setDisable(emptySelection.getValue());
		controller.getAddButton().disableProperty().bind(controller.getFillColorPicker().valueProperty().isNull()
				.or(controller.getShapeCBox().valueProperty().isNull()).or(emptySelection));

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
		controller.getClearColorButton().setDisable(emptySelection.getValue());
		controller.getClearColorButton().disableProperty().bind(emptySelection);

		ProgramProperties.track("TaxonMarkFill", controller.getFillColorPicker().valueProperty(), Color.TRANSPARENT);
		controller.getFillColorPicker().setDisable(emptySelection.getValue());
		controller.getFillColorPicker().disableProperty().bind(emptySelection);

		ProgramProperties.track("TaxonMarkShape", controller.getShapeCBox().valueProperty(), NodeShape::valueOf, NodeShape.Square);
		controller.getShapeCBox().setDisable(emptySelection.getValue());
		controller.getShapeCBox().disableProperty().bind(emptySelection);

		selectionListener = e -> {
			var fill = new Single<Paint>();
			var shape = new Single<NodeShape>();

			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				var mark = RichTextLabel.getMark(taxon.getDisplayLabelOrName());
				if (mark != null) {
					var markShape = NodeShape.valueOf(mark);
					if (fill != null) {
						if (fill.get() == null)
							fill.set(mark.getFill());
						else if (!fill.get().equals(mark.getFill()))
							fill = null;
					}
					if (shape != null) {
						if (shape.get() == null)
							shape.set(markShape);
						else if (!shape.get().equals(markShape))
							shape = null;
					}
				}
			}
			if (fill == null) // multiple colors
				controller.getFillColorPicker().setValue(null);
			else if (fill.isNotNull()) // one color
				controller.getFillColorPicker().setValue((Color) fill.get());

			if (shape == null) // multiple shapes
				controller.getShapeCBox().setValue(null);
			else if (shape.isNotNull()) // one getShape
				controller.getShapeCBox().setValue(shape.get());

			emptySelection.set(mainWindow.getTaxonSelectionModel().size() == 0);
		};

		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));
		selectionListener.invalidated(null);
	}
}
