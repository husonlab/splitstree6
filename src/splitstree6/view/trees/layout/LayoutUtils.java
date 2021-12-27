/*
 *  Copyright (C) 2018. Daniel H. Huson
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

package splitstree6.view.trees.layout;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Parent;
import jloda.fx.control.RichTextLabel;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloGraph;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.Triplet;
import splitstree6.data.TaxaBlock;
import splitstree6.view.trees.treepages.LayoutOrientation;

import java.util.LinkedList;

/**
 * some layout utilities
 * Daniel Huson, 12.2021
 */
public class LayoutUtils {
	public static double MAX_FONT_SIZE = 24;

	public static Triplet<Double, Double, Double> computeFontHeightGraphWidthHeight(TaxaBlock taxaBlock, PhyloGraph graph, boolean radial, double width, double height) {
		double fontHeight;
		if (radial)
			fontHeight = Math.min(MAX_FONT_SIZE, 0.5 * Math.min(width, height) * Math.PI / (taxaBlock.getNtax() + 1));
		else
			fontHeight = Math.min(MAX_FONT_SIZE, height / (taxaBlock.getNtax() + 1));

		var maxLabelWidth = 0.0;
		NodeArray<RichTextLabel> nodeLabelMap = graph.newNodeArray();
		for (var v : graph.nodes()) {
			var text = getLabelText(taxaBlock, graph, v);
			if (text != null) {
				var label = new RichTextLabel(text);
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				nodeLabelMap.put(v, label);

				// BasicFX.reportChanges(label.getRawText(),label.translateXProperty());
				// BasicFX.reportChanges(label.getRawText(),label.translateYProperty());

				maxLabelWidth = Math.max(maxLabelWidth, label.getEstimatedWidth());
			}
		}

		if (maxLabelWidth + fontHeight > 0.25 * width) {
			fontHeight = Math.min(MAX_FONT_SIZE, fontHeight * 0.25 * width / (maxLabelWidth + fontHeight));
			maxLabelWidth = 0;
			for (var label : nodeLabelMap.values()) {
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				maxLabelWidth = Math.max(maxLabelWidth, label.getRawText().length() * 0.7 * fontHeight);
			}
		}
		var labelGap = fontHeight;

		final double normalizeWidth;
		final double normalizeHeight;

		if (radial) {
			if (maxLabelWidth > 100) {
				fontHeight *= 100 / maxLabelWidth;
				labelGap = fontHeight;
				maxLabelWidth = 100;
			}

			var tmp = Math.min(width - 2 * (maxLabelWidth + labelGap), height - 2 * (maxLabelWidth + labelGap));
			if (tmp > 20)
				tmp -= 10;
			else if (tmp < 0)
				tmp = 20;
			normalizeWidth = normalizeHeight = tmp;
		} else {
			normalizeWidth = width - maxLabelWidth - labelGap;
			normalizeHeight = height - fontHeight;
		}

		return new Triplet<>(fontHeight, normalizeWidth, normalizeHeight);
	}

	public static double normalize(double width, double height, NodeArray<Point2D> nodePointMap, boolean maintainAspectRatio) {
		var minX = nodePointMap.values().parallelStream().mapToDouble(Point2D::getX).min().orElse(0);
		var maxX = nodePointMap.values().parallelStream().mapToDouble(Point2D::getX).max().orElse(0);
		var minY = nodePointMap.values().parallelStream().mapToDouble(Point2D::getY).min().orElse(0);
		var maxY = nodePointMap.values().parallelStream().mapToDouble(Point2D::getY).max().orElse(0);

		var scaleX = (maxX > minX ? width / (maxX - minX) : 1);
		var scaleY = (maxY > minY ? height / (maxY - minY) : 1);
		if (maintainAspectRatio) {
			scaleX = scaleY = Math.min(scaleX, scaleY);
		}
		if (minX != 0 || scaleX != 1 || minY != 0 || scaleY != 1) {
			for (var v : nodePointMap.keySet()) {
				var point = nodePointMap.get(v);
				nodePointMap.put(v, new Point2D(point.getX() * scaleX, point.getY() * scaleY));
			}
		}
		return scaleX;
	}

	public static String getLabelText(TaxaBlock taxaBlock, PhyloGraph graph, Node v) {
		if (graph.getNumberOfTaxa(v) == 1) {
			var taxonId = IteratorUtils.getFirst(graph.getTaxa(v));
			if (taxonId != null)
				return taxaBlock.get(taxonId).getDisplayLabelOrName();
			else
				return graph.getLabel(v);
		} else if (graph.getNumberOfTaxa(v) >= 2) {
			return StringUtils.toString(taxaBlock.getLabels(graph.getTaxa(v)), ",");
		} else if (v.getLabel() != null && !graph.getLabel(v).isBlank()) {
			return graph.getLabel(v);
		} else
			return null;
	}

	public static void applyLabelScaleFactor(Parent root, double factor) {
		if (factor > 0 && factor != 1) {
			var queue = new LinkedList<>(root.getChildrenUnmodifiable());
			while (queue.size() > 0) {
				var node = queue.pop();
				if (node instanceof RichTextLabel richTextLabel) {
					richTextLabel.setScale(factor * richTextLabel.getScale());
				} else if (node instanceof Parent parent)
					queue.addAll(parent.getChildrenUnmodifiable());
			}
		}
	}

	public static void applOrientation(Group group, LayoutOrientation orientation) {
		switch (orientation) {
			case Rotate0Deg -> {
				group.setScaleX(1);
				group.setRotate(0);
			}
			case Rotate90Deg -> {
				group.setScaleX(1);
				group.setRotate(-90);
			}
			case Rotate180Deg -> {
				group.setScaleX(1);
				group.setRotate(180);
			}
			case Rotate270Deg -> {
				group.setScaleX(1);
				group.setRotate(90);
			}
			case FlipRotate0Deg -> {
				group.setScaleX(-1);
			}
			case FlipRotate90Deg -> {
				group.setScaleX(-1);
				group.setRotate(-90);
			}
			case FlipRotate180Deg -> {
				group.setScaleX(-1);
				group.setRotate(180);
			}
			case FlipRotate270Deg -> {
				group.setScaleX(-1);
				group.setRotate(90);
			}
		}
	}
}
