/*
 *  EdgeLabelPresenter.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.fx.window.MainWindowManager;
import splitstree6.view.trees.densitree.DensiTreeView;

import static splitstree6.view.trees.densitree.DensiTreeView.*;

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

		controller.getResetColorButton().setOnAction(a -> {
			var newColor = MainWindowManager.isUseDarkTheme() ? DEFAULT_DARKMODE_EDGE_COLOR : DEFAULT_LIGHTMODE_EDGE_COLOR;
			undoManager.doAndAdd("Color", view.optionEdgeColorProperty(), view.getOptionEdgeColor(), newColor);
		});
		controller.getResetColorButton().disableProperty().bind(
				(MainWindowManager.useDarkThemeProperty().and(view.optionEdgeColorProperty().isEqualTo(DEFAULT_DARKMODE_EDGE_COLOR)))
						.or(MainWindowManager.useDarkThemeProperty().not().and(view.optionEdgeColorProperty().isEqualTo(DEFAULT_LIGHTMODE_EDGE_COLOR))));

		controller.getResetOtherColorButton().setOnAction(a -> undoManager.doAndAdd("Other color", view.optionOtherColorProperty(), view.getOptionOtherColor(), DEFAULT_OTHER_COLOR));
		controller.getResetOtherColorButton().disableProperty().bind(view.optionOtherColorProperty().isEqualTo(DEFAULT_OTHER_COLOR));

		controller.getResetWidthButton().setOnAction(a -> undoManager.doAndAdd("Width", view.optionStrokeWidthProperty(), view.getOptionStrokeWidth(), DEFAULT_STROKE_WIDTH));
		controller.getResetWidthButton().disableProperty().bind(view.optionStrokeWidthProperty().isEqualTo(DEFAULT_STROKE_WIDTH, 0.01));
	}
}
