/*
 *  NetworkEdits.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.network;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.layout.network.NetworkLayout;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * maintains the string array that records the edits of a network drawing, saved with the view and applied again
 * when the drawing is reopened: {@code ew:<edge id>:<width>} and {@code es:<edge id>:<colour>} for an edge's
 * stroke, {@code mn:<node id>:<x>,<y>} for a node's position, and {@code lp:<node id>:<x>,<y>} for a label's
 * offset from its node, which is recorded for every label once any label has been placed, by hand or by
 * Straighten, so that a reload puts the labels back whatever the layout does then. Files written before
 * 2026-10-18 hold {@code ml:<node id>:<x>,<y>} instead, a label's layout offset on top of the automatic
 * placement, which is still read.
 * Daniel Huson, 8.2023, 10.2026
 */
public class NetworkEdits {
	/**
	 * apply the recorded edits
	 *
	 * @param editsString         string of all edits
	 * @param labeledNodeShapeMap labeled node shape map
	 * @param edgeShapeMap        edge shape map
	 */
	public static void applyEdits(String[] editsString, ObservableMap<Node, LabeledNodeShape> labeledNodeShapeMap, ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap) {
		for (var editString : editsString) {
			var edit = Edit.parse(editString);
			if (edit != null) {
				var id = edit.id();
				switch (edit.code()) {
					case "ew" -> {
						var width = edit.parameterAsDouble();
						for (var e : edgeShapeMap.keySet()) {
							if (e.getId() == id) {
								var labeledShape = edgeShapeMap.get(e);
								if (labeledShape != null && labeledShape.getShape() instanceof Shape shape)
									shape.setStrokeWidth(width);
								break;
							}
						}
					}
					case "es" -> {
						var stroke = edit.parameterAsColor();
						for (var e : edgeShapeMap.keySet()) {
							if (e.getId() == id) {
								var labeledShape = edgeShapeMap.get(e);
								if (labeledShape != null && labeledShape.getShape() instanceof Shape shape)
									shape.setStroke(stroke);
								break;
							}
						}
					}
					case "mn" -> {
						var translate = edit.parameterAsPoint2D();
						for (var n : labeledNodeShapeMap.keySet()) {
							if (n.getId() == id) {
								var labeledNodeShape = labeledNodeShapeMap.get(n);
								if (labeledNodeShape != null) {
									Platform.runLater(() -> {
										labeledNodeShape.setTranslateX(translate.getX());
										labeledNodeShape.setTranslateY(translate.getY());
									});
									break;
								}
							}
						}

					}
					case "lp" -> { // the label's offset from its node
						var offset = edit.parameterAsPoint2D();
						for (var n : labeledNodeShapeMap.keySet()) {
							if (n.getId() == id) {
								var labeledNodeShape = labeledNodeShapeMap.get(n);
								if (labeledNodeShape != null && labeledNodeShape.getLabel() != null) {
									Platform.runLater(() -> placeLabel(labeledNodeShape, offset.getX(), offset.getY()));
									break;
								}
							}
						}
					}
					case "ml" -> { // files written before 2026-10-18: the label's layout offset, on top of whatever the layout binds
						var layout = edit.parameterAsPoint2D();
						for (var n : labeledNodeShapeMap.keySet()) {
							if (n.getId() == id) {
								var labeledNodeShape = labeledNodeShapeMap.get(n);
								if (labeledNodeShape != null) {
									Platform.runLater(() -> {
										labeledNodeShape.getLabel().setLayoutX(layout.getX());
										labeledNodeShape.getLabel().setLayoutY(layout.getY());
									});
									break;
								}
							}
						}
					}
				}
			}
		}
	}

	public static void clearEdits(ObjectProperty<String[]> optionsEdits) {
		optionsEdits.set(new String[0]);
	}

	public static String[] addEdgeStrokeEdits(String[] editsString, Collection<Edge> edges, Color color) {
		return addEdits(editsString, edges.stream().map(e -> Edit.createEdgeStrokeEdit(e.getId(), color)).toList());
	}

	public static String[] addEdgeStrokeWidthEdits(String[] editsString, Collection<Edge> edges, double width) {
		return addEdits(editsString, edges.stream().map(e -> Edit.createEdgeWidthEdit(e.getId(), width)).toList());
	}

	public static String[] addTranslateNodeEdits(String[] editsString, Collection<Node> nodes, double translateX, double translateY) {
		return addEdits(editsString, nodes.stream().map(n -> Edit.createTranslateNodeEdit(n.getId(), translateX, translateY)).toList());
	}

	/**
	 * records the position of the label of each of the nodes as its offset from the node, which is what a reload
	 * restores, whatever the layout does then
	 */
	public static String[] addLabelPositionEdits(String[] editsString, Map<Node, LabeledNodeShape> nodeShapeMap, Collection<Node> nodes) {
		var edits = new ArrayList<Edit>();
		for (var node : nodes) {
			var shape = nodeShapeMap.get(node);
			if (shape != null && shape.getLabel() != null)
				edits.add(Edit.createLabelPositionEdit(node.getId(), labelOffsetX(shape), labelOffsetY(shape)));
		}
		return edits.isEmpty() ? editsString : addEdits(editsString, edits);
	}

	/**
	 * adds the position of every label that has none, so that a drawing in which any label has been placed is
	 * saved with the positions of all its labels
	 */
	public static String[] addMissingLabelPositions(String[] editsString, Map<Node, LabeledNodeShape> nodeShapeMap) {
		var withPosition = new HashSet<Integer>();
		for (var editString : editsString) {
			var edit = Edit.parse(editString);
			if (edit != null && edit.code().equals("lp"))
				withPosition.add(edit.id());
		}
		var missing = new ArrayList<Edit>();
		for (var entry : nodeShapeMap.entrySet()) {
			var shape = entry.getValue();
			if (shape.getLabel() != null && !withPosition.contains(entry.getKey().getId()))
				missing.add(Edit.createLabelPositionEdit(entry.getKey().getId(), labelOffsetX(shape), labelOffsetY(shape)));
		}
		return missing.isEmpty() ? editsString : addEdits(editsString, missing);
	}

	/**
	 * places the label at the given offset from its node, bound to the node so that it follows when the node moves
	 */
	public static void placeLabel(LabeledNodeShape shape, double offsetX, double offsetY) {
		var label = shape.getLabel();
		label.setLayoutX(0);
		label.setLayoutY(0);
		label.translateXProperty().bind(shape.translateXProperty().add(offsetX));
		label.translateYProperty().bind(shape.translateYProperty().add(offsetY));
		NetworkLayout.setPinned(label, true); // the radial layout may still finish after this, and must not undo it
	}

	/** the label's offset from its node: the translation that the layout bound plus the layout coordinates of a drag */
	private static double labelOffsetX(LabeledNodeShape shape) {
		return shape.getLabel().getTranslateX() + shape.getLabel().getLayoutX() - shape.getTranslateX();
	}

	private static double labelOffsetY(LabeledNodeShape shape) {
		return shape.getLabel().getTranslateY() + shape.getLabel().getLayoutY() - shape.getTranslateY();
	}

	/**
	 * adds the position of every node that has none, so that a drawing in which any node has been moved is saved
	 * with the positions of all its nodes
	 */
	public static String[] addMissingNodePositions(String[] editsString, Map<Node, LabeledNodeShape> nodeShapeMap) {
		var withPosition = new HashSet<Integer>();
		for (var editString : editsString) {
			var edit = Edit.parse(editString);
			if (edit != null && edit.code().equals("mn"))
				withPosition.add(edit.id());
		}
		var missing = new ArrayList<Edit>();
		for (var entry : nodeShapeMap.entrySet()) {
			if (!withPosition.contains(entry.getKey().getId()))
				missing.add(Edit.createTranslateNodeEdit(entry.getKey().getId(), entry.getValue().getTranslateX(), entry.getValue().getTranslateY()));
		}
		return missing.isEmpty() ? editsString : addEdits(editsString, missing);
	}

	public static String[] addEdits(String[] editsString, Collection<Edit> newEdits) {
		var editPosMap = new HashMap<Edit, Integer>();

		var next = editsString.length;

		for (var newEdit : newEdits) {
			var replaces = false;
			for (int i = 0; i < editsString.length; i++) {
				String editString = editsString[i];
				var edit = Edit.parse(editString);
				if (edit != null && edit.code().equals(newEdit.code()) && edit.id() == newEdit.id()) {
					editPosMap.put(newEdit, i);
					replaces = true;
					break;
				}
			}
			if (!replaces)
				editPosMap.put(newEdit, next++);
		}

		var newEditStrings = new String[next];
		System.arraycopy(editsString, 0, newEditStrings, 0, editsString.length);
		for (var edit : editPosMap.keySet()) {
			newEditStrings[editPosMap.get(edit)] = edit.toString();
		}
		return newEditStrings;
	}

	public record Edit(String code, int id, String parameter) {
		public static Edit createEdgeWidthEdit(int id, double strokeWidth) {
			return new Edit("ew", id, StringUtils.trim("%.1f", strokeWidth));
		}

		public static Edit createEdgeStrokeEdit(int id, Color color) {
			return new Edit("es", id, String.valueOf(color));
		}

		public static Edit createTranslateNodeEdit(int id, double translateX, double translateY) {
			return new Edit("mn", id, "%s,%s".formatted(StringUtils.trim("%.2f", translateX),
					StringUtils.trim("%.2f", translateY)));
		}

		public static Edit createLabelPositionEdit(int id, double offsetX, double offsetY) {
			return new Edit("lp", id, "%s,%s".formatted(StringUtils.trim("%.2f", offsetX),
					StringUtils.trim("%.2f", offsetY)));
		}

		public static Edit parse(String editString) {
			var tokens = StringUtils.split(editString, ':');
			if (tokens.length == 3)
				return new Edit(tokens[0], NumberUtils.parseInt(tokens[1]), tokens[2]);
			else
				return null;
		}

		public String toString() {
			return String.format("%s:%d:%s", code, id, parameter);
		}

		public double parameterAsDouble() {
			return NumberUtils.parseDouble(parameter);
		}

		public Color parameterAsColor() {
			return Color.web(parameter);
		}

		public Point2D parameterAsPoint2D() {
			var tokens = parameter.split(",");
			return new Point2D(NumberUtils.parseDouble(tokens[0]), NumberUtils.parseDouble(tokens[1]));
		}

	}
}
