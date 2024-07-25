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
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;

import java.util.Collection;
import java.util.HashMap;

/**
 * maintains string array recording network edits
 * Daniel Huson, 8.2023
 */
public class NetworkEdits {
	/**
	 * recursion the recorded edits
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
					case "ml" -> {
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

	public static String[] addLayoutNodeLabelEdits(String[] editsString, Collection<Node> nodes, double layoutX, double layoutY) {
		return addEdits(editsString, nodes.stream().map(n -> Edit.createLayoutNodeLabelEdit(n.getId(), layoutX, layoutY)).toList());
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
			return new Edit("ew", id, StringUtils.removeTrailingZerosAfterDot("%.1f", strokeWidth));
		}

		public static Edit createEdgeStrokeEdit(int id, Color color) {
			return new Edit("es", id, String.valueOf(color));
		}

		public static Edit createTranslateNodeEdit(int id, double translateX, double translateY) {
			return new Edit("mn", id, "%s,%s".formatted(StringUtils.removeTrailingZerosAfterDot("%.2f", translateX),
					StringUtils.removeTrailingZerosAfterDot("%.2f", translateY)));
		}

		public static Edit createLayoutNodeLabelEdit(int id, double layoutX, double layoutY) {
			return new Edit("ml", id, "%s,%s".formatted(StringUtils.removeTrailingZerosAfterDot("%.2f", layoutX),
					StringUtils.removeTrailingZerosAfterDot("%.2f", layoutY)));
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
