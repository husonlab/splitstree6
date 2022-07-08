/*
 * DensiTreeDrawer.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.*;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.Pair;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.trees.trees2trees.RootedConsensusTree;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.RadialLabelLayout;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.view.trees.InteractionSetup;
import splitstree6.window.MainWindow;

import java.util.*;
import java.util.stream.Collectors;

/**
 * draws a densitree on a canvas
 * Daniel Huson, 6.2022
 */
public class DensiTreeDrawer {
	private final MainWindow mainWindow;

	private final RadialLabelLayout radialLabelLayout;

	private PhyloTree consensusTree;
	private final InvalidationListener invalidationListener;

	public DensiTreeDrawer(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		this.radialLabelLayout = new RadialLabelLayout();

		invalidationListener = e -> {
			if (consensusTree != null) {
				consensusTree.postorderTraversal(consensusTree.getRoot(), v -> {
					if (v.getInDegree() > 0) {
						var selected = false;
						if (v.isLeaf()) {
							if (mainWindow.getTaxonSelectionModel().isSelected(mainWindow.getWorkingTaxa().get(consensusTree.getTaxon(v))))
								selected = mainWindow.getTaxonSelectionModel().isSelected(mainWindow.getWorkingTaxa().get(consensusTree.getTaxon(v)));
						} else {
							selected = true;
							for (var f : v.outEdges()) {
								if (f.getData() instanceof Line line) {
									if (line.getEffect() == null) {
										selected = false;
										break;
									}
								}
							}
						}
						if (v.getFirstInEdge().getData() instanceof Line line) {
							line.setEffect(selected ? SelectionEffectBlue.getInstance() : null);
						}
					}
				});
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(invalidationListener));
	}

	public void apply(Bounds targetBounds, List<PhyloTree> phyloTrees, StackPane parent, TreeDiagramType diagramType, boolean jitter,
					  boolean antiConsensus,
					  double horizontalZoomFactor, double verticalZoomFactor, ReadOnlyDoubleProperty fontScaleFactor) {
		radialLabelLayout.getItems().clear();

		RunAfterAWhile.apply(parent, () -> {
			var oldCanvas = (Canvas) parent.getChildren().get(0);
			oldCanvas.setOpacity(0.2);

			AService.run(() -> {
				var canvas = new Canvas(targetBounds.getWidth(), targetBounds.getHeight());
				var pane = new Pane();
				pane.setOnMouseClicked(e -> {
					if (!e.isShiftDown())
						mainWindow.getTaxonSelectionModel().clearSelection();
				});
				pane.setMinSize(targetBounds.getWidth(), targetBounds.getHeight());
				pane.setPrefSize(targetBounds.getWidth(), targetBounds.getHeight());
				pane.setMaxSize(targetBounds.getWidth(), targetBounds.getHeight());

				var width = targetBounds.getWidth() * horizontalZoomFactor;
				var height = targetBounds.getHeight() * verticalZoomFactor;
				if (diagramType.isRadialOrCircular()) {
					width = height = Math.min(width, height);
				}
				var minX = targetBounds.getMinX() + 0.5 * (targetBounds.getWidth() - width);
				var minY = targetBounds.getMinY() + 0.5 * (targetBounds.getHeight() - height);

				var bounds = new BoundingBox(minX, minY, width + (diagramType.isRadialOrCircular() ? 0 : -200), height);

				pane.setStyle("-fx-background-color: transparent;");
				//pane.setStyle("-fx-border-color: yellow;");

				var treesBlock = new TreesBlock();
				treesBlock.getTrees().addAll(phyloTrees);

				if (false) {
					canvas.getGraphicsContext2D().setStroke(Color.RED);
					canvas.getGraphicsContext2D().strokeRect(2, 2, targetBounds.getWidth() - 4, targetBounds.getHeight() - 4);
					canvas.getGraphicsContext2D().strokeRect(2, 2, 0.9 * targetBounds.getWidth() - 4, 0.9 * targetBounds.getHeight() - 4);
				}
				if (false) {
					canvas.getGraphicsContext2D().setStroke(Color.YELLOW);
					canvas.getGraphicsContext2D().strokeRect(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
				}
				apply(mainWindow.getWorkingTaxa(), phyloTrees, diagramType, jitter, antiConsensus, bounds, canvas, pane, radialLabelLayout);
				return new Result(canvas, pane, radialLabelLayout);
			}, result -> {
				parent.getChildren().set(0, result.canvas());
				parent.getChildren().set(1, result.labelPane());

				postprocessLabels(mainWindow.getStage(), mainWindow.getWorkingTaxa(), mainWindow.getTaxonSelectionModel(), result.labelPane(), fontScaleFactor);
				Platform.runLater(() -> invalidationListener.invalidated(null));
				RunAfterAWhile.apply(result, () -> Platform.runLater(() -> result.radialLabelLayout().layoutLabels()));
			}, null);
		});
	}

	private void apply(TaxaBlock taxaBlock, List<PhyloTree> trees, TreeDiagramType diagramType, boolean jitter, boolean antiConsensus, Bounds bounds, Canvas canvas, Pane labelPane,
					   RadialLabelLayout radialLabelLayout) {

		var cycle = computeCycle(taxaBlock.getTaxaSet(), trees);
		final var taxon2pos = new int[cycle.length];
		for (var pos = 1; pos < cycle.length; pos++) {
			taxon2pos[cycle[pos]] = pos;
		}
		var lastTaxon = cycle[cycle.length - 1];

		// compute consensus tree
		Point2D consensusCenter;
		Point2D consensusScale;
		{
			var edgesGroup = new Group();
			consensusTree = RootedConsensusTree.computeRootedConsensusTree(trees, RootedConsensusTree.Consensus.Majority);
			try (NodeArray<Point2D> nodePointMap = consensusTree.newNodeArray();
				 var nodeAngleMap = consensusTree.newNodeDoubleArray()) {
				if (diagramType.isRadialOrCircular()) {
					computeRadialLayout(consensusTree, taxon2pos, taxaBlock.getNtax(), lastTaxon, nodePointMap, nodeAngleMap);
					consensusScale = LayoutUtils.scaleToBounds(bounds, nodePointMap);
				} else {
					computeTriangularLayout(consensusTree, taxon2pos, nodePointMap);
					consensusScale = LayoutUtils.scaleToBounds(bounds, nodePointMap);
				}
				for (var e : consensusTree.edges()) {
					var p = nodePointMap.get(e.getSource());
					var q = nodePointMap.get(e.getTarget());
					var line = new Line(p.getX(), p.getY(), q.getX(), q.getY());
					e.setData(line);
					line.getStyleClass().add("graph-special-edge");
					edgesGroup.getChildren().add(line);
					line.setOnMouseClicked(a -> {
						if (!a.isShiftDown())
							mainWindow.getTaxonSelectionModel().clearSelection();

						consensusTree.preorderTraversal(e.getTarget(), v -> {
							if (v.isLeaf()) {
								var taxon = taxaBlock.get(consensusTree.getTaxon(v));
								mainWindow.getTaxonSelectionModel().toggleSelection(taxon);
							}
						});
						a.consume();
					});
					line.setOnMouseEntered(a -> {
						if (!a.isStillSincePress()) {
							line.setStrokeWidth(5);
						}
					});
					line.setOnMouseExited(a -> {
						if (!a.isStillSincePress()) {
							line.setStrokeWidth(1);
						}
					});
				}

				var labelGroup = new Group();

				var fontSize = Math.min(14, labelPane.getPrefHeight() / (1.5 * taxaBlock.getNtax() + 1));
				for (var v : consensusTree.leaves()) {
					var t = consensusTree.getTaxon(v);
					var label = new RichTextLabel(taxaBlock.get(t).getDisplayLabelOrName());
					label.setFontSize(fontSize);
					label.setUserData(t);
					var point = nodePointMap.get(v);
					var angle = nodeAngleMap.getOrDefault(v, 0.0);
					label.setTranslateX(point.getX());
					label.setTranslateY(point.getY());
					//radialLabelLayout.addAvoidable(() -> x, () -> y, () -> 3.0, () -> 3.0);
					radialLabelLayout.addItem(new SimpleDoubleProperty(point.getX()), new SimpleDoubleProperty(point.getY()), angle,
							label.widthProperty(), label.heightProperty(), a -> label.setTranslateX(point.getX() + a), a -> label.setTranslateY(point.getY() + a));
					labelGroup.getChildren().add(label);
				}
				labelPane.getChildren().addAll(edgesGroup, labelGroup);
				consensusCenter = computeCenter(nodePointMap.values());
			}
		}

		var gc = canvas.getGraphicsContext2D();
		gc.setStroke((MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK).deriveColor(1, 1, 1, 0.1));

		gc.setLineWidth(0.3);

		var random = new Random(666);

		var consensusClusters = (antiConsensus ? extractClusters(consensusTree) : null);

		for (var tree : trees) {
			if (consensusClusters != null && areCompatible(extractClusters(tree), consensusClusters)) {
				continue;
			}

			if (false)
				gc.setStroke(Color.color(random.nextDouble(), random.nextDouble(), random.nextDouble()));

			try (NodeArray<Point2D> nodePointMap = tree.newNodeArray()) {
				if (diagramType.isRadialOrCircular()) {
					try (var nodeAngleMap = tree.newNodeDoubleArray()) {
						computeRadialLayout(tree, taxon2pos, taxaBlock.getNtax(), lastTaxon, nodePointMap, nodeAngleMap);
					}
					// scale and center based on consensus tree:
					LayoutUtils.scale(consensusScale.getX(), consensusScale.getY(), nodePointMap);
					var center = computeCenter(nodePointMap.values());
					LayoutUtils.translate(consensusCenter.subtract(center).getX(), consensusCenter.subtract(center).getY(), nodePointMap);

				} else {
					computeTriangularLayout(tree, taxon2pos, nodePointMap);
					// scale to bounds:
					LayoutUtils.scaleToBounds(bounds, nodePointMap);
				}

				if (jitter) {
					var distance = 2 * Math.pow(2 * random.nextGaussian(), 2);
					var angle = 360 * random.nextDouble();
					var direction = GeometryUtilsFX.translateByAngle(0, 0, angle, distance);
					LayoutUtils.translate(direction.getX(), direction.getY(), nodePointMap);
				}

				for (var e : tree.edges()) {
					var p = nodePointMap.get(e.getSource());
					var q = nodePointMap.get(e.getTarget());
					gc.strokeLine(p.getX(), p.getY(), q.getX(), q.getY());
				}
			}
		}
	}

	private boolean areCompatible(HashSet<BitSet> clusters1, HashSet<BitSet> clusters2) {
		for (var a : clusters1) {
			for (var b : clusters2) {
				if (!(!a.intersects(b) || BitSetUtils.contains(a, b) || BitSetUtils.contains(b, a)))
					return false;
			}
		}
		return true;
	}

	public static HashSet<BitSet> extractClusters(PhyloTree tree) {
		try (NodeArray<BitSet> nodeClusterMap = tree.newNodeArray()) {
			tree.postorderTraversal(v -> {
				var cluster = new BitSet();
				for (var t : tree.getTaxa(v))
					cluster.set(t);
				for (var w : v.children()) {
					cluster.or(nodeClusterMap.get(w));
				}
				nodeClusterMap.put(v, cluster);

			});
			return new HashSet<>(nodeClusterMap.values());
		}
	}

	public RadialLabelLayout getRadialLabelLayout() {
		return radialLabelLayout;
	}

	public static int[] computeCycle(BitSet taxa, List<PhyloTree> trees) {
		var distances = new DistancesBlock();
		distances.setNtax(taxa.cardinality());
		var step = Math.max(1, trees.size() / 1000);
		for (var i = 0; i < trees.size(); i += step) {
			var tree = trees.get(i);
			var splits = new ArrayList<ASplit>();
			TreesUtilities.computeSplits(taxa, tree, splits);
			SplitsUtilities.splitsToDistances(splits, true, distances);
		}
		return NeighborNetCycle.compute(taxa.cardinality(), distances.getDistances());
	}

	private static Point2D computeCenter(Collection<Point2D> points) {
		if (points.size() == 0)
			return new Point2D(0, 0);
		else
			return new Point2D(points.stream().mapToDouble(Point2D::getX).sum() / points.size(),
					points.stream().mapToDouble(Point2D::getY).sum() / points.size());
	}

	private static boolean nodeShapeOrLabelEntered = false;

	private static void postprocessLabels(Stage stage, TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel, Pane labelPane, ReadOnlyDoubleProperty fontScaleFactor) {
		var references = new ArrayList<>();

		for (var label : BasicFX.getAllRecursively(labelPane, RichTextLabel.class)) {
			if (label.getUserData() instanceof Integer t) {
				var taxon = taxaBlock.get(t);
				//label.setOnMousePressed(mousePressedHandler);
				//label.setOnMouseDragged(mouseDraggedHandler);
				final EventHandler<MouseEvent> mouseClickedHandler = e -> {
					if (e.isStillSincePress()) {
						if (!e.isShiftDown())
							taxonSelectionModel.clearSelection();
						taxonSelectionModel.toggleSelection(taxon);
					}
					e.consume();
				};
				label.setOnContextMenuRequested(InteractionSetup.createNodeContextMenuHandler(stage, label));
				label.setOnMouseClicked(mouseClickedHandler);

				if (taxonSelectionModel.isSelected(taxon)) {
					label.setEffect(SelectionEffectBlue.getInstance());
				}
				DraggableUtils.setupDragMouseTranslate(label);

				InvalidationListener invalidationListener = e -> {
					label.setText(taxon.getDisplayLabelOrName());
				};
				taxon.displayLabelProperty().addListener(new WeakInvalidationListener(invalidationListener));
				references.add(invalidationListener); // avoid garbage collection

				ChangeListener<Number> fontScaleChangeListener = (v, o, n) -> {
					label.setFontSize(label.getFontSize() / o.doubleValue() * n.doubleValue());
				};
				fontScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));
				references.add(fontScaleChangeListener);
			}

			label.setOnMouseEntered(e -> {
				if (!e.isStillSincePress() && !nodeShapeOrLabelEntered) {
					nodeShapeOrLabelEntered = true;
					label.setScaleX(1.1 * label.getScaleX());
					label.setScaleY(1.1 * label.getScaleY());
					e.consume();
				}
			});
			label.setOnMouseExited(e -> {
				if (nodeShapeOrLabelEntered) {
					label.setScaleX(label.getScaleX() / 1.1);
					label.setScaleY(label.getScaleY() / 1.1);
					nodeShapeOrLabelEntered = false;
					e.consume();
				}
			});
		}
		InvalidationListener invalidationListener = e -> {
			for (var label : BasicFX.getAllRecursively(labelPane, RichTextLabel.class)) {
				if (label.getUserData() instanceof Integer t) {
					var taxon = taxaBlock.get(t);
					if (taxonSelectionModel.isSelected(taxon)) {
						label.setEffect(SelectionEffectBlue.getInstance());
					} else
						label.setEffect(null);
				}
			}
		};
		taxonSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(invalidationListener));
		references.add(invalidationListener); // avoid garbage collection
		labelPane.setUserData(references); // avoid garbage collection
	}

	public static void computeRadialLayout(PhyloTree tree, int[] taxon2pos, int numberOfTaxa, int lastTaxon, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap) {
		var alpha = 360.0 / numberOfTaxa;

		try (NodeArray<BitSet> taxaBelow = tree.newNodeArray()) {
			tree.postorderTraversal(v -> {
				if (v.isLeaf()) {
					var taxa = BitSetUtils.asBitSet(tree.getTaxa(v));
					taxaBelow.put(v, taxa);
				} else {
					var taxa = BitSetUtils.union(v.childrenStream().map(taxaBelow::get).collect(Collectors.toList()));
					taxaBelow.put(v, taxa);
				}
			});

			var treeTaxa = BitSetUtils.asBitSet(tree.getTaxa());
			tree.postorderTraversal(v -> {
				var taxa = taxaBelow.get(v);
				int add;
				if (taxa.get(lastTaxon)) {
					taxa = BitSetUtils.minus(treeTaxa, taxa);
					add = 180;
				} else
					add = 0;
				nodeAngleMap.put(v, BitSetUtils.asStream(taxa).mapToDouble(t -> alpha * taxon2pos[t] + add).average().orElse(0));
			});
			tree.preorderTraversal(v -> {
				if (v.getInDegree() == 0) { // the root
					nodePointMap.put(v, new Point2D(0, 0));
				} else {
					var e = v.getFirstInEdge();
					var p = e.getSource();
					nodePointMap.put(v, GeometryUtilsFX.translateByAngle(nodePointMap.get(p), nodeAngleMap.get(v), tree.getWeight(e)));
				}
			});
		}
	}

	public static void computeTriangularLayout(PhyloTree tree, int[] taxon2pos, NodeArray<Point2D> nodePointMap) {
		try (NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray()) {
			var root = tree.getRoot();
			if (root != null) {
				nodePointMap.put(root, new Point2D(0.0, 0.0));
				// compute all y-coordinates:
				{
					LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
						if (tree.isLeaf(v) || tree.isLsaLeaf(v)) {
							nodePointMap.put(v, new Point2D(0.0, taxon2pos[tree.getTaxon(v)]));
							firstLastLeafBelowMap.put(v, new Pair<>(v, v));
						} else {
							var firstLeafBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> firstLastLeafBelowMap.get(w).getFirst()).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
									.min(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
							var lastLeafBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> firstLastLeafBelowMap.get(w).getSecond()).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
									.max(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
							var y = 0.5 * (nodePointMap.get(firstLeafBelow).getY() + nodePointMap.get(lastLeafBelow).getY());
							var x = -(Math.abs(nodePointMap.get(lastLeafBelow).getY() - nodePointMap.get(firstLeafBelow).getY()));
							nodePointMap.put(v, new Point2D(x, y));
							firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
						}
					});
				}
			}
		}
	}

	private static record Result(Canvas canvas, Pane labelPane, RadialLabelLayout radialLabelLayout) {
	}
}


