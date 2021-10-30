/*
 * WorkflowEdgeItem.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.tabs.workflow;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import jloda.fx.util.GrayOutlineEffect;

/**
 * workflow edge view
 * Daniel Huson, 1.2017
 */
public class WorkflowEdgeItem extends Group {
	private final Pane source;
	private final Pane target;
	private final Line part1 = new Line();
	private final Line part2 = new Line();
	private final ObjectProperty<Color> stroke = new SimpleObjectProperty<>(Color.LIGHTGRAY);
	private final DoubleProperty strokeWidth = new SimpleDoubleProperty(4);

	/**
	 * constructor
	 *
	 * @param source source node
	 * @param target target node
	 */
	public WorkflowEdgeItem(Pane source, Pane target) {
		this.source = source;
		this.target = target;

		part1.strokeProperty().bind(stroke);
		part1.strokeWidthProperty().bind(strokeWidth);
		part2.strokeProperty().bind(stroke);
		part2.strokeWidthProperty().bind(strokeWidth);

		setEffect(GrayOutlineEffect.getInstance());

		part1.startXProperty().bind(source.translateXProperty().add(source.widthProperty().divide(2)));
		part1.startYProperty().bind(source.translateYProperty().add(source.heightProperty().divide(2)));
		part1.endXProperty().bind(part1.startXProperty());
		part1.endYProperty().bind(target.translateYProperty().add(target.heightProperty().divide(2)));

		part2.startXProperty().bind(source.translateXProperty().add(source.widthProperty().divide(2)));
		part2.endXProperty().bind(target.translateXProperty().add(target.widthProperty().divide(2)));
		part2.endYProperty().bind(target.translateYProperty().add(target.heightProperty().divide(2)));
		part2.startYProperty().bind(part2.endYProperty());

		final ArrowHead arrowHead1 = new ArrowHead();

		arrowHead1.update(part1);

		part1.startXProperty().addListener((observable, oldValue, newValue) -> arrowHead1.update(part1));
		part1.startYProperty().addListener((observable, oldValue, newValue) -> arrowHead1.update(part1));
		part1.endXProperty().addListener((observable, oldValue, newValue) -> arrowHead1.update(part1));
		part1.endYProperty().addListener((observable, oldValue, newValue) -> arrowHead1.update(part1));
		arrowHead1.visibleProperty().bind(part1.startYProperty().isNotEqualTo(part1.endYProperty()));

		final ArrowHead arrowHead2 = new ArrowHead();

		arrowHead2.update(part2);

		part2.startXProperty().addListener((observable, oldValue, newValue) -> arrowHead2.update(part2));
		part2.startYProperty().addListener((observable, oldValue, newValue) -> arrowHead2.update(part2));
		part2.endXProperty().addListener((observable, oldValue, newValue) -> arrowHead2.update(part2));
		part2.endYProperty().addListener((observable, oldValue, newValue) -> arrowHead2.update(part2));
		arrowHead2.visibleProperty().bind(part2.startXProperty().isNotEqualTo(part2.endXProperty()));

		this.getChildren().addAll(part1, part2, arrowHead1, arrowHead2);
	}

	/**
	 * compute angle of vector in radian
	 *
	 * @param p point
	 * @return angle of vector in radian
	 */
	public static double computeAngle(Point2D p) {
		if (p.getX() != 0) {
			double x = Math.abs(p.getX());
			double y = Math.abs(p.getY());
			double a = Math.atan(y / x);

			if (p.getX() > 0) {
				if (p.getY() > 0)
					return a;
				else
					return 2.0 * Math.PI - a;
			} else // p.getX()<0
			{
				if (p.getY() > 0)
					return Math.PI - a;
				else
					return Math.PI + a;
			}
		} else if (p.getY() > 0)
			return 0.5 * Math.PI;
		else // p.y<0
			return -0.5 * Math.PI;
	}

	private class ArrowHead extends Group {
		private final Line part1 = new Line();
		private final Line part2 = new Line();

		public ArrowHead() {
			part1.strokeProperty().bind(stroke);
			part1.strokeWidthProperty().bind(strokeWidth);
			part2.strokeProperty().bind(stroke);
			part2.strokeWidthProperty().bind(strokeWidth);

			getChildren().add(part1);
			getChildren().add(part2);
		}

		public void update(Line line) {
			Point2D start = new Point2D(line.getStartX(), line.getStartY());
			Point2D end = new Point2D(line.getEndX(), line.getEndY());
			double radian = computeAngle(end.subtract(start));

			(new Point2D(line.getStartX(), line.getStartY())).angle(line.getEndX(), line.getEndY());

			double dx = 5 * Math.cos(radian);
			double dy = 5 * Math.sin(radian);

			Point2D mid = start.midpoint(end);
			Point2D head = mid.add(dx, dy);
			Point2D one = mid.add(-dy, dx);
			Point2D two = mid.add(dy, -dx);

			part1.setStartX(one.getX());
			part1.setStartY(one.getY());
			part1.setEndX(head.getX());
			part1.setEndY(head.getY());

			part2.setStartX(two.getX());
			part2.setStartY(two.getY());
			part2.setEndX(head.getX());
			part2.setEndY(head.getY());
		}
	}

	public Pane getSource() {
		return source;
	}

	public Pane getTarget() {
		return target;
	}

	public Color getStroke() {
		return stroke.get();
	}

	public ObjectProperty<Color> strokeProperty() {
		return stroke;
	}

	public void setStroke(Color stroke) {
		this.stroke.set(stroke);
	}

	public double getStrokeWidth() {
		return strokeWidth.get();
	}

	public DoubleProperty strokeWidthProperty() {
		return strokeWidth;
	}

	public void setStrokeWidth(double strokeWidth) {
		this.strokeWidth.set(strokeWidth);
	}
}
