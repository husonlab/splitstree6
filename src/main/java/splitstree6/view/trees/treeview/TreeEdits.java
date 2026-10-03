/*
 *  TreeEdits.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.fx.util.ColorUtilsFX;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.layout.tree.LabeledEdgeShape;

import java.util.*;

/**
 * maintains string array recording tree edits.
 * An edit names its edge by the position of the edge in a traversal of the tree from the root, children in the
 * order in which they are written ("w:#12:2.5"), which a tree keeps when it is written as Newick and read again;
 * edits saved before 10.2026 name the edge by its id ("w:12:2.5"), which the reader does not give back for a
 * tree that an algorithm computed. Both are read; new edits are written by position.
 * Daniel Huson, 5.2022
 */
public class TreeEdits {
	/**
	 * apply the recorded edits
	 *
	 * @param editsString  edits string
	 * @param edgeShapeMap edge getShape map
	 * @return the edits, every edge named by its position; to be kept in place of the given ones
	 */
	public static String[] applyEdits(String[] editsString, ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap) {
		var tree = edgeShapeMap.keySet().stream().map(e -> (PhyloTree) e.getOwner()).findAny().orElse(null);
		if (tree != null) {
			var positions = edgePositions(tree);
			var edgeAtPosition = new HashMap<Integer, Edge>();
			for (var entry : positions.entrySet())
				edgeAtPosition.put(entry.getValue(), entry.getKey());
			var result = new ArrayList<String>();
			for (var editString : editsString) {
				var edit = Edit.parse(editString);
				if (edit != null) {
					var edge = (edit.byPosition() ? edgeAtPosition.get(edit.edge())
							: tree.edgeStream().filter(e -> e.getId() == edit.edge()).findAny().orElse(null));
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
						result.add(new Edit(edit.code(), positions.get(edge), true, edit.parameter()).toString());
					}
				}
			}
			return result.toArray(new String[0]);
		}
		return editsString;
	}

	/**
	 * the position of every edge in a traversal of the tree from the root, children in the order of the out
	 * edges, each node expanded on its first visit. This is the order in which the edges are written as Newick
	 * and read again, so a tree that has been saved and reloaded has the same positions
	 */
	public static Map<Edge, Integer> edgePositions(PhyloTree tree) {
		var positions = new HashMap<Edge, Integer>();
		if (tree.getRoot() == null) {
			for (var e : tree.edges())
				positions.put(e, positions.size() + 1);
			return positions;
		}
		var seen = new HashSet<Node>();
		var stack = new ArrayDeque<Node>();
		stack.push(tree.getRoot());
		seen.add(tree.getRoot());
		while (!stack.isEmpty()) {
			var v = stack.pop();
			var children = new ArrayList<Edge>();
			for (var e : v.outEdges()) {
				children.add(e);
				positions.put(e, positions.size() + 1);
			}
			for (var i = children.size() - 1; i >= 0; i--) {
				var w = children.get(i).getTarget();
				if (seen.add(w))
					stack.push(w);
			}
		}
		return positions;
	}

	/**
	 * the positions of the edges of the tree that the given edges belong to; empty if there are none
	 */
	public static Map<Edge, Integer> edgePositions(Collection<Edge> edges) {
		return edges.isEmpty() ? Map.of() : edgePositions((PhyloTree) edges.iterator().next().getOwner());
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
				if (edit != null && edit.code() == newEdit.code() && edit.byPosition() == newEdit.byPosition() && edit.edge() == newEdit.edge()) {
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

	/**
	 * an edit of an edge
	 *
	 * @param code       'w' width or 'c' colour
	 * @param edge       the position of the edge in a traversal from the root, or, in an edit read from an older
	 *                   file, its id
	 * @param byPosition whether {@code edge} is a position
	 * @param parameter  the width or the colour
	 */
	public record Edit(char code, int edge, boolean byPosition, String parameter) {
		/** an edit of the edge at the given position */
		public Edit(char code, int position, double value) {
			this(code, position, true, String.valueOf(value));
		}

		/** an edit of the edge at the given position */
		public Edit(char code, int position, Color value) {
			this(code, position, true, String.valueOf(value));
		}

		public static Edit parse(String editString) {
			var tokens = StringUtils.split(editString, ':');
			if (tokens.length == 3 && tokens[0].length() == 1) {
				var byPosition = tokens[1].startsWith("#");
				var edge = (byPosition ? tokens[1].substring(1) : tokens[1]);
				if (NumberUtils.isInteger(edge))
					return new Edit(tokens[0].charAt(0), NumberUtils.parseInt(edge), byPosition, tokens[2]);
			}
			return null;
		}

		public String toString() {
			var edge = (byPosition ? "#" : "") + this.edge;
			if (NumberUtils.isDouble(parameter))
				return String.format("%c:%s:%s", code, edge, StringUtils.trim("%.2f", NumberUtils.parseDouble(parameter)));
			else
				return String.format("%c:%s:%s", code, edge, parameter);
		}

		public double parameterAsDouble() {
			return NumberUtils.parseDouble(parameter);
		}
	}
}
