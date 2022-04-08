/*
 *  SplitNetworkEdits.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.fx.util.BasicFX;
import jloda.graph.Node;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * maintains string array recording of single tree edits
 * Daniel Huson, 2.2022
 */
public class TreeViewEdits {
	/**
	 * apply the recorded edits
	 *
	 * @param editsString
	 * @param nodeShapeMap
	 * @param splitShapeMap
	 */
	public static void applyEdits(String[] editsString, ObservableMap<Node, Shape> nodeShapeMap, ObservableMap<Integer, ArrayList<Shape>> splitShapeMap) {
		for (var editString : editsString) {
			var edit = Edit.parse(editString);
			if (edit != null) {
				switch (edit.code()) {
					case 'c' -> {
						if (BasicFX.isColor(edit.parameter()) && splitShapeMap.containsKey(edit.split())) {
							var color = Color.web(edit.parameter());
							for (var shape : splitShapeMap.get(edit.split())) {
								shape.getStyleClass().remove("graph-edge");
								shape.setStroke(color);
							}
						}
					}
					case 'w' -> {
						if (NumberUtils.isDouble(edit.parameter()) && splitShapeMap.containsKey(edit.split())) {
							var width = edit.parameterAsDouble();
							if (width > 0) {
								for (var shape : splitShapeMap.get(edit.split()))
									shape.setStrokeWidth(width);
							}
						}
					}
					case 'a' -> {
						if (NumberUtils.isDouble(edit.parameter())) {
							var angle = edit.parameterAsDouble();
							//if (angle != 0.0)
							//	RotateSplit.apply(edit.split(), angle, nodeShapeMap);
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
				if (edit != null && edit.code() == newEdit.code() && edit.split() == newEdit.split()) {
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

	public static String[] addAngles(String[] oldEdits, Collection<Integer> splits, double angle) {
		final var edits = new ArrayList<Edit>();

		var splitsSet = new HashSet<>(splits);

		for (var editString : oldEdits) {
			var edit = Edit.parse(editString);
			if (edit != null && edit.code() == 'a' && splitsSet.contains(edit.split())) {
				edits.add(new Edit('a', edit.split(), edit.parameterAsDouble() + angle));
				splitsSet.remove(edit.split());
			}
		}
		for (var split : splitsSet) {
			edits.add(new Edit('a', split, angle));
		}

		return addEdits(oldEdits, edits);
	}

	public record Edit(char code, int split, String parameter) {
		public Edit(char code, int split, double value) {
			this(code, split, String.valueOf(value));
		}

		public Edit(char code, int split, Color value) {
			this(code, split, String.valueOf(value));
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
				return String.format("%c:%d:%s", code, split, StringUtils.removeTrailingZerosAfterDot("%.2f", NumberUtils.parseDouble(parameter)));
			else
				return String.format("%c:%d:%s", code, split, parameter);
		}

		public double parameterAsDouble() {
			return NumberUtils.parseDouble(parameter);
		}
	}
}
