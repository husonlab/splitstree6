/*
 *  LayoutUtils.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.layout;

import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.util.Duration;
import jloda.fx.util.GeometryUtilsFX;
import splitstree6.layout.tree.LayoutOrientation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * layout utils
 * Daniel Huson, 1.2022
 */
public class LayoutUtils {
	private static double mouseX;
	private static double mouseY;

	public static void applyOrientation(Collection<? extends Node> shapes, LayoutOrientation oldOrientation, LayoutOrientation newOrientation,
										Consumer<LayoutOrientation> orientationConsumer,
										BooleanProperty changingOrientation) {
		if (!changingOrientation.get()) {
			changingOrientation.set(true);

			var transitions = new ArrayList<Transition>();

			for (var shape : shapes) {
				var translate = new TranslateTransition(Duration.seconds(1));
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

	public static void installTranslateUsingLayout(javafx.scene.Node node, Runnable select) {
		node.setOnMousePressed(e -> {
			mouseX = e.getSceneX();
			mouseY = e.getSceneY();
			select.run();
			e.consume();
		});

		node.setOnMouseDragged(e -> {
			node.setLayoutX(node.getLayoutX() + (e.getSceneX() - mouseX));
			node.setLayoutY(node.getLayoutY() + (e.getSceneY() - mouseY));
			mouseX = e.getSceneX();
			mouseY = e.getSceneY();
			e.consume();
		});
	}
}
