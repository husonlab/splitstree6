/*
 *  AxisAndScrollBarUpdate.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.geometry.Point2D;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import splitstree6.data.CharactersBlock;
import splitstree6.main.SplitsTree6;

import java.util.BitSet;

import static splitstree6.view.alignment.AlignmentDrawer.SELECTION_STROKE;

/**
 * update axis and horizontal scroll bar
 * Daniel Huson, 4.2022
 */
public class AxisAndScrollBarUpdate {

	public static void update(NumberAxis axis, ScrollBar hScrollBar, double canvasWidth, double boxWidth, int nChar, AlignmentView alignmentView) {
		if (nChar < 1) {
			hScrollBar.setVisible(false);
			axis.setVisible(false);
		} else {
			hScrollBar.setVisible(true);
			axis.setVisible(true);

			var numberOnCanvas = (canvasWidth - 10) / boxWidth;
			hScrollBar.setMin(1);
			hScrollBar.setMax(nChar - numberOnCanvas + 1);
			hScrollBar.setVisibleAmount(numberOnCanvas);
			if (numberOnCanvas >= nChar) {
				hScrollBar.setValue(0);
			}

			hScrollBar.setBlockIncrement(numberOnCanvas);

			axis.setLowerBound(Math.max(1, Math.floor(hScrollBar.getValue())));
			axis.setUpperBound(Math.round(hScrollBar.getValue() + numberOnCanvas));

			axis.setOnMouseClicked(event -> {
				var pointInScene = new Point2D(event.getSceneX(), event.getSceneY());
				double xPosInAxis = axis.sceneToLocal(new Point2D(pointInScene.getX(), 0)).getX();
				var site = (int) Math.round(axis.getValueForDisplay(xPosInAxis).doubleValue());
				if (site >= 1 && site <= nChar) {
					var bits = new BitSet();
					bits.or(alignmentView.getSelectedSites());
					if (event.isShiftDown() || !SplitsTree6.isDesktop()) {
						if (bits.cardinality() == 0 || bits.cardinality() == 1 && bits.get(site)) {
							bits.set(site, !bits.get(site));
						} else {
							var left = site;
							while (left >= 1 && !bits.get(left))
								left--;
							var right = site;
							while (right <= nChar && !bits.get(right))
								right++;
							if (left >= 1) {
								for (var s = left; s <= site; s++)
									bits.set(s);
							}
							if (right <= nChar) {
								for (var s = site; s <= right; s++)
									bits.set(s);
							}
						}
					} else if (event.isShortcutDown()) {
						bits.set(site, !bits.get(site));
					} else {
						bits.clear();
						bits.set(site);
					}
					alignmentView.setSelectedSites(bits);
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

	public static void updateSelection(Pane selectionPane, NumberAxis axis, CharactersBlock inputCharacters, BitSet activeSites, BitSet selectedSites) {
		selectionPane.setVisible(axis.isVisible());
		selectionPane.getChildren().clear();

		if (inputCharacters != null) {
			var boxWidth = (axis.getWidth()) / (axis.getUpperBound() - axis.getLowerBound());
			var left = Math.max(1, (int) axis.getLowerBound() - 1);
			var right = Math.min(inputCharacters.getNchar(), axis.getUpperBound() - 1);

			var inactiveFill = Color.DARKGRAY.deriveColor(1, 1, 1, 0.3);

			for (var site = left; site <= right; site++) {
				var inactive = !activeSites.get(site);
				var selected = selectedSites.get(site);
				if (selected || inactive) {
					var x = (site - axis.getLowerBound()) * boxWidth;
					var rectangle = new Rectangle(x, axis.getHeight(), Math.max(0.5, boxWidth), 3);
					rectangle.setStrokeWidth(0.5);
					if (selected) {
						rectangle.setStroke(SELECTION_STROKE);
						rectangle.setFill(SELECTION_STROKE);
					} else {
						rectangle.setFill(inactiveFill);
					}
					selectionPane.getChildren().add(rectangle);
				}
			}
		}
	}
}
