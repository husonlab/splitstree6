/*
 * DrawNewick.java Copyright (C) 2024 Daniel H. Huson
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
 *
 */

package splitstree6.tools.server;

import javafx.geometry.Point2D;
import jloda.fx.phylo.embed.Averaging;
import jloda.fx.phylo.embed.CircularPhylogenyLayout;
import jloda.fx.phylo.embed.RectangularPhylogenyLayout;
import jloda.fx.phylo.embed.TriangularTreeLayout;
import jloda.graph.Node;
import jloda.phylo.LSAUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressSilent;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.layout.splits.SplitNetworkLayout;
import splitstree6.layout.splits.SplitsDiagramType;
import splitstree6.layout.tree.LayoutTreeRadial;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jloda.util.FileLineIterator.PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING;

/**
 * handles draw newick requests
 * Daniel Huson, 11/2024
 */
public class DrawNewick {
	public static String apply(String newick, String layout, double width, double height) throws IOException {
		if (newick.contains("<") && newick.contains(">")) {
			return applySplitNewick(newick, layout, width, height);
		} else {
			return applyTreeNewick(newick, layout, width, height);
		}
	}

	public static String applySplitNewick(String newick, String layout, double width, double height) throws IOException {
		var progress = new ProgressSilent();
		var taxaBlock = new TaxaBlock();
		var splitsBlock = new SplitsBlock();
		var newickReader = new splitstree6.io.readers.splits.NewickReader();

		if (!List.of("radial", "outline").contains(layout)) {
			throw new IOException("Invalid layout value for splits: " + layout);
		}

		var input = PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING + newick;
		newickReader.read(progress, input, taxaBlock, splitsBlock);
		var layouter = new SplitNetworkLayout();

		SplitsDiagramType diagramType = (layout.equals("outline") ? SplitsDiagramType.Outline : SplitsDiagramType.Splits);
		layouter.apply(progress, taxaBlock, splitsBlock, diagramType, width, height);

		var graph = layouter.getGraph();
		var nodePointMap = layouter.getNodePointMap();
		var buf = new StringBuilder();
		buf.append("%d\t%d%n".formatted(graph.getNumberOfNodes(), graph.getNumberOfEdges()));
		for (var v : graph.nodes()) {
			var point = nodePointMap.get(v);
			var label = (graph.hasTaxa(v) ? "\t" + taxaBlock.getLabel(graph.getTaxon(v)) : "");
			buf.append("%d\t%.1f\t%.1f%s%n".formatted(v.getId(), point.getX(), point.getY(), label));
		}
		for (var e : graph.edges()) {
			buf.append("%d\t%d\t%d\t%s\t%d%n".formatted(e.getId(), e.getSource().getId(), e.getTarget().getId(),
					StringUtils.removeTrailingZerosAfterDot("%.8f", graph.getWeight(e)), graph.getSplit(e)));
		}
		return buf.toString();
	}

	public static String applyTreeNewick(String newick, String layout, double width, double height) throws IOException {
		var progress = new ProgressSilent();
		var taxaBlock = new TaxaBlock();
		var treesBlock = new TreesBlock();
		var newickReader = new splitstree6.io.readers.trees.NewickReader();

		var input = PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING + newick;
		newickReader.read(progress, input, taxaBlock, treesBlock);

		if (treesBlock.getNTrees() == 0)
			throw new IOException("No trees found");
		var tree = treesBlock.getTree(1);

		if (tree.hasReticulateEdges()) {
			if (!List.of("cladogram", "phylogram", "circular_cladogram", "circular_phylogram").contains(layout))
				layout = "cladogram";
		} else {
			if (!List.of("radial", "triangular", "cladogram", "phylogram", "circular_cladogram", "circular_phylogram").contains(layout))
				layout = "radial";

		}

		Map<Node, Double> nodeAngleMap = new HashMap<>();
		Map<Node, Point2D> nodePointMap = new HashMap<>();

		switch (layout) {
			case "phylogram" -> {
				LSAUtils.setLSAChildrenAndTransfersMap(tree);
				RectangularPhylogenyLayout.apply(tree, true, Averaging.ChildAverage, true, nodePointMap);
			}
			case "cladogram" -> {
				LSAUtils.setLSAChildrenAndTransfersMap(tree);
				RectangularPhylogenyLayout.apply(tree, false, Averaging.ChildAverage, true, nodePointMap);
			}
			case "triangular" -> TriangularTreeLayout.apply(tree, nodePointMap);
			case "radial" -> {
				LayoutTreeRadial.apply(tree, nodePointMap);
			}
			case "radial_cladogram" -> {
				CircularPhylogenyLayout.apply(tree, false, Averaging.ChildAverage, true, nodeAngleMap, nodePointMap);
			}
			case "circular_cladogram" -> {
				LSAUtils.setLSAChildrenAndTransfersMap(tree);
				CircularPhylogenyLayout.apply(tree, false, Averaging.ChildAverage, true, nodeAngleMap, nodePointMap);
			}
			case "circular_phylogram" -> {
				LSAUtils.setLSAChildrenAndTransfersMap(tree);
				CircularPhylogenyLayout.apply(tree, true, Averaging.ChildAverage, true, nodeAngleMap, nodePointMap);
			}
		}
			scaleCoordinates(nodePointMap, width, height, (layout.contains("circular") || layout.contains("radial")));

			var buf = new StringBuilder();
			buf.append("%d\t%d%n".formatted(tree.getNumberOfNodes(), tree.getNumberOfEdges()));
			for (var v : tree.nodes()) {
				var point = nodePointMap.get(v);
				var label = (tree.hasTaxa(v) ? "\t" + taxaBlock.getLabel(tree.getTaxon(v)) : "");
				buf.append("%d\t%.1f\t%.1f%s%n".formatted(v.getId(), point.getX(), point.getY(), label));
			}
			for (var e : tree.edges()) {
				buf.append("%d\t%d\t%d\t\t%s%n".formatted(e.getId(), e.getSource().getId(), e.getTarget().getId(),
						StringUtils.removeTrailingZerosAfterDot("%.8f", tree.getWeight(e))));
			}
			return buf.toString();
	}

	private static void scaleCoordinates(Map<Node, Point2D> nodePointMap, double width, double height, boolean maintainAspectRatio) {
		var minX = nodePointMap.values().stream().mapToDouble(Point2D::getX).min().orElse(0);
		var maxX = nodePointMap.values().stream().mapToDouble(Point2D::getX).max().orElse(0);
		var minY = nodePointMap.values().stream().mapToDouble(Point2D::getY).min().orElse(0);
		var maxY = nodePointMap.values().stream().mapToDouble(Point2D::getY).max().orElse(0);

		var factorX = (maxX - minX > 0 ? width / (maxX - minX) : 1.0);
		var factorY = (maxY - minY > 0 ? height / (maxY - minY) : 1.0);

		if (maintainAspectRatio) {
			factorX = factorY = Math.min(factorX, factorY);
		}
		var fFactorX = factorX;
		var fFactorY = factorY;

		for (var v : nodePointMap.keySet()) {
			nodePointMap.compute(v, (k, p) -> (p != null ? new Point2D((p.getX() - minX) * fFactorX, (p.getY() - minY) * fFactorY) : null));
		}
	}
}
