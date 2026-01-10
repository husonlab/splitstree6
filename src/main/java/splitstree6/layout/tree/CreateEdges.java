/*
 * CreateEdges.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.layout.tree;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * creates edges
 * Daniel Huson, 1.2025
 */
public class CreateEdges {
	public enum Type {Straight, Circular, Rectangular}

	private static final Point2D origin = new Point2D(0, 0);

	public static void apply(PhyloTree tree, Map<Node, LabeledNodeShape> nodeShapeMap, Map<Edge, LabeledEdgeShape> edgeShapeMap, Type type) {
		for (var e : tree.edges()) {
			var sourceShape = nodeShapeMap.get(e.getSource());
			var targetShape = nodeShapeMap.get(e.getTarget());
			var label = (tree.getLabel(e) != null ? new RichTextLabel(tree.getLabel(e)) : null);
			var path = switch (type) {
				case Straight -> setupStraightPath(tree, e, sourceShape, targetShape, label);
				case Circular -> setupCircularPath(tree, e, sourceShape, targetShape, label);
				case Rectangular -> setupRectangularPath(tree, e, sourceShape, targetShape, label);
			};
			edgeShapeMap.put(e, new LabeledEdgeShape(label, path));
		}
	}

	/**
	 * setup straight edges
	 *
	 * @param tree        tree
	 * @param e           edge
	 * @param sourceShape source shape
	 * @param targetShape target shape
	 * @param label       label
	 * @return path
	 */
	private static Path setupStraightPath(PhyloTree tree, Edge e, LabeledNodeShape sourceShape, LabeledNodeShape targetShape, RichTextLabel label) {
		var path = new Path();
		path.setFill(Color.TRANSPARENT);
		path.setStrokeLineCap(StrokeLineCap.ROUND);
		path.setStrokeWidth(1);
		path.setPickOnBounds(false);

		var moveTo = new MoveTo();
		var lineTo = new LineTo();

		moveTo.setX(sourceShape.getTranslateX());
		moveTo.setY(sourceShape.getTranslateY());
		lineTo.setX(targetShape.getTranslateX());
		lineTo.setY(targetShape.getTranslateY());
		path.getElements().addAll(moveTo, lineTo);

		if (tree.isTreeEdge(e))
			path.getStyleClass().add("graph-edge");
		else
			path.getStyleClass().add("graph-special-edge");

		if (tree.isTransferEdge(e))
			addArrowHead(path, moveTo, lineTo);

		if (label != null) {
			if (!tree.isTreeEdge(e))
				label.setTextFill(Color.DARKORANGE);
			label.translateXProperty().bind((sourceShape.translateXProperty().add(targetShape.translateXProperty())).multiply(0.5));
			label.translateYProperty().bind(((sourceShape.translateYProperty().add(targetShape.translateYProperty())).multiply(0.5)).subtract(15));
		}
		return path;
	}

	/**
	 * setup circular edges
	 *
	 * @param tree        tree
	 * @param e           edge
	 * @param sourceShape source shape
	 * @param targetShape target shape
	 * @param label       label
	 * @return path
	 */
	private static Path setupCircularPath(PhyloTree tree, Edge e, LabeledNodeShape sourceShape, LabeledNodeShape targetShape, RichTextLabel label) {
		var source = new Point2D(sourceShape.getTranslateX(), sourceShape.getTranslateY());
		var sMagnitude = source.magnitude();
		var target = new Point2D(targetShape.getTranslateX(), targetShape.getTranslateY());
		var tMagnitude = target.magnitude();

		var path = new Path();
		path.setPickOnBounds(false);

		if (tree.isTreeEdge(e) || tree.isTransferAcceptorEdge(e)) {
			path.getStyleClass().add("graph-edge");

			path.getElements().add(new MoveTo(source.getX(), source.getY()));

			if (sMagnitude > 0 && tMagnitude > 0) {
				var corner = target.multiply(sMagnitude / tMagnitude);

				var arcTo = new ArcTo();
				arcTo.setX(corner.getX());
				arcTo.setY(corner.getY());
				arcTo.setRadiusX(sMagnitude);
				arcTo.setRadiusY(sMagnitude);
				arcTo.setLargeArcFlag(GeometryUtilsFX.computeObservedAngle(origin, source, target) > 180);
				arcTo.setSweepFlag(GeometryUtilsFX.computeObservedAngle(origin, source, target) > 0);

				path.getElements().add(arcTo);
			}
			path.getElements().add(new LineTo(target.getX(), target.getY()));

			if (label != null) {
				final AtomicBoolean pending = new AtomicBoolean(false);

				final InvalidationListener listener = obs -> {
					if (pending.compareAndSet(false, true)) {
						Platform.runLater(() -> {
							pending.set(false);
							var sm = GeometryUtilsFX.magnitude(sourceShape.getTranslateX(), sourceShape.getTranslateY());
							var t = new Point2D(targetShape.getTranslateX(), targetShape.getTranslateY());
							var tm = t.magnitude();
							var corner = t.multiply(tm == 0 ? 1 : sm / tm);
							label.setTranslateX(0.5 * (corner.getX() + t.getX()));
							label.setTranslateY(0.5 * (corner.getY() + t.getY()));

						});
					}
				};
				listener.invalidated(null);


				//sourceShape.translateXProperty().addListener(listener);
				//sourceShape.translateYProperty().addListener(listener);
				targetShape.translateXProperty().addListener(listener);
				targetShape.translateYProperty().addListener(listener);
			}
		} else {
			path.getStyleClass().add("graph-special-edge");

			var moveTo = new MoveTo(source.getX(), source.getY());
			var lineTo = new LineTo(target.getX(), target.getY());
			path.getElements().addAll(moveTo, lineTo);
			if (tree.isTransferEdge(e))
				addArrowHead(path, moveTo, lineTo);

			if (label != null) {
				label.setTextFill(Color.DARKORANGE);
				label.translateXProperty().bind((sourceShape.translateXProperty().add(targetShape.translateXProperty())).multiply(0.5));
				label.translateYProperty().bind(((sourceShape.translateYProperty().add(targetShape.translateYProperty())).multiply(0.5)).subtract(15));
			}
		}
		return path;
	}

	/**
	 * setup rectangular edges
	 *
	 * @param tree        tree
	 * @param e           edge
	 * @param sourceShape source shape
	 * @param targetShape target shape
	 * @param label       label
	 * @return path
	 */
	private static Path setupRectangularPath(PhyloTree tree, Edge e, LabeledNodeShape sourceShape, LabeledNodeShape targetShape, RichTextLabel label) {
		var sourceX = sourceShape.getTranslateX();
		var sourceY = sourceShape.getTranslateY();
		var targetX = targetShape.getTranslateX();
		var targetY = targetShape.getTranslateY();

		var path = new Path();
		path.setPickOnBounds(false);

		var moveTo = new MoveTo();
		moveTo.setX(sourceX);
		moveTo.setY(sourceY);

		path.getElements().add(moveTo);

		if (tree.isTreeEdge(e) || tree.isTransferAcceptorEdge(e)) {
			path.getStyleClass().add("graph-edge");

			var lineTo1 = new LineTo();
			path.getElements().add(lineTo1);

			var dx = targetX - sourceX;
			var dy = targetY - sourceY;

			if (Math.abs(dx) <= 5 || Math.abs(dy) <= 5) {
				lineTo1.setX(sourceX);
				lineTo1.setY(targetY);
			} else {
				lineTo1.setX(sourceX);
				lineTo1.setY(sourceY + dy + (dy > 0 ? -4 : 4));

				var quadTo = new QuadCurveTo();
				path.getElements().add(quadTo);
				quadTo.setControlX(sourceX);
				quadTo.setControlY(targetY);
				quadTo.setX(sourceX + (dx > 0 ? +4 : -4));
				quadTo.setY(targetY);
			}

			var lineTo2 = new LineTo();
			path.getElements().add(lineTo2);
			lineTo2.setX(targetX);
			lineTo2.setY(targetY);

			if (label != null) {
				label.translateXProperty().bind((sourceShape.translateXProperty().add(targetShape.translateXProperty())).multiply(0.5));
				label.translateYProperty().bind(targetShape.translateYProperty().subtract(18));
			}
		} else if (tree.isTransferEdge(e)) {
			var lineTo1 = new LineTo();
			path.getElements().add(lineTo1);
			path.getStyleClass().add("graph-special-edge");

			lineTo1.setX(targetX);
			lineTo1.setY(targetY);
			addArrowHead(path, moveTo, lineTo1);

			if (label != null) {
				label.setTextFill(Color.DARKORANGE);
				label.translateXProperty().bind((sourceShape.translateXProperty().add(targetShape.translateXProperty())).multiply(0.5));
				label.translateYProperty().bind(((sourceShape.translateYProperty().add(targetShape.translateYProperty())).multiply(0.5)).subtract(15));

			}
		} else { // tree.isReticulateEdge(e)
			path.getStyleClass().add("graph-special-edge");
			var quadCurveTo = new QuadCurveTo();
			path.getElements().add(quadCurveTo);
			quadCurveTo.setControlX(sourceX);
			quadCurveTo.setControlY(targetY);
			quadCurveTo.setX(targetX);
			quadCurveTo.setY(targetY);
			if (label != null) {
				label.setTextFill(Color.DARKORANGE);
				label.translateXProperty().bind((sourceShape.translateXProperty().add(targetShape.translateXProperty())).multiply(0.5));
				label.translateYProperty().bind(Bindings.createDoubleBinding(() -> targetShape.getTranslateY() + (
								sourceShape.getTranslateY() < targetShape.getTranslateY() ? -18 : 18),
						targetShape.translateYProperty(), sourceShape.translateYProperty()));
			}
		}
		return path;
	}

	public static void addArrowHead(Path path, MoveTo moveto, LineTo lineTo) {
		var radian = GeometryUtilsFX.deg2rad(GeometryUtilsFX.computeAngle(lineTo.getX() - moveto.getX(), lineTo.getY() - moveto.getY()));
		var dx = 10 * Math.cos(radian);
		var dy = 10 * Math.sin(radian);

		var head = new Point2D(lineTo.getX(), lineTo.getY());
		var one = head.add(-dx - dy, dx - dy);
		var two = head.add(-dx + dy, -dx - dy);

		path.getElements().add(new LineTo(one.getX(), one.getY()));
		path.getElements().add(new MoveTo(head.getX(), head.getY()));
		path.getElements().add(new LineTo(two.getX(), two.getY()));
	}


}
