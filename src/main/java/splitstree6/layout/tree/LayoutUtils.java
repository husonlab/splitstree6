/*
 *  ScaleUtils.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.util.Duration;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.BasicFX;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloGraph;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;

import java.util.LinkedList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * some layout utilities
 * Daniel Huson, 12.2021
 */
public class LayoutUtils {
	public static final double MAX_FONT_SIZE = RichTextLabel.getDefaultFont().getSize();

	public static FontHeightGraphWidthHeight computeFontHeightGraphWidthHeight(int nTaxa, Function<Integer, StringProperty> taxonLabelMap, PhyloGraph graph, boolean radial, double width, double height) {
		double fontHeight;
		if (radial)
			fontHeight = Math.min(MAX_FONT_SIZE, 0.5 * Math.min(width, height) * Math.PI / (nTaxa + 1));
		else
			fontHeight = Math.min(MAX_FONT_SIZE, height / (nTaxa + 1));

		var maxLabelWidth = 0.0;
		NodeArray<RichTextLabel> nodeLabelMap = graph.newNodeArray();
		for (var v : graph.nodes()) {
			var label = getLabel(taxonLabelMap, graph, v);
			if (label != null) {
				label.setScale(fontHeight / RichTextLabel.getDefaultFont().getSize());
				label.applyCss();
				nodeLabelMap.put(v, label);

				// BasicFX.reportChanges(label.getRawText(),label.translateXProperty());
				// BasicFX.reportChanges(label.getRawText(),label.translateYProperty());

				maxLabelWidth = Math.max(maxLabelWidth, label.getEstimatedWidth());
			}
		}

		if (maxLabelWidth + fontHeight > 0.25 * width) {
			fontHeight = Math.min(MAX_FONT_SIZE, fontHeight * 0.25 * width / (maxLabelWidth + fontHeight));
			maxLabelWidth = 0;
			for (var label : nodeLabelMap.values()) {
				label.setScale(fontHeight / RichTextLabel.getDefaultFont().getSize());
				maxLabelWidth = Math.max(maxLabelWidth, label.getRawText().length() * 0.7 * fontHeight);
			}
		}
		var labelGap = fontHeight;

		final double normalizeWidth;
		final double normalizeHeight;

		if (radial) {
			if (maxLabelWidth > 100) {
				fontHeight *= 100 / maxLabelWidth;
				labelGap = fontHeight;
				maxLabelWidth = 100;
			}

			var tmp = Math.min(width - 2 * (maxLabelWidth + labelGap), height - 2 * (maxLabelWidth + labelGap));
			if (tmp > 20)
				tmp -= 10;
			else if (tmp < 0)
				tmp = 20;
			normalizeWidth = normalizeHeight = tmp;
		} else {
			normalizeWidth = width - maxLabelWidth - labelGap;
			normalizeHeight = height - fontHeight;
		}

		return new FontHeightGraphWidthHeight(fontHeight, normalizeWidth, normalizeHeight);
	}

	public static record FontHeightGraphWidthHeight(double fontHeight, double width, double height) {
	}

	public static double normalize(double width, double height, NodeArray<Point2D> nodePointMap, boolean maintainAspectRatio) {
		var minX = nodePointMap.values().parallelStream().mapToDouble(Point2D::getX).min().orElse(0);
		var maxX = nodePointMap.values().parallelStream().mapToDouble(Point2D::getX).max().orElse(0);
		var minY = nodePointMap.values().parallelStream().mapToDouble(Point2D::getY).min().orElse(0);
		var maxY = nodePointMap.values().parallelStream().mapToDouble(Point2D::getY).max().orElse(0);

		var scaleX = (maxX > minX ? width / (maxX - minX) : 1);
		var scaleY = (maxY > minY ? height / (maxY - minY) : 1);
		if (maintainAspectRatio) {
			scaleX = scaleY = Math.min(scaleX, scaleY);
		}
		if (scaleX != 1 || scaleY != 1) {
			for (var v : nodePointMap.keySet()) {
				var point = nodePointMap.get(v);
				nodePointMap.put(v, new Point2D(point.getX() * scaleX, point.getY() * scaleY));
			}
		}
		return scaleX;
	}

	public static RichTextLabel getLabel(Function<Integer, StringProperty> taxonLabelMap, PhyloGraph graph, Node v) {
		if (graph.getNumberOfTaxa(v) == 1) {
			var taxonId = graph.getTaxon(v);
			if (taxonId == -1) {
				var again = graph.getTaxon(v);
				System.err.println(StringUtils.toString(graph.getTaxa(v), " "));
				return null;
			}
			var textLabel = new RichTextLabel();
			textLabel.textProperty().bindBidirectional(taxonLabelMap.apply(taxonId));
			return textLabel;
		} else if (graph.getNumberOfTaxa(v) >= 2) {
			var label = StringUtils.toString(IteratorUtils.asStream(graph.getTaxa(v)).map(t -> taxonLabelMap.apply(t).getValue()).collect(Collectors.toList()), ",");
			return new RichTextLabel(label);
		} else if (v.getLabel() != null && !v.getLabel().isBlank()) {
			return new RichTextLabel(v.getLabel());
		} else if (graph.getLabel(v) != null && !graph.getLabel(v).isBlank()) {
			return new RichTextLabel(graph.getLabel(v));
		} else
			return null;
	}

	public static void applyLabelScaleFactor(Parent root, double factor) {
		if (factor != 0 && factor != 1) {
			var queue = new LinkedList<>(root.getChildrenUnmodifiable());
			while (!queue.isEmpty()) {
				var node = queue.pop();
				if (node instanceof RichTextLabel richTextLabel) {
					if (!"edge-label".equals(richTextLabel.getId()))
						richTextLabel.setScale(factor * richTextLabel.getScale());
				} else if (node instanceof Parent parent)
					queue.addAll(parent.getChildrenUnmodifiable());
			}
		}
	}

	/**
	 * update a change of orientation to a node
	 */
	public static void applyOrientation(String newOrientationLabel, javafx.scene.Node node, boolean keepLabelsUnrotated) {
		var newOrientation = LayoutOrientation.valueOf(newOrientationLabel);
		if (newOrientation.flip())
			node.setScaleX(-node.getScaleX());
		var angle = -newOrientation.angle();
		if (angle != 0.0) {
			node.setRotate(angle);
			if (keepLabelsUnrotated)
				rotateLabels(node, -angle);
		}
		ensureRichTextLabelsUpright(node);
	}

	public static void applyOrientation(javafx.scene.Node node, LayoutOrientation newOrientation, LayoutOrientation oldOrientation, Predicate<javafx.scene.Node> keepLabelUnrotated, BooleanProperty changingOrientation, boolean animate) {
		applyOrientation(node, newOrientation, oldOrientation, keepLabelUnrotated, changingOrientation, null, animate);
	}

	/**
	 * update a change of orientation to a node
	 */
	public static void applyOrientation(javafx.scene.Node node, LayoutOrientation newOrientation, LayoutOrientation oldOrientation, Predicate<javafx.scene.Node> keepLabelUnrotated, BooleanProperty changingOrientation, Runnable runAtFinished, boolean animate) {
		if (!changingOrientation.get()) {
			changingOrientation.set(true);
			final var angle0 = (oldOrientation != null ? oldOrientation.angle() : 0.0) - newOrientation.angle();
			var flip = (oldOrientation == null && newOrientation.flip() || oldOrientation != null && newOrientation.flip() != oldOrientation.flip());

			if (Math.abs(angle0) == 180 && flip) {
				var scaleTransition = new ScaleTransition(Duration.seconds(1));
				scaleTransition.setNode(node);
				var oldScaleY = node.getScaleY();
				scaleTransition.setToY(-oldScaleY);
				scaleTransition.setOnFinished(e -> {
					node.setScaleY(oldScaleY);
					node.setScaleX(-node.getScaleX());
					node.setRotate(node.getRotate() + angle0);

					for (var label : BasicFX.getAllRecursively(node, RichTextLabel.class)) {
						if (keepLabelUnrotated.test(label))
							label.setRotate(label.getRotate() + angle0);
					}
					ensureRichTextLabelsUpright(node);
					changingOrientation.set(false);
					if (runAtFinished != null)
						runAtFinished.run();
				});
				scaleTransition.play();
			} else {
				double angle;
				if (angle0 == 270)
					angle = -90;
				else if (angle0 == -270)
					angle = 90;
				else
					angle = angle0;


				if (animate) {
					var rotateTransition = new RotateTransition(Duration.seconds(1));
					rotateTransition.setNode(node);
					rotateTransition.setByAngle(angle);

					var scaleTransition = new ScaleTransition(Duration.seconds(1));
					scaleTransition.setNode(node);
					scaleTransition.setToX((flip ? -1 : 1) * node.getScaleX());

					var parallelTransition = new ParallelTransition(rotateTransition, scaleTransition);
					parallelTransition.play();
					parallelTransition.setOnFinished(e -> {
						for (var label : BasicFX.getAllRecursively(node, RichTextLabel.class)) {
							if (keepLabelUnrotated.test(label))
								label.setRotate(label.getRotate() + angle0);
						}
						ensureRichTextLabelsUpright(node);
						changingOrientation.set(false);
						if (runAtFinished != null)
							runAtFinished.run();
					});
				} else {
					if (angle != 0)
						node.setRotate(node.getRotate() + angle);
					if (flip)
						node.setScaleX(-1 * node.getScaleX());
					for (var label : BasicFX.getAllRecursively(node, RichTextLabel.class)) {
						if (keepLabelUnrotated.test(label))
							label.setRotate(label.getRotate() + angle);
					}
					ensureRichTextLabelsUpright(node);
					changingOrientation.set(false);
					if (runAtFinished != null)
						runAtFinished.run();
				}
			}
		}
	}

	public static void ensureRichTextLabelsUpright(javafx.scene.Node node) {
		for (var label : BasicFX.getAllRecursively(node, RichTextLabel.class)) {
			label.ensureUpright();
		}
	}

	public static void rotateLabels(javafx.scene.Node node, double angle) {
		for (var label : BasicFX.getAllRecursively(node, RichTextLabel.class)) {
			label.setRotate(label.getRotate() + angle);
		}
	}
}
