/*
 *  TreeScaleAndAlignment.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import jloda.graph.Node;
import jloda.graph.NodeArray;

import java.util.Map;

/**
 * takes care of scaling and alignment of tree drawings
 * Daniel Huson, 1.2023
 */
public class TreeScaleAndAlignment {
	public enum AlignTo {Center, Max}

	private final AlignTo alignTo;
	private final double hRef;
	private final double vRef;
	private final Point2D scale;

	/**
	 * constructor, also scales points to bounds
	 *
	 * @param alignTo      the desired horizontal alignment
	 * @param bounds       drawing bounds
	 * @param nodePointMap node point map of consensus tree
	 */
	public TreeScaleAndAlignment(AlignTo alignTo, Bounds bounds, Map<Node, Point2D> nodePointMap) {
		scale = scaleToBounds(bounds, nodePointMap); // scale consensus tree to bounds

		this.alignTo = alignTo;

		var points = nodePointMap.values();
		if (alignTo == AlignTo.Max)
			hRef = points.stream().mapToDouble(Point2D::getX).max().orElse(0);
		else
			hRef = points.stream().mapToDouble(Point2D::getX).average().orElse(0);

		vRef = 0.5 * (points.stream().mapToDouble(Point2D::getY).max().orElse(0.0) + points.stream().mapToDouble(Point2D::getY).min().orElse(0.0));
	}

	/**
	 * compute scaling and alignment to provided points
	 *
	 * @param nodePointMap node points map for tree to draw
	 */
	public void apply(Map<Node, Point2D> nodePointMap) {
		scale(scale.getX(), scale.getY(), nodePointMap);
		var points = nodePointMap.values();
		double hOffset;
		if (alignTo == AlignTo.Center)
			hOffset = hRef - points.stream().mapToDouble(Point2D::getX).sum() / points.size();
		else
			hOffset = hRef - points.stream().mapToDouble(Point2D::getX).max().orElse(hRef);
		var vOffset = vRef - 0.5 * (points.stream().mapToDouble(Point2D::getY).max().orElse(0.0) + points.stream().mapToDouble(Point2D::getY).min().orElse(0.0));
		translate(hOffset, vOffset, nodePointMap);
	}

	public static Point2D scaleToBounds(Bounds bounds, Map<Node, Point2D> nodePointMap) {
		var xMin = nodePointMap.values().stream().mapToDouble(Point2D::getX).min().orElse(0);
		var xMax = nodePointMap.values().stream().mapToDouble(Point2D::getX).max().orElse(0);
		var yMin = nodePointMap.values().stream().mapToDouble(Point2D::getY).min().orElse(0);
		var yMax = nodePointMap.values().stream().mapToDouble(Point2D::getY).max().orElse(0);

		var scaleX = bounds.getWidth() / (xMax - xMin);
		var scaleY = bounds.getHeight() / (yMax - yMin);

		var xMid = 0.5 * (xMin + xMax);
		var yMid = 0.5 * (yMin + yMax);

		for (var v : nodePointMap.keySet()) {
			var point = nodePointMap.get(v);
			nodePointMap.replace(v, new Point2D((point.getX() - xMid) * scaleX + bounds.getCenterX(), (point.getY() - yMid) * scaleY + bounds.getCenterY()));
		}
		return new Point2D(scaleX, scaleY);
	}

	public static void center(Bounds bounds, NodeArray<Point2D> nodePointMap) {
		var x = nodePointMap.values().stream().mapToDouble(Point2D::getX).sum() / nodePointMap.size();
		var y = nodePointMap.values().stream().mapToDouble(Point2D::getY).sum() / nodePointMap.size();

		var offsetX = bounds.getCenterX() - x;
		var offsetY = bounds.getCenterY() - y;
		nodePointMap.replaceAll((k, v) -> v.add(offsetX, offsetY));
	}

	public static void translate(double offsetX, double offsetY, Map<Node, Point2D> nodePointMap) {
		var offset = new Point2D(offsetX, offsetY);
		nodePointMap.replaceAll((k, v) -> v.add(offset));
	}

	public static void scale(double factorX, double factorY, Map<Node, Point2D> nodePointMap) {
		nodePointMap.replaceAll((k, p) -> new Point2D(p.getX() * factorX, p.getY() * factorY));
	}
}
