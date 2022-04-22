/*
 *  AxisAndScrollBarUpdate.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.alignment;

import javafx.beans.InvalidationListener;
import javafx.geometry.Point2D;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;
import jloda.fx.selection.SelectionModel;

/**
 * update axis and horizontal scroll bar
 * Daniel Huson, 4.2022
 */
public class AxisAndScrollBarUpdate {

	public static void apply(NumberAxis axis, ScrollBar scrollBar, double canvasWidth, double boxWidth, int nChar,
							 SelectionModel<Integer> siteSelectionModel) {
		if (nChar < 1) {
			scrollBar.setVisible(false);
			axis.setVisible(false);
		} else {
			scrollBar.setVisible(true);
			axis.setVisible(true);
			var numberOnCanvas = canvasWidth / boxWidth;
			scrollBar.setMin(1);
			scrollBar.setMax(nChar);
			scrollBar.setVisibleAmount(numberOnCanvas);

			axis.setLowerBound(Math.max(1, Math.floor(scrollBar.getValue())));
			axis.setUpperBound(Math.round(scrollBar.getValue() + numberOnCanvas));

			axis.setOnMouseClicked(event -> {
				var pointInScene = new Point2D(event.getSceneX(), event.getSceneY());
				double xPosInAxis = axis.sceneToLocal(new Point2D(pointInScene.getX(), 0)).getX();
				var site = (int) Math.round(axis.getValueForDisplay(xPosInAxis).doubleValue());
				if (site >= 1 && site <= nChar) {
					if (event.isShiftDown()) {
						if (siteSelectionModel.size() == 0 || siteSelectionModel.size() == 1 && siteSelectionModel.isSelected(site)) {
							siteSelectionModel.toggleSelection(site);
						} else {
							var left = site;
							while (left >= 1 && !siteSelectionModel.isSelected(left))
								left--;
							var right = site;
							while (right <= nChar && !siteSelectionModel.isSelected(right))
								right++;
							if (left >= 1) {
								for (var s = left; s <= site; s++)
									siteSelectionModel.select(s);
							}
							if (right <= nChar) {
								for (var s = site; s <= right; s++)
									siteSelectionModel.select(s);
							}
						}
					} else if (event.isShortcutDown()) {
						siteSelectionModel.toggleSelection(site);
					} else {
						siteSelectionModel.clearSelection();
						siteSelectionModel.select(site);
					}
				}
			});
			if (numberOnCanvas < 100) {
				axis.setTickUnit(1);
				axis.setMinorTickVisible(false);
			} else if (numberOnCanvas < 500) {
				axis.setTickUnit(10);
				axis.setMinorTickVisible(true);
			} else if (numberOnCanvas < 5000) {
				axis.setTickUnit(100);
				axis.setMinorTickVisible(true);
			} else {
				axis.setTickUnit(1000);
				axis.setMinorTickVisible(true);
			}
		}
	}

	public static InvalidationListener setupSelectionVisualization(StackPane pane, NumberAxis axis, SelectionModel<Integer> siteSelectionModel) {
		return null;
	}
}
