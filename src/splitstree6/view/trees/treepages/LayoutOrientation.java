/*
 *  LayoutOrientation.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import jloda.fx.util.GeometryUtilsFX;

/**
 * tree or network layout orientation
 * Daniel Huson,12.2021
 */
public enum LayoutOrientation {
	Rotate0Deg, Rotate90Deg, Rotate180Deg, Rotate270Deg, FlipRotate0Deg, FlipRotate90Deg, FlipRotate180Deg, FlipRotate270Deg;

	public boolean isWidthHeightSwitched() {
		return this == Rotate90Deg || this == FlipRotate90Deg || this == Rotate270Deg || this == FlipRotate270Deg;
	}

	public double angle() {
		return switch (this) {
			case Rotate0Deg -> 0;
			case Rotate90Deg -> 90;
			case Rotate180Deg -> 180;
			case Rotate270Deg -> 270;
			case FlipRotate0Deg -> 0;
			case FlipRotate90Deg -> 90;
			case FlipRotate180Deg -> 180;
			case FlipRotate270Deg -> 270;
		};
	}

	public boolean flip() {
		return switch (this) {
			case Rotate0Deg -> false;
			case Rotate90Deg -> false;
			case Rotate180Deg -> false;
			case Rotate270Deg -> false;
			case FlipRotate0Deg -> true;
			case FlipRotate90Deg -> true;
			case FlipRotate180Deg -> true;
			case FlipRotate270Deg -> true;
		};
	}

	public Label createLabel() {
		var label = new Label("R");
		apply(label);
		return label;
	}

	public Point2D apply(Point2D point) {
		return switch (this) {
			case Rotate0Deg -> point;
			case Rotate90Deg -> GeometryUtilsFX.rotate(point, -90);
			case Rotate180Deg -> GeometryUtilsFX.rotate(point, -180);
			case Rotate270Deg -> GeometryUtilsFX.rotate(point, -270);
			case FlipRotate0Deg -> new Point2D(-point.getX(), point.getY());
			case FlipRotate90Deg -> GeometryUtilsFX.rotate(-point.getX(), point.getY(), -90);
			case FlipRotate180Deg -> GeometryUtilsFX.rotate(-point.getX(), point.getY(), -180);
			case FlipRotate270Deg -> GeometryUtilsFX.rotate(-point.getX(), point.getY(), -270);
		};
	}

	public double apply(double angle) {
		return switch (this) {
			case Rotate0Deg -> angle;
			case Rotate90Deg -> GeometryUtilsFX.modulo360(angle - 90);
			case Rotate180Deg -> GeometryUtilsFX.modulo360(angle + 180);
			case Rotate270Deg -> GeometryUtilsFX.modulo360(angle - 270);
			case FlipRotate0Deg -> GeometryUtilsFX.modulo360(180 - angle);
			case FlipRotate90Deg -> GeometryUtilsFX.modulo360(90 - angle);
			case FlipRotate180Deg -> GeometryUtilsFX.modulo360(360 - angle);
			case FlipRotate270Deg -> GeometryUtilsFX.modulo360(-90 - angle);
		};
	}

	public void apply(Node node) {
		switch (this) {
			case Rotate90Deg -> node.setRotate(-90);
			case Rotate180Deg -> node.setRotate(-180);
			case Rotate270Deg -> node.setRotate(-270);
			case FlipRotate0Deg -> node.setScaleX(-node.getScaleX());
			case FlipRotate90Deg -> {
				node.setScaleX(-node.getScaleX());
				node.setRotate(-90);
			}
			case FlipRotate180Deg -> {
				node.setScaleX(-node.getScaleX());
				node.setRotate(-180);
			}
			case FlipRotate270Deg -> {
				node.setScaleX(-node.getScaleX());
				node.setRotate(-270);
			}
		}
	}
}
