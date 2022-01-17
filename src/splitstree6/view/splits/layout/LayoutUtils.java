/*
 *  LayoutUtils.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.splits.layout;

import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.shape.Shape;
import javafx.util.Duration;
import jloda.fx.util.BasicFX;
import jloda.fx.util.GeometryUtilsFX;
import splitstree6.view.trees.treepages.LayoutOrientation;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * layout utils
 * Daniel Huson, 1.2022
 */
public class LayoutUtils {
	public static void applyOrientation(Parent node, LayoutOrientation oldOrientation, LayoutOrientation newOrientation, Consumer<LayoutOrientation> orientationConsumer) {
		var transitions = new ArrayList<Transition>();

		BasicFX.preorderTraversal(node.getChildrenUnmodifiable().get(0), n -> {
			if (n instanceof Shape shape) {
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
		});
		var parallel = new ParallelTransition(transitions.toArray(new Transition[0]));
		if (orientationConsumer != null)
			parallel.setOnFinished(e -> {
				Platform.runLater(() -> orientationConsumer.accept(newOrientation));
			});
		parallel.play();
	}
}
