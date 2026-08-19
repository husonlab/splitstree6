/*
 *  NetworkLayout.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.network;

import javafx.application.Platform;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.*;
import jloda.graph.fmm.FastMultiLayerMethodLayout;
import jloda.graph.fmm.FastMultiLayerMethodOptions;
import jloda.graph.layout.GraphLayouts;
import jloda.graph.layout.JlodaFmmLayoutService;
import jloda.phylo.PhyloGraph;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutUtils;
import splitstree6.layout.tree.RadialLabelLayout;

import java.util.*;
import java.util.function.ToDoubleFunction;

import static splitstree6.layout.tree.LayoutUtils.computeFontHeightGraphWidthHeight;
import static splitstree6.layout.tree.LayoutUtils.normalize;

/**
 * network layout
 * Daniel Huson, 4.2022
 */
public class NetworkLayout {
	public boolean verbose = true;

	/**
	 * wall-clock budget, in milliseconds, for optimizing candidate drawings. The most promising candidate is
	 * always optimized; after that we only start on another while still inside the budget. That keeps a redraw
	 * responsive on a large network, where a single pass of the force-directed layout can itself cost a second,
	 * while still buying several passes on the small networks where a pass is cheap and where the extra passes
	 * do the most good.
	 */
	private static final long OPTIMIZATION_BUDGET_MILLIS = 600;
	private final RadialLabelLayout labelLayout = new RadialLabelLayout();
	private final FastMultiLayerMethodOptions options;

	public NetworkLayout() {
		options = new FastMultiLayerMethodOptions();
		//options =FastMultiLayerMethodOptions.getDefaultForPhylogenetics();options =FastMultiLayerMethodOptions.getDefaultForMicrobialGenomes();
		// options.setStopCriterion(FastMultiLayerMethodOptions.StopCriterion.FixedIterationsOrThreshold);
		if (true) {
			options.setMSingleLevel(true);
			options.setMinGraphSize(Integer.MAX_VALUE); // ensures no coarsening
			options.setInitialPlacementForces(FastMultiLayerMethodOptions.InitialPlacementForces.KeepPositions);
			//options.setForceModel(FastMultiLayerMethodOptions.ForceModel.FruchtermanReingold);
			options.setRepulsiveForcesCalculation(FastMultiLayerMethodOptions.RepulsiveForcesCalculation.Exact);
			options.setAllowedPositions(FastMultiLayerMethodOptions.AllowedPositions.All);
			options.setStopCriterion(FastMultiLayerMethodOptions.StopCriterion.FixedIterations);
			options.setFixedIterations(1000);       // FR usually needs more iters than FMMM
			options.setCoolTemperature(true);
			options.setCoolValue(0.995f);
			options.setFineTuningIterations(1000);     // not needed; FR is the main pass
			options.validate();
		}
	}

	public Group apply(ProgressListener progress, TaxaBlock taxaBlock, NetworkBlock networkBlock, DiagramType diagram, double width, double height, ObservableMap<Integer, RichTextLabel> taxonLabelMap,
					   ObservableMap<Node, LabeledNodeShape> nodeShapeMap, ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap, int randomLayoutSeed) throws CanceledException {
		labelLayout.clear();
		Platform.runLater(nodeShapeMap::clear);
		Platform.runLater(edgeShapeMap::clear);

		var graph = networkBlock.getGraph();

		progress.setTasks("Network", "computing layout");
		progress.setMaximum(graph.getNumberOfNodes() + graph.getNumberOfEdges());
		progress.setProgress(0);

		try (NodeArray<Point2D> nodePointMap = graph.newNodeArray();
			 var nodeAngleMap = graph.newNodeDoubleArray();
			 NodeArray<LabeledNodeShape> newNodeShapeMap = graph.newNodeArray();
			 EdgeArray<LabeledEdgeShape> newEdgeShapeMap = graph.newEdgeArray()) {
			ToDoubleFunction<Edge> edgeWeightFunction;
			if (diagram == DiagramType.Network) {
				if (false)
					edgeWeightFunction = e -> Math.max(0.00001, graph.getWeight(e));
				else edgeWeightFunction = setupScaling(graph);
			} else {
				edgeWeightFunction = e -> 1.0;
			}

			if (networkBlock.getNetworkType().equals(NetworkBlock.Type.Points)) {
				for (int t = 1; t <= taxaBlock.getNtax(); t++) {
					var v = networkBlock.getGraph().getTaxon2Node(t);
					var x = Double.parseDouble(networkBlock.getNodeData(v).get(NetworkBlock.NodeData.BasicKey.x.name()));
					var y = Double.parseDouble(networkBlock.getNodeData(v).get(NetworkBlock.NodeData.BasicKey.y.name()));
					nodePointMap.put(v, new Point2D(x, y));
				}
			} else {
				computeBestLayout(progress, graph, edgeWeightFunction, randomLayoutSeed, nodePointMap);
			}

			if (graph.getNumberOfEdges() == 0) {
				var center = computeCenter(nodePointMap.values());
				for (var v : graph.nodes()) {
					nodeAngleMap.put(v, GeometryUtilsFX.computeAngle(nodePointMap.get(v).subtract(center)));
				}

			} else {
				for (var v : graph.nodes())
					if (graph.getNumberOfTaxa(v) > 0) {
						nodeAngleMap.put(v, computeLabelAngle(v, nodePointMap));
					}
			}

			var dimensions = computeFontHeightGraphWidthHeight(taxaBlock.getNtax(), t -> taxaBlock.get(t).displayLabelProperty(), graph, true, width, height);
			var fontHeight = dimensions.fontHeight();
			width = dimensions.width();
			height = dimensions.height();

			normalize(width, height, nodePointMap, true);

			// nodes:
			var nodesGroup = new Group();
			var nodeLabelsGroup = new Group();

			for (var v : graph.nodes()) {
				try {
					var point = nodePointMap.get(v);
					var labeledNode = new LabeledNodeShape();
					labeledNode.setId("graph-node");
					labeledNode.setTranslateX(point.getX());
					labeledNode.setTranslateY(point.getY());
					if (!networkBlock.getNode2data().containsKey(v)) {
						networkBlock.getNodeData(v).put("x", StringUtils.trim("%.4f", point.getX()));
						networkBlock.getNodeData(v).put("y", StringUtils.trim("%.4f", point.getY()));
					}

					if (graph.hasTaxa(v))
						labeledNode.setTaxa(BitSetUtils.asBitSet(graph.getTaxa(v)));

					var label = LayoutUtils.getLabel(t -> taxaBlock.get(t).displayLabelProperty(), graph, v);
					if (false && graph.getNumberOfEdges() == 0 && label != null) { // todo: allow user to use marks for nodes
						labeledNode.setShape(RichTextLabel.getMark(label.getText()));
					} else {
						var circle = new Circle(v.getDegree() == 1 ? 3 : 2);
						circle.setFill(Color.WHITE);
						circle.setStroke(Color.BLACK);
						labeledNode.setShape(circle, true);
					}

					if (false) {
						for (var node : labeledNode.getChildren()) {
							if ("iceberg".equals(node.getId())) {
								node.setStyle("-fx-stroke: green;");
							}
						}
					}

					nodesGroup.getChildren().add(labeledNode);

					if (graph.getNumberOfTaxa(v) == 1) {
						labeledNode.setUserData(taxaBlock.get(graph.getTaxon(v)));
					}
					newNodeShapeMap.put(v, labeledNode);

					if (label != null) {
						labeledNode.setLabel(label);

						if (graph.getNumberOfTaxa(v) == 1) {
							taxonLabelMap.put(graph.getTaxon(v), label);
						}

						label.setScale(fontHeight / RichTextLabel.getDefaultFont().getSize());
						label.setTranslateX(labeledNode.getTranslateX() + 10);
						label.setTranslateY(labeledNode.getTranslateY() + 10);
						label.setUserData(labeledNode);
						nodeLabelsGroup.getChildren().add(label);

						label.applyCss();

						var translateXProperty = labeledNode.translateXProperty();
						var translateYProperty = labeledNode.translateYProperty();

						labelLayout.addItem(translateXProperty, translateYProperty, nodeAngleMap.getOrDefault(v, 0.0), label.widthProperty(), label.heightProperty(),
								xOffset -> {
									label.setLayoutX(0);
									label.translateXProperty().bind(translateXProperty.add(xOffset));
								},
								yOffset -> {
									label.setLayoutY(0);
									label.translateYProperty().bind(translateYProperty.add(yOffset));
								});

						labelLayout.addAvoidable(() -> labeledNode.getTranslateX() - 0.5 * labeledNode.prefWidth(0), () -> labeledNode.getTranslateY() - 0.5 * labeledNode.prefHeight(0), () -> labeledNode.prefWidth(0), () -> labeledNode.prefHeight(0));
					}
				} catch (NotOwnerException ignored) {
				}
				progress.incrementProgress();
			}

			var edgesGroup = new Group();
			for (var e : graph.edges()) {
				var line = new Line();
				line.getStyleClass().add("graph-edge");

				line.startXProperty().bind(newNodeShapeMap.get(e.getSource()).translateXProperty());
				line.startYProperty().bind(newNodeShapeMap.get(e.getSource()).translateYProperty());
				line.endXProperty().bind(newNodeShapeMap.get(e.getTarget()).translateXProperty());
				line.endYProperty().bind(newNodeShapeMap.get(e.getTarget()).translateYProperty());

				var edgeShape = new LabeledEdgeShape(line);
				edgesGroup.getChildren().add(edgeShape);
				newEdgeShapeMap.put(e, edgeShape);

				progress.incrementProgress();
			}
			Platform.runLater(() -> nodeShapeMap.putAll(newNodeShapeMap));
			Platform.runLater(() -> edgeShapeMap.putAll(newEdgeShapeMap));

			return new Group(edgesGroup, nodesGroup, nodeLabelsGroup);
		}
	}

	/**
	 * computes the positions of the nodes, keeping the most readable of several candidate drawings
	 * <p>
	 * Neither of our layouts has any notion of an edge crossing, and neither has a move that would undo one,
	 * so which drawing we end up with is decided almost entirely by the configuration the optimizer starts
	 * from. We therefore produce more than one drawing and keep whichever reads best; see {@link LayoutScore}.
	 */
	private void computeBestLayout(ProgressListener progress, PhyloGraph graph, ToDoubleFunction<Edge> edgeWeightFunction,
								   int randomLayoutSeed, NodeArray<Point2D> result) throws CanceledException {
		// the parity of the seed selects which of the two layouts to use
		var useStressLayout = (randomLayoutSeed % 2 == 1);
		var layoutService = GraphLayouts.getService();

		LayoutScore best = null;
		if (verbose)
			System.err.printf("Running %s layout...", useStressLayout ? "MDS-based" : layoutService.getName());

		if (useStressLayout)
			best = computeStressLayout(progress, graph, edgeWeightFunction, randomLayoutSeed, result);
		else if (JlodaFmmLayoutService.NAME.equals(layoutService.getName()))
			best = computeForceDirectedLayout(progress, graph, edgeWeightFunction, randomLayoutSeed, result);
		else {
			// Route through the graph-layout SPI so that a native provider (OGDF FM3) is used when one is on the
			// path. Such a provider takes neither a seed nor a starting drawing, so there is only ever the one
			// drawing to be had from it and nothing to choose between.
			layoutService.apply(graph, edgeWeightFunction, (v, p) -> result.put(v, new Point2D(p.getX(), p.getY())));
		}

		if (verbose)
			System.err.println(best != null ? " done (%s)".formatted(best) : " done");
	}

	/**
	 * force-directed layout, started from a crossing-free drawing of a spanning tree
	 * <p>
	 * Our options ask the FMM layout to keep the positions it is given, so it has to actually be given some:
	 * passing it null left every node sitting on the origin, and a force layout started from a collapsed point
	 * cloud shoots leaves out along whatever direction the numerical jitter happened to point and then has no
	 * move that could ever undo the resulting crossings. {@link InitialTreeLayout} supplies a starting drawing
	 * that has no crossings to begin with.
	 * <p>
	 * Generating and scoring one of those costs microseconds where relaxing it costs tens of milliseconds, so
	 * we generate many, sort them, and relax only the most promising - stopping at the first result that comes
	 * back without crossings, which in practice is usually the first one tried.
	 */
	private LayoutScore computeForceDirectedLayout(ProgressListener progress, PhyloGraph graph, ToDoubleFunction<Edge> edgeWeightFunction,
												   int randomLayoutSeed, NodeArray<Point2D> result) throws CanceledException {
		var candidates = computeCandidates(progress, graph, edgeWeightFunction, randomLayoutSeed);

		LayoutScore best = null;
		var deadline = System.currentTimeMillis() + OPTIMIZATION_BUDGET_MILLIS;
		try (NodeArray<Point2D> candidate = graph.newNodeArray()) {
			for (var run = 0; run < Math.min(numberOfRelaxations(graph), candidates.size()); run++) {
				progress.checkForCancel();
				if (run > 0 && System.currentTimeMillis() >= deadline)
					break; // out of budget; what we have is what we keep
				candidate.clear();
				var initial = candidates.get(run);
				options.setRandSeed(randomLayoutSeed + 2 * run);
				FastMultiLayerMethodLayout.apply(options, graph, edgeWeightFunction,
						v -> new LayoutPoint(initial.getOrDefault(v, Point2D.ZERO)),
						(v, p) -> candidate.put(v, new Point2D(p.getX(), p.getY())));
				var score = LayoutScore.compute(graph, candidate);
				if (best == null || score.compareTo(best) < 0) {
					best = score;
					result.clear();
					result.putAll(candidate);
				}
				if (best.crossings() == 0)
					break; // nothing left worth paying for another relaxation
			}
		}
		return best;
	}

	/**
	 * MDS-based layout, run once per candidate starting drawing
	 * <p>
	 * Started from a circle this layout is barely affected by its seed: the jitter that breaks the circle's
	 * symmetry is small enough that on many graphs every run converges to the same local minimum of the
	 * stress, crossings and all. Given the spanning-tree drawings to start from instead, its runs differ as
	 * much as the drawings do. A run is far cheaper here than a force-directed relaxation, so it can afford
	 * to work through more of them.
	 */
	private LayoutScore computeStressLayout(ProgressListener progress, PhyloGraph graph, ToDoubleFunction<Edge> edgeWeightFunction,
											int randomLayoutSeed, NodeArray<Point2D> result) throws CanceledException {
		var candidates = computeCandidates(progress, graph, edgeWeightFunction, randomLayoutSeed);

		LayoutScore best = null;
		var deadline = System.currentTimeMillis() + OPTIMIZATION_BUDGET_MILLIS;
		try (NodeArray<Point2D> candidate = graph.newNodeArray()) {
			for (var run = 0; run < Math.min(numberOfStressRuns(graph), candidates.size()); run++) {
				progress.checkForCancel();
				if (run > 0 && System.currentTimeMillis() >= deadline)
					break;
				candidate.clear();
				var initial = candidates.get(run);
				var params = new WeightedLayout.Params();
				params.maxIterations = 5000;
				params.randomSeed = randomLayoutSeed + 2L * run;
				new WeightedLayout<Node, Edge>().layout(graph.getNodesAsList(), Node::adjacentEdges,
						Node::getOpposite, edgeWeightFunction, candidate::put, params, initial::get);
				var score = LayoutScore.compute(graph, candidate);
				if (best == null || score.compareTo(best) < 0) {
					best = score;
					result.clear();
					result.putAll(candidate);
				}
				if (best.crossings() == 0)
					break;
			}
		}
		return best;
	}

	/**
	 * generates candidate starting drawings and returns them ordered, most promising first
	 * <p>
	 * These cost microseconds each where actually optimizing one costs tens of milliseconds, so it pays to
	 * make a great many, score them, and only ever optimize the best few.
	 */
	private List<Map<Node, Point2D>> computeCandidates(ProgressListener progress, PhyloGraph graph,
													   ToDoubleFunction<Edge> edgeWeightFunction, int randomLayoutSeed) throws CanceledException {
		var candidates = new ArrayList<Map<Node, Point2D>>();
		var scores = new ArrayList<LayoutScore>();
		for (var i = 0; i < numberOfCandidates(graph); i++) {
			progress.checkForCancel();
			// candidate 0 is the canonical drawing, so that an unchanged graph always redraws the same way
			candidates.add(InitialTreeLayout.apply(graph, edgeWeightFunction, i == 0 ? null : new Random(randomLayoutSeed + 31L * i)));
			scores.add(LayoutScore.compute(graph, candidates.get(i)));
		}
		var order = new ArrayList<Integer>();
		for (var i = 0; i < candidates.size(); i++)
			order.add(i);
		order.sort(Comparator.comparing(scores::get)); // stable, so a tie leaves the canonical drawing in front
		return order.stream().map(candidates::get).toList();
	}

	/**
	 * how many candidate spanning-tree drawings to generate and score before relaxing any of them. Scoring is
	 * quadratic in the number of edges, so the number shrinks as the graph grows; on a small network fifty of
	 * them together cost a few milliseconds.
	 */
	private static int numberOfCandidates(Graph graph) {
		if (!LayoutScore.isApplicable(graph))
			return 1; // too big to score, so there would be nothing to choose by
		else if (graph.getNumberOfEdges() <= 150)
			return 50;
		else if (graph.getNumberOfEdges() <= 400)
			return 20;
		else
			return 8;
	}

	/**
	 * upper bound on how many candidates to relax. This is only a backstop: what actually decides how many we
	 * get through is {@link #OPTIMIZATION_BUDGET_MILLIS}, and we stop at the first crossing-free result anyway.
	 */
	private static int numberOfRelaxations(Graph graph) {
		if (!LayoutScore.isApplicable(graph))
			return 1;
		else if (graph.getNumberOfEdges() <= 150)
			return 6;
		else
			return 4;
	}

	/**
	 * upper bound on how many candidates to run the MDS layout on. A run of it costs a small fraction of a
	 * force-directed relaxation, so within the same budget it gets through far more of them.
	 */
	private static int numberOfStressRuns(Graph graph) {
		if (!LayoutScore.isApplicable(graph))
			return 1;
		else if (graph.getNumberOfEdges() <= 150)
			return 24;
		else
			return 12;
	}

	/**
	 * adapter for handing a coordinate to the FMM layout: its point interface declares two methods and so
	 * cannot be a lambda, and jloda does not export its own implementation of it
	 */
	private record LayoutPoint(double x, double y) implements FastMultiLayerMethodLayout.Point {
		LayoutPoint(Point2D point) {
			this(point.getX(), point.getY());
		}

		@Override
		public double getX() {
			return x;
		}

		@Override
		public double getY() {
			return y;
		}
	}

	private double computeLabelAngle(Node v, NodeArray<Point2D> nodePointMap) {
		if (v.getDegree() == 0)
			return 0;
		else if (v.getDegree() == 1) {
			var vp = nodePointMap.get(v);
			var wp = nodePointMap.get(v.getFirstAdjacentEdge().getOpposite(v));
			return GeometryUtilsFX.computeAngle(vp.getX() - wp.getX(), vp.getY() - wp.getY());
		} else {
			var vPt = nodePointMap.get(v);
			var angles = v.adjacentEdgesStream(false).map(e -> e.getOpposite(v)).mapToDouble(w -> GeometryUtilsFX.modulo360(GeometryUtilsFX.computeAngle(nodePointMap.get(w).getX() - vPt.getX(), nodePointMap.get(w).getY() - vPt.getY()))).toArray();
			Arrays.sort(angles);
			var bestI = angles.length - 1;
			var bestD = (360 - angles[angles.length - 1]) + angles[0];
			for (var i = 0; i < angles.length - 1; i++) {
				var d = angles[i + 1] - angles[i];
				if (d > bestD) {
					bestI = i;
					bestD = d;
				}
			}
			return GeometryUtilsFX.modulo360(angles[bestI] + 0.5 * bestD);
		}
	}

	private Point2D computeCenter(Collection<Point2D> points) {
		if (!points.isEmpty()) {
			var x = points.stream().mapToDouble(Point2D::getX).sum();
			var y = points.stream().mapToDouble(Point2D::getY).sum();
			return new Point2D(x / points.size(), y / points.size());
		} else
			return new Point2D(0, 0);
	}

	public RadialLabelLayout getLabelLayout() {
		return labelLayout;
	}

	public static ToDoubleFunction<Edge> setupScaling(PhyloGraph graph) {
		var epsilon = 0.00001;
		var minNonZero = graph.edgeStream().mapToDouble(graph::getWeight).filter(x -> x > epsilon).min().orElse(1.0);
		var max = graph.edgeStream().mapToDouble(graph::getWeight).max().orElse(1.0);

		var maxDesiredLength = 5;

		var edgeWeights = new HashMap<Edge, Double>();
		for (var e : graph.edges()) {
			var w = Math.max(minNonZero, graph.getWeight(e));
			var transformed = Math.max(epsilon, Math.sqrt(1 + (maxDesiredLength - 1) * (maxDesiredLength - 1) * (w - minNonZero) / max));
			edgeWeights.put(e, transformed);
		}
		return edgeWeights::get;
	}
}
