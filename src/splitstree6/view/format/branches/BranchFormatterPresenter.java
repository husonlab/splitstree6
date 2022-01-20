/*
 *  BranchFormatterPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.branches;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

/**
 * branch formatter presenter
 * Daniel Huson, 1.2022
 */
public class BranchFormatterPresenter {
	private final InvalidationListener selectionListener;

	private boolean inUpdatingDefaults = false;

	public BranchFormatterPresenter(MainWindow mainWindow, BranchFormatterController controller, SelectionModel<Integer> splitSelectionModel, Map<Integer, ArrayList<Shape>> splitShapeMap) {

		var strokeWidth = new SimpleDoubleProperty(1.0);
		controller.getWidthCBox().getItems().addAll(0.1, 0.5, 1d, 2d, 3d, 4d, 5d, 6d, 8d, 10d, 20d);
		controller.getWidthCBox().valueProperty().addListener((v, o, n) -> {
			if (n != null)
				strokeWidth.set(n);
		});

		strokeWidth.addListener((v, o, n) -> {
			if (!inUpdatingDefaults) {
				var width = n == null ? null : n.doubleValue();
				controller.getWidthCBox().setValue(width);
				if (width != null) {
					for (var split : splitSelectionModel.getSelectedItems()) {
						if (splitShapeMap.containsKey(split)) {
							for (var shape : splitShapeMap.get(split)) {
								shape.setStrokeWidth(width);
							}
						}
					}
				}
			}
		});

		controller.getColorPicker().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var color = controller.getColorPicker().getValue();
				for (var split : splitSelectionModel.getSelectedItems()) {
					if (splitShapeMap.containsKey(split)) {
						for (var shape : splitShapeMap.get(split)) {
							shape.setStroke(color);
						}
					}
				}
			}
		});

		selectionListener = e -> {
			inUpdatingDefaults = true;
			try {
				controller.getWidthCBox().setDisable(splitSelectionModel.size() == 0);
				controller.getColorPicker().setDisable(splitSelectionModel.size() == 0);

				var widths = new HashSet<Double>();
				var colors = new HashSet<Paint>();
				for (var split : splitSelectionModel.getSelectedItems()) {
					if (splitShapeMap.containsKey(split)) {
						for (var shape : splitShapeMap.get(split)) {
							if (shape.getUserData() instanceof Double width) // temporarily store with in user data when user is hovering over edge
								widths.add(width);
							else
								widths.add(shape.getStrokeWidth());
							colors.add(shape.getStroke());
						}
					}
				}
				var width = (widths.size() == 1 ? widths.iterator().next() : null);
				controller.getWidthCBox().setValue(width);
				strokeWidth.setValue(-1d);
				controller.getColorPicker().setValue(colors.size() == 1 ? (Color) colors.iterator().next() : null);
			} finally {
				inUpdatingDefaults = false;
			}
		};

		//selectionModel.getSelectedItems().addListener(selectionListener);
		splitSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));
		selectionListener.invalidated(null);

	}
}
