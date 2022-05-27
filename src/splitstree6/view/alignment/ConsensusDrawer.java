/*
 *  ConsensusDrawer.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import jloda.fx.util.AService;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.util.BitSet;

public class ConsensusDrawer {
	private final Group canvasGroup;

	private final AService<Canvas> service;

	public ConsensusDrawer(Group canvasGroup, Pane bottomPane) {
		this.canvasGroup = canvasGroup;
		service = new AService<>(bottomPane);
	}

	/**
	 * update the drawing
	 */
	public void updateDrawing(double canvasWidth, double canvasHeight, TaxaBlock inputTaxa, CharactersBlock inputCharacters,
							  char[] consensusSequence, ColorScheme colorScheme,
							  double boxHeight, ScrollBar vScrollBar, NumberAxis axis, BitSet activeSites) {

		if (inputTaxa != null && inputCharacters != null) {
			var axisLowerBound = axis.getLowerBound();
			var axisStartOffset = 7;
			var boxWidth = (axis.getWidth()) / (axis.getUpperBound() - axisLowerBound);

			var fontSize = 0.9 * Math.min(boxWidth, boxHeight);
			var showColors = (colorScheme != ColorScheme.None);

			var lineStroke = MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;
			var textFill = !showColors && MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;

			var vOffset = vScrollBar.isVisible() ? (vScrollBar.getValue() * (canvasHeight - inputTaxa.getNtax() * boxHeight)) : 0;

			var left = Math.max(1, (int) axis.getLowerBound() - 1);
			var right = Math.min(inputCharacters.getNchar(), Math.ceil(axis.getUpperBound()));

			service.setCallable(() -> {
				var progress = service.getProgressListener();
				progress.setTasks("Drawing", "consensus");

				var canvas = new Canvas(canvasWidth, boxHeight);
				var gc = canvas.getGraphicsContext2D();
				gc.setFont(Font.font("monospaced", fontSize));
				gc.setLineWidth(0.75);
				gc.setStroke(lineStroke);

				progress.setMaximum(inputTaxa.getNtax());
				progress.setProgress(0);

				if (boxWidth <= 0.2 || boxHeight <= 0.2) { // if too small to see, draw one gray box and leave
					var xleft = (left - axisLowerBound) * boxWidth + axisStartOffset;
					var xright = (right - axisLowerBound) * boxWidth + axisStartOffset;
					gc.setFill(Color.LIGHTGRAY);

					gc.fillRect(xleft, 0, xright - xleft, boxHeight);
				} else {

					for (var site = left; site <= right; site++) {
						var consensusCharacter = (consensusSequence == null ? ' ' : consensusSequence[site]);

						var x = (site - axisLowerBound) * boxWidth + axisStartOffset;
						if (!activeSites.get(site)) {
							gc.setFill(Color.TRANSPARENT);
							gc.fillRect(x, 0, boxWidth, boxHeight);
							gc.setFill(MainWindowManager.isUseDarkTheme() ? Color.web("0x6F6F6F") : Color.LIGHTGRAY);
						} else if (showColors) {
							gc.setFill(colorScheme.apply(consensusCharacter));
							gc.fillRect(x, 0, boxWidth, boxHeight);
							gc.setFill(textFill);

						} else {
							gc.setFill(textFill);
						}

						if (fontSize >= 2) {
							gc.fillText(String.valueOf(consensusCharacter), x + 0.5 * (boxWidth - fontSize), 0.5 * boxHeight);
						}

						if (site == 1) {
							gc.strokeLine(x, 0, x, boxHeight);
						}
						gc.strokeLine(x, 0, x + boxWidth, boxHeight);

						if (site == inputCharacters.getNchar()) {
							gc.strokeLine(x + boxWidth, 0, x + boxWidth, boxHeight);
						}
					}
				}
				return canvas;
			});
			service.setOnRunning(e -> {
				var oldCanvas = getCanvas();
				RunAfterAWhile.apply(new Object(), () -> oldCanvas.setOpacity(0.5));
			});
			service.setOnFailed(e -> NotificationManager.showError("Draw consensus failed: " + service.getException()));
			service.setOnSucceeded(e -> canvasGroup.getChildren().setAll(service.getValue()));
			service.restart();
		}

	}

	private Canvas getCanvas() {
		return (Canvas) canvasGroup.getChildren().get(0);
	}
}
