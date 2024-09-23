/*
 *  LabelUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.geometry.Point2D;

public class LabelUtils {
	private static double mouseDownX;
	private static double mouseDownY;
	private static double mouseX;
	private static double mouseY;
	private static boolean wasDragged;

	public static void makeDraggable(jloda.graph.Node v, javafx.scene.Node label, DrawPane drawPane) {
		label.setOnMousePressed(e -> {
			mouseDownX = e.getScreenX();
			mouseDownY = e.getScreenY();
			mouseX = e.getScreenX();
			mouseY = e.getScreenY();
			wasDragged = false;
			e.consume();

		});
		label.setOnMouseDragged(e -> {
			var previous = drawPane.screenToLocal(mouseX, mouseY);
			var current = drawPane.screenToLocal(e.getScreenX(), e.getScreenY());
			var delta = new Point2D(current.getX() - previous.getX(), current.getY() - previous.getY());
			label.setLayoutX(label.getLayoutX() + delta.getX());
			label.setLayoutY(label.getLayoutY() + delta.getY());
			wasDragged = true;
			mouseX = e.getScreenX();
			mouseY = e.getScreenY();
			e.consume();
		});
		label.setOnMouseReleased(e -> {
			if (wasDragged) {
				drawPane.getUndoManager().add("move label", () -> {
					var previous = drawPane.screenToLocal(mouseDownX, mouseDownY);
					var current = drawPane.screenToLocal(e.getScreenX(), e.getScreenY());
					var delta = new Point2D(current.getX() - previous.getX(), current.getY() - previous.getY());

					label.setLayoutX(label.getLayoutX() - delta.getX());
					label.setLayoutY(label.getLayoutY() - delta.getY());
				}, () -> {
					var previous = drawPane.screenToLocal(mouseDownX, mouseDownY);
					var current = drawPane.screenToLocal(e.getScreenX(), e.getScreenY());
					var delta = new Point2D(current.getX() - previous.getX(), current.getY() - previous.getY());
					label.setLayoutX(label.getLayoutX() + delta.getX());
					label.setLayoutY(label.getLayoutY() + delta.getY());
				});
			}
			e.consume();
		});
	}

}
