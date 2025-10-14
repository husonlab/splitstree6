/*
 *  LayoutOrientation.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.tree;

import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;
import jloda.fx.util.GeometryUtilsFX;
import jloda.util.NumberUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * tree or network layout orientation
 * Daniel Huson,12.2021
 */
public class LayoutOrientation {
	public static final String Rotate0DegString = "Rotate0Deg";

	public static final LayoutOrientation Rotate0Deg = new LayoutOrientation(false, 0);

	private boolean flip;

	private double alpha;

	public LayoutOrientation() {
	}

	public LayoutOrientation(boolean flip, double alpha) {
		this.flip = flip;
		this.alpha = alpha;
	}

	public boolean isWidthHeightSwitched() {
		return alpha == 90 || alpha == 270;
	}

	public double angle() {
		return alpha;
	}

	public boolean flip() {
		return flip;
	}

	public Label createLabel() {
		var label = new Label("R");
		apply(label);
		return label;
	}

	public Point2D apply(Point2D point) {
		if (!flip) {
			if (alpha == 0)
				return point;
			else return GeometryUtilsFX.rotate(point, -alpha);
		} else {
			if (alpha == 0)
				return new Point2D(-point.getX(), point.getY());
			else return GeometryUtilsFX.rotate(-point.getX(), point.getY(), -alpha);
		}
	}

	public double apply(double angle) {
		if (!flip) {
			if (angle == 0)
				return 0;
			else
				return GeometryUtilsFX.modulo360(angle - alpha);

		} else {
			if (angle == 0) return 0;
			else
				return GeometryUtilsFX.modulo360(180 - angle - alpha);
		}
	}

	public void apply(Node node) {
		if (flip)
			node.setScaleX(-node.getScaleX());
		if (alpha != 0)
			node.setRotate(alpha);
	}

	public LayoutOrientation getRotateLeft(int angle) {
		var newAngle = GeometryUtilsFX.modulo360(alpha + angle);
		if (newAngle == 360)
			newAngle = 0.0;
		return new LayoutOrientation(flip, newAngle);
	}

	public LayoutOrientation getRotateRight(int angle) {
		var newAngle = GeometryUtilsFX.modulo360(alpha - angle);
		if (newAngle == 360)
			newAngle = 0.0;
		return new LayoutOrientation(flip, newAngle);
	}

	public LayoutOrientation getFlipHorizontal() {
		var newAngle = GeometryUtilsFX.modulo360(360 - alpha);
		if (newAngle == 360)
			newAngle = 0.0;
		return new LayoutOrientation(!flip, newAngle);
	}

	public LayoutOrientation getFlipVertical() {
		var newAngle = GeometryUtilsFX.modulo360(180 + alpha);
		if (newAngle == 360)
			newAngle = 0.0;
		return new LayoutOrientation(!flip, newAngle);
	}

	public static void applyOrientation(Collection<? extends Node> shapes, String oldOrientationLabel, String newOrientationLabel,
										Consumer<LayoutOrientation> orientationConsumer,
										BooleanProperty changingOrientation) {
		if (!changingOrientation.get()) {
			var oldOrientation = LayoutOrientation.valueOf(oldOrientationLabel);
			var newOrientation = LayoutOrientation.valueOf(newOrientationLabel);

			changingOrientation.set(true);

			var transitions = new ArrayList<Transition>();

			for (var shape : shapes) {
				var translate = new TranslateTransition();
				translate.setNode(shape);
				var point = new Point2D(shape.getTranslateX(), shape.getTranslateY());

				if (oldOrientation.angle() != 0)
					point = GeometryUtilsFX.rotate(point, oldOrientation.angle());
				if (oldOrientation.flip())
					point = new Point2D(-point.getX(), point.getY());

				if (newOrientation.flip())
					point = new Point2D(-point.getX(), point.getY());
				if (newOrientation.angle() != 0)
					point = GeometryUtilsFX.rotate(point, -newOrientation.angle());

				var arc = Math.abs(newOrientation.angle() - oldOrientation.angle());
				if (arc >= 180)
					arc = 360 - arc;
				if (oldOrientation.flip() != newOrientation.flip() || arc > 10)
					translate.setDuration(Duration.seconds(1.0));
				else
					translate.setDuration(Duration.millis(1));

				translate.setToX(point.getX());
				translate.setToY(point.getY());
				transitions.add(translate);
			}
			var parallel = new ParallelTransition(transitions.toArray(new Transition[0]));
			if (orientationConsumer != null)
				parallel.setOnFinished(e -> Platform.runLater(() -> {
					orientationConsumer.accept(newOrientation);
					changingOrientation.set(false);
				}));
			parallel.play();
		}
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof LayoutOrientation other && flip == other.flip && alpha == other.alpha;
	}

	public String toString() {
		if (!flip)
			return "Rotate%.0fDeg".formatted(alpha);
		else
			return "FlipRotate%.0fDeg".formatted(alpha);
	}

	public static LayoutOrientation valueOf(String string) {
		var item = new LayoutOrientation();
		if (string != null) {
			var matcher = Pattern.compile("\\d+").matcher(string);
			if (matcher.find())
				item.alpha = NumberUtils.parseDouble(matcher.group());
			else
				item.alpha = 0.0;
			item.alpha = GeometryUtilsFX.modulo360(item.alpha);
			if (item.alpha == 360)
				item.alpha = 0.0;
			item.flip = string.startsWith("Flip");
		}
		return item;
	}
}
