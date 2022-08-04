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
import javafx.beans.property.ReadOnlyBooleanProperty;
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
import javafx.scene.shape.*;
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
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.trees.trees2splits.ConsensusNetwork;
import splitstree6.algorithms.trees.trees2trees.RootedConsensusTree;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.RadialLabelLayout;
import splitstree6.view.trees.InteractionSetup;
import splitstree6.window.MainWindow;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static splitstree6.algorithms.trees.trees2trees.RootedConsensusTree.isCompatibleWithAll;

/**
 * draws a densi-tree on a canvas
 * Daniel Huson, 6.2022
 */
public class DensiTreeDrawer {
	private final MainWindow mainWindow;

	private final RadialLabelLayout radialLabelLayout;

	private PhyloTree consensusTree;
	private final InvalidationListener invalidationListener;

	private final AService<Result> service;

	public DensiTreeDrawer(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		this.radialLabelLayout = new RadialLabelLayout();

		service = new AService<>(mainWindow.getController().getBottomFlowPane());

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

	public void apply(Bounds targetBounds, List<PhyloTree> phyloTrees, StackPane parent, DensiTreeDiagramType diagramType, boolean jitter,
					  boolean antiConsensus, double horizontalZoomFactor, double verticalZoomFactor, ReadOnlyDoubleProperty fontScaleFactor,
					  ReadOnlyBooleanProperty showConsensusTree) {
		radialLabelLayout.getItems().clear();

		RunAfterAWhile.applyInFXThread(parent, () -> {
			var oldCanvas = (Canvas) parent.getChildren().get(0);
			oldCanvas.setOpacity(0.2);

			service.setCallable(() -> {
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

				apply(service.getProgressListener(), mainWindow.getWorkingTaxa(), phyloTrees, diagramType, jitter, antiConsensus, bounds, canvas, pane, radialLabelLayout, showConsensusTree);
				return new Result(canvas, pane, radialLabelLayout);
			});
			service.setOnSucceeded(e -> {
				var result = service.getValue();
				parent.getChildren().set(0, result.canvas());
				parent.getChildren().set(1, result.labelPane());
				postprocessLabels(mainWindow.getStage(), mainWindow.getWorkingTaxa(), mainWindow.getTaxonSelectionModel(), result.labelPane(), fontScaleFactor);
				Platform.runLater(() -> invalidationListener.invalidated(null));
				RunAfterAWhile.applyInFXThread(result, () -> result.radialLabelLayout().layoutLabels());
			});
			service.restart();
		});
	}

	private void apply(ProgressListener progress, TaxaBlock taxaBlock, List<PhyloTree> trees, DensiTreeDiagramType diagramType, boolean jitter, boolean antiConsensus, Bounds bounds,
					   Canvas canvas, Pane labelPane, RadialLabelLayout radialLabelLayout, ReadOnlyBooleanProperty showConsensusTree) throws CanceledException {

		consensusTree = RootedConsensusTree.computeRootedConsensusTree(trees, RootedConsensusTree.Consensus.Greedy);

		//var cycle = computeCycle(consensusTree);
		var cycle = computeCycle(taxaBlock, trees);
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
			try (NodeArray<Point2D> nodePointMap = consensusTree.newNodeArray();
				 var nodeAngleMap = consensusTree.newNodeDoubleArray()) {
				if (diagramType.isRadialOrCircular()) {
					computeRadialLayout(consensusTree, taxon2pos, taxaBlock.getNtax(), lastTaxon, nodePointMap, nodeAngleMap);
					consensusScale = LayoutUtils.scaleToBounds(bounds, nodePointMap);
				} else {
					computeTriangularLayout(consensusTree, taxon2pos, nodePointMap);
					consensusScale = LayoutUtils.scaleToBounds(bounds, nodePointMap);
				}
				{
					var circle = new Circle(1, Color.DARKORANGE);
					circle.getStyleClass().add("graph-special-edge");
					circle.setTranslateX(nodePointMap.get(consensusTree.getRoot()).getX());
					circle.setTranslateY(nodePointMap.get(consensusTree.getRoot()).getY());
					edgesGroup.getChildren().add(circle);
				}


				for (var e : consensusTree.edges()) {
					var p = nodePointMap.get(e.getSource());
					var q = nodePointMap.get(e.getTarget());

					var line = switch (diagramType) {
						case TriangularPhylogram, RadialPhylogram -> new Line(p.getX(), p.getY(), q.getX(), q.getY());
						case RectangularPhylogram -> new Polyline(p.getX(), p.getY(), p.getX(), q.getY(), q.getX(), q.getY());
						case RoundedPhylogram -> new Path(new MoveTo(p.getX(), p.getY()), new QuadCurveTo(p.getX(), q.getY(), q.getX(), q.getY()));
					};
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
				// implement show/hide consensus tree:
				ChangeListener<Boolean> showConsensusListener = (v, o, n) -> {
					edgesGroup.setVisible(n);
				};
				edgesGroup.setUserData(showConsensusListener); // keep it alive
				showConsensusTree.addListener(new WeakChangeListener<>(showConsensusListener));
				edgesGroup.setVisible(showConsensusTree.get());

				var labelGroup = new Group();

				var fontSize = Math.min(14, labelPane.getPrefHeight() / (1.5 * taxaBlock.getNtax() + 1));
				for (var v : consensusTree.leaves()) {
					var t = consensusTree.getTaxon(v);
					var label = new RichTextLabel(taxaBlock.get(t).getDisplayLabelOrName());
					label.setFontSize(fontSize);
					label.setUserData(t);
					var angle = nodeAngleMap.getOrDefault(v, 0.0);
					var point = GeometryUtilsFX.translateByAngle(nodePointMap.get(v), angle, 50);

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

		gc.setLineWidth(0.25);

		var random = new Random(666);

		var consensusClusters = (antiConsensus ? new HashSet<>(extractClusters(consensusTree).values()) : null);

		var rounds = (antiConsensus ? 2 : 1);

		progress.setTasks("DensiTree", "Drawing");
		progress.setMaximum(trees.size());
		progress.setProgress(0);

		for (var i = 0; i < rounds; i++) {
			for (var tree : trees) {
				var treeClusters = (antiConsensus ? extractClusters(tree) : null);

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
						// scale and center based on consensus tree:
						LayoutUtils.scale(consensusScale.getX(), consensusScale.getY(), nodePointMap);
						var center = computeCenter(nodePointMap.values());
						LayoutUtils.translate(consensusCenter.subtract(center).getX(), consensusCenter.subtract(center).getY(), nodePointMap);
					}

					if (jitter) {
						var distance = 2 * Math.pow(2 * random.nextGaussian(), 2);
						var angle = 360 * random.nextDouble();
						var direction = GeometryUtilsFX.translateByAngle(0, 0, angle, distance);
						LayoutUtils.translate(direction.getX(), direction.getY(), nodePointMap);
					}

					var black = (MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK).deriveColor(1, 1, 1, 0.05);
					var red = Color.DARKRED.deriveColor(1, 1, 1, 0.05);

					gc.setStroke(black);

					for (var v : tree.nodes()) {
						var useColor = false;
						if (consensusClusters != null && treeClusters != null) {
							if (!isCompatibleWithAll(treeClusters.get(v), consensusClusters))
								useColor = true;
						}

						for (var e : v.outEdges()) {
							if (consensusClusters != null && treeClusters != null) {
								if (!useColor)
									useColor = !isCompatibleWithAll(treeClusters.get(e.getTarget()), consensusClusters);
								if (useColor)
									gc.setStroke(red);
								else
									gc.setStroke(black);
								if (i == 0 && useColor || i == 1 && !useColor)
									continue;
							}

							var p = nodePointMap.get(e.getSource());
							var q = nodePointMap.get(e.getTarget());
							switch (diagramType) {
								case TriangularPhylogram, RadialPhylogram -> gc.strokeLine(p.getX(), p.getY(), q.getX(), q.getY());
								case RectangularPhylogram -> {
									gc.strokeLine(p.getX(), p.getY(), p.getX(), q.getY());
									gc.strokeLine(p.getX(), q.getY(), q.getX(), q.getY());
								}
								case RoundedPhylogram -> {
									gc.beginPath();
									gc.moveTo(p.getX(), p.getY());
									gc.quadraticCurveTo(p.getX(), q.getY(), q.getX(), q.getY());
									gc.stroke();
								}
							}
						}
					}
				}
				progress.incrementProgress();
			}
		}
		progress.reportTaskCompleted();
	}

	public static Map<Node, BitSet> extractClusters(PhyloTree tree) {
		NodeArray<BitSet> nodeClusterMap = tree.newNodeArray();
		tree.postorderTraversal(v -> {
			var cluster = new BitSet();
			for (var t : tree.getTaxa(v))
				cluster.set(t);
			for (var w : v.children()) {
				cluster.or(nodeClusterMap.get(w));
			}
			nodeClusterMap.put(v, cluster);

		});
		return nodeClusterMap;
	}

	public RadialLabelLayout getRadialLabelLayout() {
		return radialLabelLayout;
	}

	public static int[] computeCycle(PhyloTree tree) {
		var cycle = new ArrayList<Integer>();
		cycle.add(0);

		var seen = new BitSet();
		tree.postorderTraversal(v -> {
			for (var t : tree.getTaxa(v)) {
				if (!seen.get(t)) {
					cycle.add(t);
					seen.set(t);
				}
			}
		});
		return cycle.stream().mapToInt(t -> t).toArray();
	}

	public static int[] computeCycle(TaxaBlock taxaBlock0, Collection<PhyloTree> trees) {
		if (true) {
			var taxonPosMap = new HashMap<Integer, Integer>();
			for (var tree : trees) {
				var posInTree = new Counter();
				tree.postorderTraversal(v -> {
					for (var t : tree.getTaxa(v)) {
						taxonPosMap.put(t, taxonPosMap.getOrDefault(t, 0) + (int) posInTree.getAndIncrement());
					}
				});
			}
			var posTaxonList = new ArrayList<Pair<Integer, Integer>>();
			posTaxonList.add(new Pair<>(0, 0));
			for (var t : taxonPosMap.keySet()) {
				posTaxonList.add(new Pair<>(taxonPosMap.get(t), t));
			}
			posTaxonList.sort(Comparator.comparing(Pair::getFirst));
			return posTaxonList.stream().mapToInt(Pair::getSecond).toArray();
		} else {
			var consensusNetworkAlgorithm = new ConsensusNetwork();
			consensusNetworkAlgorithm.setOptionThresholdPercent(30);
			consensusNetworkAlgorithm.setOptionEdgeWeights(ConsensusNetwork.EdgeWeights.Mean);
			var splits = new SplitsBlock();
			var taxaBlock = new TaxaBlock(taxaBlock0);
			var treesBlock = new TreesBlock();
			taxaBlock.addTaxonByName("rho");
			var tRho = taxaBlock.getNtax();

			var firstTaxonCountMap = new HashMap<Integer, Integer>();

			for (var tree0 : trees) {
				var tree = new PhyloTree(tree0);
				var rho = tree.newNode();
				tree.addTaxon(rho, tRho);
				var e = tree.newEdge(tree.getRoot(), rho);
				tree.setWeight(e, 1);
				treesBlock.getTrees().add(tree);

				{
					var v = tree.getRoot();
					while (v.getOutDegree() > 0) {
						v = v.getFirstOutEdge().getTarget();
					}
					var t = tree.getTaxon(v);
					firstTaxonCountMap.put(t, firstTaxonCountMap.getOrDefault(t, 0) + 1);
				}
			}
			try {
				consensusNetworkAlgorithm.compute(new ProgressSilent(), taxaBlock, treesBlock, splits);
			} catch (IOException ignored) {
			}
			var cycle0 = splits.getCycle();
			var cycle = new int[cycle0.length - 1];
			var pos0 = 1;
			while (cycle0[pos0] != tRho)
				pos0++;
			for (var pos = 1; pos < cycle.length; pos++) {
				pos0++;
				if (pos0 == cycle0.length)
					pos0 = 1;
				cycle[pos] = cycle0[pos0];
			}

			var first = 1;
			var firstCount = 0;
			{
				for (var t : firstTaxonCountMap.keySet()) {
					if (firstTaxonCountMap.get(t) > firstCount) {
						first = t;
						firstCount = firstTaxonCountMap.get(t);
					}
				}
			}
			if (cycle[cycle.length - 1] == first) { // reverse
				var tmp = new int[cycle.length];
				for (var i = 1; i < cycle.length; i++)
					tmp[tmp.length - i] = cycle[i];
				cycle = tmp;
			}

			return cycle;
		}
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

				label.setFontSize(label.getFontSize() * fontScaleFactor.get());
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

	public static void computeTriangularTopologyLayout(PhyloTree tree, int[] taxon2pos, NodeArray<Point2D> nodePointMap) {
		try (NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray()) {
			var root = tree.getRoot();
			if (root != null) {
				nodePointMap.put(root, new Point2D(0.0, 0.0));
				// compute all x- and y-coordinates:

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

	public static void computeTriangularLayout(PhyloTree tree, int[] taxon2pos, NodeArray<Point2D> nodePointMap) {
		try (NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray()) {
			var root = tree.getRoot();
			if (root != null) {
				nodePointMap.put(root, new Point2D(0.0, 0.0));
				// compute all x-coordinates:
				LSAUtils.preorderTraversalLSA(tree, tree.getRoot(), v -> {
					var x = 0.0;
					for (var e : v.inEdges()) {
						var w = e.getSource();
						x = Math.max(x, nodePointMap.get(w).getX() + tree.getWeight(e));
					}
					nodePointMap.put(v, new Point2D(x, 0));
				});

				// compute all y-coordinates:
				{
					LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
						if (tree.isLeaf(v) || tree.isLsaLeaf(v)) {
							nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), taxon2pos[tree.getTaxon(v)]));
							firstLastLeafBelowMap.put(v, new Pair<>(v, v));
						} else {
							var firstLeafBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> firstLastLeafBelowMap.get(w).getFirst()).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
									.min(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
							var lastLeafBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> firstLastLeafBelowMap.get(w).getSecond()).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
									.max(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
							var y = 0.5 * (nodePointMap.get(firstLeafBelow).getY() + nodePointMap.get(lastLeafBelow).getY());
							nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), y));
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


