/*
 * LabeledShape.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.layout.tree;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.MouseDragToTranslate;

import java.util.ArrayList;

public class LabeledEdgeShape extends Group {
	private RichTextLabel label;

	public LabeledEdgeShape() {

		getStyleClass().add("graph-edge");
		label.setStyle("-fx-display-caret: false;");
	}

	public LabeledEdgeShape(Shape shape) {
		this(null, shape);
	}

	public LabeledEdgeShape(RichTextLabel label) {
		this(label, null);

	}

	public LabeledEdgeShape(RichTextLabel label, Shape shape) {
		setLabel(label);
		if (shape != null) {
			shape.setId("graph-edge"); // the is used to rotate graph
			setShape(shape);
		}
	}

	public void setLabel(RichTextLabel label) {
		this.label = label;
		if (label != null) {
			MouseDragToTranslate.setup(label);
		}
	}


	public void setShape(Shape shape) {
		if (shape == null)
			getChildren().clear();
		else
			getChildren().setAll(shape);
	}

	public Node getShape() {
		return getChildren().size() == 0 ? null : getChildren().get(0);
	}

	public RichTextLabel getLabel() {
		return label;
	}

	public boolean hasLabel() {
		return label != null;
	}

	public boolean hasShape() {
		return getShape() != null;
	}

	public Iterable<Node> all() {
		var list = new ArrayList<>(getChildren());
		if (label != null)
			list.add(label);
		return list;
	}
}
