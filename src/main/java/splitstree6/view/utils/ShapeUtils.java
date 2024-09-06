/*
 *  ShapeUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.utils;

import javafx.geometry.Point2D;
import javafx.scene.shape.Shape;

import java.util.Collection;


public class ShapeUtils {
	/**
	 * gets the shapes center in local coordinates
	 *
	 * @param shape shape
	 * @return center in local coordinates
	 */
	public static Point2D getCenter(Shape shape) {
		var bounds = shape.getBoundsInLocal();
		double localCenterX = (bounds.getMinX() + bounds.getMaxX()) / 2;
		double localCenterY = (bounds.getMinY() + bounds.getMaxY()) / 2;
		return new Point2D(localCenterX, localCenterY);
	}

	/**
	 * gets the shapes center in screen coordinates
	 *
	 * @param shape shape
	 * @return center in screen coordinates
	 */
	public static Point2D getCenterScreenCoordinates(Shape shape) {
		return shape.localToScreen(getCenter(shape));
	}

	public static Point2D getCenterScreenCoordinates(Collection<Shape> shapes) {
		if (shapes.isEmpty())
			return new Point2D(0, 0);
		else {
			double x = 0;
			double y = 0;
			for (var shape : shapes) {
				var center = getCenterScreenCoordinates(shape);
				x += center.getX();
				y += center.getY();
			}
			return new Point2D(x / shapes.size(), y / shapes.size());
		}
	}
}
