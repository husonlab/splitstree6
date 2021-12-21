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
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import jloda.fx.util.GrayOutlineEffect;
import jloda.fx.util.MiddleArrowHead;

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

		var midArrow1 = new MiddleArrowHead(part1);
		midArrow1.visibleProperty().bind(part1.startYProperty().isNotEqualTo(part1.endYProperty()));

		var midArrow2 = new MiddleArrowHead(part2);
		midArrow2.visibleProperty().bind(part2.startXProperty().isNotEqualTo(part2.endXProperty()));

		this.getChildren().addAll(part1, part2, midArrow1, midArrow2);
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
