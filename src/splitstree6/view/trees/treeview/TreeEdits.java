/*
 *  TreeEdits.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.treeview;

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import jloda.swing.util.ColorUtilsFX;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.layout.tree.LabeledEdgeShape;

import java.util.Collection;
import java.util.HashMap;

/**
 * maintains string array recording tree edits
 * Daniel Huson, 5.2022
 */
public class TreeEdits {
	/**
	 * update the recorded edits
	 *
	 * @param editsString  edits string
	 * @param edgeShapeMap edge getShape map
	 */
	public static void applyEdits(String[] editsString, ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap) {
		var tree = edgeShapeMap.keySet().stream().map(e -> (PhyloTree) e.getOwner()).findAny().orElse(null);
		if (tree != null) {
			for (var editString : editsString) {
				var edit = Edit.parse(editString);
				if (edit != null) {
					var edge = tree.edgeStream().filter(e -> e.getId() == edit.edgeId()).findAny().orElse(null);
					if (edge != null) {
						if (edgeShapeMap.get(edge).getShape() instanceof Shape shape) {
							switch (edit.code()) {
								case 'c' -> {
									if (ColorUtilsFX.isColor(edit.parameter())) {
										var color = Color.web(edit.parameter());
										shape.getStyleClass().remove("graph-edge");
										shape.setStroke(color);
									}
								}
								case 'w' -> {
									if (NumberUtils.isDouble(edit.parameter())) {
										var width = edit.parameterAsDouble();
										if (width > 0) {
											shape.setStrokeWidth(width);
										}
									}
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

	public static String[] addEdits(String[] editsString, Collection<Edit> newEdits) {
		var editPosMap = new HashMap<Edit, Integer>();

		var next = editsString.length;

		for (var newEdit : newEdits) {
			var replaces = false;
			for (int i = 0; i < editsString.length; i++) {
				String editString = editsString[i];
				var edit = Edit.parse(editString);
				if (edit != null && edit.code() == newEdit.code() && edit.edgeId() == newEdit.edgeId()) {
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


	public record Edit(char code, int edgeId, String parameter) {
		public Edit(char code, int edgeId, double value) {
			this(code, edgeId, String.valueOf(value));
		}

		public Edit(char code, int edgeId, Color value) {
			this(code, edgeId, String.valueOf(value));
		}

		public static Edit parse(String editString) {
			var tokens = StringUtils.split(editString, ':');
			if (tokens.length == 3 && tokens[0].length() == 1 && NumberUtils.isInteger(tokens[1])) {
				return new Edit(tokens[0].charAt(0), NumberUtils.parseInt(tokens[1]), tokens[2]);
			} else
				return null;
		}

		public String toString() {
			if (NumberUtils.isDouble(parameter))
				return String.format("%c:%d:%s", code, edgeId, StringUtils.removeTrailingZerosAfterDot("%.2f", NumberUtils.parseDouble(parameter)));
			else
				return String.format("%c:%d:%s", code, edgeId, parameter);
		}

		public double parameterAsDouble() {
			return NumberUtils.parseDouble(parameter);
		}
	}
}
