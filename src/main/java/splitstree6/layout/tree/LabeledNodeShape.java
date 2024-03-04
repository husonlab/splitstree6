/*
 * LabeledShape.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.fx.util.Icebergs;

import java.util.ArrayList;

public class LabeledNodeShape extends Group {
	private RichTextLabel label;

	public LabeledNodeShape() {
	}

	public LabeledNodeShape(Shape shape) {
		this(null, shape);
	}

	public LabeledNodeShape(RichTextLabel label) {
		this(label, null);

	}

	public LabeledNodeShape(RichTextLabel label, Shape shape) {
		this.label = label;
		if (shape != null) {
			setShape(shape);
			shape.getStyleClass().add("graph-node");
		}
		setId("graph-node"); // the is used to rotate graph
	}

	public void setLabel(RichTextLabel label) {
		this.label = label;
		if (label != null)
			label.getStyleClass().add("graph-label");
	}

	public void setShape(Shape shape) {
		getChildren().clear();
		if (shape != null) {
			if (false && Icebergs.enabled()) { // node icebergs seem to get in the way...
				getChildren().add(Icebergs.create(shape, false));
			}
			getChildren().add(shape);
		}
	}

	public RichTextLabel getLabel() {
		return label;
	}

	public boolean hasLabel() {
		return label != null;
	}

	public boolean hasShape() {
		return !getChildren().isEmpty();
	}

	public Iterable<Node> all() {
		var list = new ArrayList<>(getChildren());
		if (label != null)
			list.add(label);
		return list;
	}
}
