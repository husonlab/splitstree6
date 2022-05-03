/*
 *  DrawAlignment.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.DoubleProperty;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.ScrollBar;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import jloda.fx.window.MainWindowManager;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.util.BitSet;

/**
 * draw the alignment and indicate selection
 * Daniel Huson, 4.2022
 */
public class DrawAlignment {
	/**
	 * draw the alignment
	 */
	public static void updateCanvas(Canvas canvas, TaxaBlock inputTaxa, CharactersBlock inputCharacters, ColorScheme colorScheme,
									double boxHeight, ScrollBar vScrollBar, NumberAxis axis, BitSet activateTaxa, BitSet activeSites) {
		var gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

		if (inputTaxa != null && inputCharacters != null) {
			var axisStartOffset = 7;
			var boxWidth = (axis.getWidth()) / (axis.getUpperBound() - axis.getLowerBound());

			var fontSize = 0.9 * Math.min(boxWidth, boxHeight);
			gc.setFont(Font.font("monospaced", fontSize));
			var showColors = (colorScheme != ColorScheme.None);

			var lineStroke = MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;
			var textFill = !showColors && MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;
			// will only stroke to show selection:

			var vOffset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvas.getHeight() - inputTaxa.getNtax() * boxHeight)) : 0;

			for (var t = 1; t <= inputTaxa.getNtax(); t++) {
				var y = t * boxHeight + vOffset;
				if (y < 0)
					continue;
				if (y > canvas.getHeight() + boxHeight)
					break;

				var left = Math.max(1, (int) axis.getLowerBound() - 1);
				var right = Math.min(inputCharacters.getNchar(), Math.ceil(axis.getUpperBound()));

				for (var site = left; site <= right; site++) {
					var ch = inputCharacters.get(t, site);
					var x = (site - axis.getLowerBound()) * boxWidth + axisStartOffset;
					if (!activateTaxa.get(t) || !activeSites.get(site)) {
						gc.setFill(Color.TRANSPARENT);
						gc.fillRect(x, y - boxHeight, boxWidth, boxHeight);
						gc.setFill(MainWindowManager.isUseDarkTheme() ? Color.web("0x6F6F6F") : Color.LIGHTGRAY);
					} else if (showColors) {
						gc.setFill(colorScheme.apply(ch));
						gc.fillRect(x, y - boxHeight, boxWidth, boxHeight);
						gc.setFill(textFill);
					} else
						gc.setFill(textFill);
					//if(fontSize>=6)
					gc.fillText(String.valueOf(ch), x + 0.5 * (boxWidth - fontSize), y - 0.4 * fontSize);
					if (site == 1) {
						gc.setLineWidth(0.75);
						gc.setStroke(lineStroke);
						gc.strokeLine(x, y - boxHeight, x, y);
					}
					if (t == inputTaxa.getNtax()) {
						gc.setLineWidth(0.75);
						gc.setStroke(lineStroke);
						gc.strokeLine(x, y, x + boxWidth, y);
					}
					if (site == inputCharacters.getNchar()) {
						gc.setLineWidth(0.75);
						gc.setStroke(lineStroke);
						gc.strokeLine(x + boxWidth, y - boxHeight, x + boxWidth, y);
					}
				}
			}
		}
	}

	public final static Color SELECTION_FILL = Color.web("#039ED3").deriveColor(1, 1, 1, 0.4);
	public final static Color SELECTION_STROKE = (Color.web("#039ED3"));

	/**
	 * update the site selection visualization
	 */
	public static void updateSiteSelection(Canvas canvas, Group selectionGroup, TaxaBlock inputTaxa, CharactersBlock inputCharacters,
										   double boxHeight, ScrollBar vScrollBar, NumberAxis axis, BitSet selectedSites) {
		selectionGroup.getChildren().clear();

		if (inputTaxa != null && inputCharacters != null) {
			var axisStartOffset = 7;
			var boxWidth = (axis.getWidth()) / (axis.getUpperBound() - axis.getLowerBound());

			var vOffset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvas.getHeight() - inputTaxa.getNtax() * boxHeight)) : 0;

			var height = Math.min(canvas.getHeight(), inputTaxa.getNtax() * boxHeight + vOffset);
			var left = (int) Math.max(1, Math.floor(axis.getLowerBound()) - 1);
			var right = Math.min(inputCharacters.getNchar(), Math.ceil(axis.getUpperBound()));

			var strokeWidth = Math.min(1, boxWidth / 3);

			for (var site = left; site <= right; site++) {
				var x = (site - axis.getLowerBound()) * boxWidth + axisStartOffset;
				if (selectedSites.get(site)) {
					var rectangle = new Rectangle(x + 0.5 * strokeWidth, 0, boxWidth - strokeWidth, height);
					rectangle.setStrokeWidth(strokeWidth);
					rectangle.setFill(SELECTION_FILL);
					rectangle.setStroke(SELECTION_STROKE);
					selectionGroup.getChildren().add(rectangle);
				}
			}
		}
	}

	/**
	 * update the taxon selection visualization
	 */
	public static void updateTaxaSelection(Canvas canvas, Group selectionGroup, TaxaBlock inputTaxa, CharactersBlock inputCharacters,
										   double boxHeight, ScrollBar vScrollBar, NumberAxis axis, BitSet selectedTaxa) {
		selectionGroup.getChildren().clear();

		if (inputTaxa != null && inputCharacters != null) {
			var boxWidth = (axis.getWidth()) / (axis.getUpperBound() - axis.getLowerBound());
			var offset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvas.getHeight() - inputTaxa.getNtax() * boxHeight)) : 0;

			var strokeWidth = Math.min(1, boxHeight / 3);

			var width = (inputCharacters.getNchar() - axis.getLowerBound() + 1) * boxWidth;
			for (var t = 1; t <= inputTaxa.getNtax(); t++) {
				var y = t * boxHeight + offset;
				if (y < 0)
					continue;

				if (y > canvas.getHeight() + boxHeight)
					break;

				if (selectedTaxa.get(t)) {
					var rectangle = new Rectangle(7, y - boxHeight + 0.5 * strokeWidth, width, boxHeight - strokeWidth);
					rectangle.setFill(SELECTION_FILL);
					rectangle.setStroke(SELECTION_STROKE);
					selectionGroup.getChildren().add(rectangle);
				}
			}
		}
	}

	public static void zoomToFit(Canvas canvas, TaxaBlock inputTaxa, CharactersBlock inputCharacters, DoubleProperty unitWidthProperty, DoubleProperty unitHeightProperty) {
		if (inputTaxa != null && inputCharacters != null) {
			var newWidth = Math.min(18, canvas.getWidth() / (inputCharacters.getNchar() + 3));
			var newHeight = Math.min(18, canvas.getHeight() / (inputTaxa.getNtax() + 2));
			if (newWidth < unitWidthProperty.get() && newHeight < unitHeightProperty.get()) {
				unitWidthProperty.set(newWidth);
				unitHeightProperty.set(newHeight);
			} else {
				unitWidthProperty.set(18);
				unitHeightProperty.set(18);
			}
		}
	}
}
