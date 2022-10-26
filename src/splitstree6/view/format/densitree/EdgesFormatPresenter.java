/*
 *  EdgesFormatPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.densitree;

import splitstree6.view.trees.densitree.DensiTreeView;

public class EdgesFormatPresenter {
	public EdgesFormatPresenter(DensiTreeView view, EdgesFormatController controller) {
		var undoManager = view.getUndoManager();

		controller.getWidthCBox().getItems().addAll(0.1, 0.25, 0.5, 0.75, 1, 2, 3, 4, 5, 6, 8, 10, 20);
		controller.getWidthCBox().setValue(view.getOptionStrokeWidth());
		controller.getWidthCBox().setOnAction(e -> {
			var o = view.getOptionStrokeWidth();
			var n = controller.getWidthCBox().getValue();
			if (n != null)
				undoManager.doAndAdd("Width", view.optionStrokeWidthProperty(), o, n.doubleValue());
		});
		view.optionStrokeWidthProperty().addListener((v, o, n) -> controller.getWidthCBox().setValue(n.doubleValue()));

		controller.getColorPicker().setValue(view.getOptionEdgeColor());
		controller.getColorPicker().setOnAction(e -> {
			var o = view.getOptionEdgeColor();
			var n = controller.getColorPicker().getValue();
			undoManager.doAndAdd("Color", view.optionEdgeColorProperty(), o, n);
		});
		view.optionEdgeColorProperty().addListener((v, o, n) -> controller.getColorPicker().setValue(n));

		controller.getOtherColorPicker().setValue(view.getOptionOtherColor());
		controller.getOtherColorPicker().setOnAction(e -> {
			var o = view.getOptionOtherColor();
			var n = controller.getOtherColorPicker().getValue();
			undoManager.doAndAdd("Other color", view.optionOtherColorProperty(), o, n);
		});
		view.optionOtherColorProperty().addListener((v, o, n) -> controller.getOtherColorPicker().setValue(n));

	}
}
