/*
 *  AlignmentDrawer.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.ScrollBar;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import jloda.fx.util.AService;
import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.util.BitSet;

/**
 * draw the alignment and indicate selection
 * Daniel Huson, 4.2022
 */
public class AlignmentDrawer {
	private final ObjectProperty<Canvas> canvas = new SimpleObjectProperty<>(this, "canvas");
	private final Group imageGroup;

	private final AService<WritableImage> service;

	public AlignmentDrawer(Group imageGroup, Group canvasGroup, Pane bottomPane) {
		this.imageGroup = imageGroup;
		canvas.set((Canvas) canvasGroup.getChildren().get(0));
		canvas.addListener((c, o, n) -> canvasGroup.getChildren().setAll(n));
		service = new AService<>(bottomPane);
	}

	/**
	 * update the canvas
	 */
	public void updateCanvas(double canvasWidth, double canvasHeight, TaxaBlock inputTaxa, CharactersBlock inputCharacters,
							 char[] consensusSequence, ColorScheme colorScheme,
							 double boxHeight, ScrollBar vScrollBar, NumberAxis axis, BitSet activateTaxa, BitSet activeSites) {

		if (inputTaxa != null && inputCharacters != null && canvasWidth > 0 && canvasHeight > 0) {
			var axisLowerBound = axis.getLowerBound();
			var axisStartOffset = 7;
			var boxWidth = (axis.getWidth()) / (axis.getUpperBound() - axisLowerBound);

			var fontSize = 0.9 * Math.min(boxWidth, boxHeight);
			var showColors = (colorScheme != ColorScheme.None);

			var lineStroke = MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;
			var textFill = !showColors && MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;
			var notActiveFill = (MainWindowManager.isUseDarkTheme() ? Color.web("0x6F6F6F") : Color.LIGHTGRAY);

			var vOffset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvasHeight - inputTaxa.getNtax() * boxHeight)) : 0;

			var left = Math.max(1, (int) axis.getLowerBound() - 1);
			var right = Math.min(inputCharacters.getNchar(), Math.ceil(axis.getUpperBound()));


			imageGroup.getChildren().clear();

			service.setCallable(() -> {
				var image = new WritableImage((int) canvasWidth, (int) canvasHeight);
				var progress = service.getProgressListener();
				progress.setTasks("Drawing", "alignment");

				var colors = new Color[256];

				progress.setMaximum(inputTaxa.getNtax());
				progress.setProgress(0);
				for (var t = 1; t <= inputTaxa.getNtax(); t++) {
					progress.incrementProgress();

					var tNotActive = !activateTaxa.get(t);
					var chars = inputCharacters.getMatrix()[t - 1];

					var y = t * boxHeight + vOffset;
					if (y < 0)
						continue;
					if (y > image.getHeight() + boxHeight)
						break;

					if (boxWidth <= 0.2 || boxHeight <= 0.2) { // if too small to see, draw one gray box and leave
						var xleft = (left - axisLowerBound) * boxWidth + axisStartOffset;
						var xright = (right - axisLowerBound) * boxWidth + axisStartOffset;

						var totalHeight = boxHeight;
						for (var s = t; s <= inputTaxa.getNtax(); s++) {
							if (y + totalHeight > image.getHeight() + boxHeight)
								break;
							totalHeight += boxHeight;
						}
						BasicFX.fillRectangle(image, xleft, y - boxHeight, xright - xleft, totalHeight, Color.LIGHTGRAY);
						break;
					}

					for (var site = left; site <= right; site++) {
						var ch = chars[site - 1];
						var x = (site - axisLowerBound) * boxWidth + axisStartOffset;

						if (showColors) {
							Color color;
							if (tNotActive || !activeSites.get(site))
								color = notActiveFill;
							else {
								color = colors[ch];
								if (color == null) {
									color = colors[ch] = colorScheme.apply(ch);
								}
							}
							BasicFX.fillRectangle(image, x, y - boxHeight, boxWidth, boxHeight, color);
						}

						if (site == 1) {
							BasicFX.fillRectangle(image, x, y - boxHeight, 1, boxHeight, Color.DARKGRAY);
						}
						if (t == inputTaxa.getNtax()) {
							BasicFX.fillRectangle(image, x, y, boxWidth, 1, Color.DARKGRAY);
						}
						if (site == chars.length) {
							BasicFX.fillRectangle(image, x + boxWidth, y - boxHeight, 1, boxHeight, Color.DARKGRAY);
						}
					}
				}
				return image;
			});
			service.setOnFailed(e -> NotificationManager.showError("Draw alignment failed: " + service.getException()));
			service.setOnSucceeded(e -> {
				imageGroup.getChildren().setAll(new ImageView(service.getValue()));
			});
			service.restart();

			{
				var canvas = getCanvas();
				var gc = canvas.getGraphicsContext2D();
				gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
				canvas.setWidth((int) canvasWidth);
				canvas.setHeight((int) canvasHeight);

				if (fontSize > 2) {
					gc.setFont(Font.font("monospaced", fontSize));
					gc.setLineWidth(0.75);
					gc.setStroke(lineStroke);

					for (var t = 1; t <= inputTaxa.getNtax(); t++) {
						var tNotActive = !activateTaxa.get(t);
						var chars = inputCharacters.getMatrix()[t - 1];

						var y = t * boxHeight + vOffset;
						if (y < 0)
							continue;
						if (y > canvas.getHeight() + boxHeight)
							break;

						for (var site = left; site <= right; site++) {
							var ch = chars[site - 1];
							var x = (site - axisLowerBound) * boxWidth + axisStartOffset;
							if (tNotActive || !activeSites.get(site)) {
								gc.setFill(notActiveFill.darker());
							} else
								gc.setFill(textFill);
							gc.fillText(String.valueOf(ch), x + 0.5 * (boxWidth - fontSize), y - 0.5 * boxHeight);
						}
					}
				}
			}

		}
	}

	public Canvas getCanvas() {
		return canvas.get();
	}

	public ReadOnlyObjectProperty<Canvas> canvasProperty() {
		return canvas;
	}

	private void setCanvas(Canvas canvas) {
		this.canvas.set(canvas);
	}

	public final static Color SELECTION_FILL = Color.web("#039ED3").deriveColor(1, 1, 1, 0.4);
	public final static Color SELECTION_STROKE = (Color.web("#039ED3"));

	/**
	 * update the site selection visualization
	 */
	public void updateSiteSelection(Group selectionGroup, TaxaBlock inputTaxa, CharactersBlock inputCharacters,
									double boxHeight, ScrollBar vScrollBar, NumberAxis axis, BitSet selectedSites) {
		var canvas = getCanvas();
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
	public void updateTaxaSelection(Group selectionGroup, TaxaBlock inputTaxa, CharactersBlock inputCharacters,
									double boxHeight, ScrollBar vScrollBar, NumberAxis axis, BitSet selectedTaxa) {
		var canvas = getCanvas();
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

	public void close() {
		service.cancel();
	}
}
