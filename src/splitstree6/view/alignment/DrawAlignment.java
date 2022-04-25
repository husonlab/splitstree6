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

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.ScrollBar;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import jloda.fx.selection.SelectionModel;
import jloda.fx.window.MainWindowManager;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

/**
 * draw the alignment and indicate selection
 * Daniel Huson, 4.2022
 */
public class DrawAlignment {
	/**
	 * draw the alignment
	 */
	public static void updateCanvas(Canvas canvas, TaxaBlock taxaBlock, CharactersBlock charactersBlock, ColorScheme colorScheme,
									double boxWidth, double boxHeight, ScrollBar vScrollBar, NumberAxis axis) {
		var gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

		if (taxaBlock != null && charactersBlock != null) {
			var fontSize = 0.9 * Math.min(boxWidth, boxHeight);
			gc.setFont(Font.font("monospaced", fontSize));
			var showColors = (colorScheme != ColorScheme.None);

			var lineStroke = MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;
			var textFill = !showColors && MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;
			// will only stroke to show selection:

			var offset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvas.getHeight() - taxaBlock.getNtax() * boxHeight)) : 0;

			for (var t = 1; t <= taxaBlock.getNtax(); t++) {
				var y = t * boxHeight + offset;
				if (y < 0)
					continue;
				;
				if (y > canvas.getHeight() + boxHeight)
					break;

				var left = (int) Math.max(1, Math.floor(axis.getLowerBound()));
				var right = Math.min(charactersBlock.getNchar(), Math.ceil(axis.getUpperBound()));

				var col = 0;
				for (var c = left; c <= right; c++) {
					var ch = charactersBlock.get(t, c);
					var x = (col++) * boxWidth;
					if (showColors) {
						gc.setFill(colorScheme.apply(ch));
						gc.fillRect(x, y - boxHeight, boxWidth, boxHeight);
					}
					gc.setFill(textFill);
					gc.fillText(String.valueOf(ch), x + 0.25 * fontSize, y - 0.4 * fontSize);
					if (c == charactersBlock.getNchar()) {
						gc.setLineWidth(0.75);
						gc.setStroke(lineStroke);
						gc.strokeLine(x + boxWidth, y - boxHeight, x + boxWidth, y);
					}
					if (t == taxaBlock.getNtax()) {
						gc.setLineWidth(0.75);
						gc.setStroke(lineStroke);
						gc.strokeLine(x, y, x + boxWidth, y);
					}
				}
			}
		}
	}

	/**
	 * update the site selection visualization
	 */
	public static void updateSiteSelection(Canvas canvas, Group selectionGroup, TaxaBlock taxaBlock, CharactersBlock charactersBlock,
										   double boxWidth, double boxHeight, ScrollBar vScrollBar, NumberAxis axis, SelectionModel<Integer> siteSelectionModel) {
		selectionGroup.getChildren().clear();
		if (taxaBlock != null && charactersBlock != null) {
			var offset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvas.getHeight() - taxaBlock.getNtax() * boxHeight)) : 0;

			var height = Math.min(canvas.getHeight(), taxaBlock.getNtax() * boxHeight + offset);
			var left = (int) Math.max(1, Math.floor(axis.getLowerBound()));
			var right = Math.min(charactersBlock.getNchar(), Math.ceil(axis.getUpperBound()));

			var col = 0;
			for (var c = left; c <= right; c++) {
				var x = (col++) * boxWidth;
				if (siteSelectionModel.isSelected(c)) {
					var rectangle = new Rectangle(x - 10, -10, boxWidth, height);
					rectangle.setFill(Color.web("#039ED3").deriveColor(1, 1, 1, 0.4));
					rectangle.setStroke(Color.web("#039ED3"));
					selectionGroup.getChildren().add(rectangle);
				}
			}
		}
	}

	/**
	 * update the taxon selection visualization
	 */
	public static void updateTaxaSelection(Canvas canvas, Group selectionGroup, TaxaBlock taxaBlock, CharactersBlock charactersBlock,
										   double boxWidth, double boxHeight, ScrollBar vScrollBar, NumberAxis axis, SelectionModel<Taxon> taxonSelectionModel) {
		selectionGroup.getChildren().clear();
		if (taxaBlock != null && charactersBlock != null) {
			var offset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvas.getHeight() - taxaBlock.getNtax() * boxHeight)) : 0;

			var width = Math.min(canvas.getWidth(), (charactersBlock.getNchar() - axis.getLowerBound() + 1) * boxWidth);
			for (var t = 1; t <= taxaBlock.getNtax(); t++) {
				var y = t * boxHeight + offset;
				if (y < 0)
					continue;

				if (y > canvas.getHeight() + boxHeight)
					break;

				if (taxonSelectionModel.isSelected(taxaBlock.get(t))) {
					var rectangle = new Rectangle(0, y - boxHeight, width, boxHeight);
					rectangle.setFill(Color.web("#039ED3").deriveColor(1, 1, 1, 0.4));
					rectangle.setStroke(Color.web("#039ED3"));
					selectionGroup.getChildren().add(rectangle);
				}
			}
		}
	}
}
