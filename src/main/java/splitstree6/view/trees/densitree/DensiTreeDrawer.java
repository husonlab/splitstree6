/*
 *  DensiTreeDrawer.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.fx.control.ProgressPane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.*;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.graph.algorithms.PQTree;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.phylogeny.layout.Averaging;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.RadialLabelLayout;
import splitstree6.main.SplitsTree6;
import splitstree6.utils.TreesUtils;
import splitstree6.view.trees.InteractionSetup;
import splitstree6.window.MainWindow;

import java.util.*;
import java.util.stream.Collectors;

import static splitstree6.utils.ClusterUtils.isCompatibleWithAll;

/**
 * draws a densi-tree on a canvas
 * Daniel Huson, 6.2022
 */
public class DensiTreeDrawer {
	private final MainWindow mainWindow;

	private final RadialLabelLayout radialLabelLayout;

	private final PhyloTree consensusTree = new PhyloTree();

	private final InvalidationListener invalidationListener;

	private final AService<Boolean> service;


	public DensiTreeDrawer(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		this.radialLabelLayout = new RadialLabelLayout();

		service = new AService<>(mainWindow.getController().getBottomFlowPane());

		invalidationListener = e -> {
			if (consensusTree.getRoot() != null) {
				consensusTree.postorderTraversal(consensusTree.getRoot(), v -> {
					if (v.getInDegree() > 0) {
						var selected = false;
						if (v.isLeaf()) {
							if (mainWindow.getTaxonSelectionModel().isSelected(mainWindow.getWorkingTaxa().get(consensusTree.getTaxon(v))))
								selected = mainWindow.getTaxonSelectionModel().isSelected(mainWindow.getWorkingTaxa().get(consensusTree.getTaxon(v)));
						} else {
							selected = true;
							for (var f : v.outEdges()) {
								if (f.getData() instanceof Shape shape) {
									if (shape.getEffect() == null) {
										selected = false;
										break;
									}
								}
							}
						}
						if (v.getFirstInEdge().getData() instanceof Shape shape) {
							shape.setEffect(selected ? SelectionEffectBlue.getInstance() : null);
						}
					}
				});
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(invalidationListener));
	}

	public void apply(Bounds targetBounds, List<PhyloTree> trees0, StackPane parent, DensiTreeDiagramType diagramType, Averaging averaging,
					  boolean vFlip, boolean jitter, boolean rightAdjust,
					  boolean colorIncompatibleEdges, double horizontalZoomFactor, double verticalZoomFactor, ReadOnlyDoubleProperty fontScaleFactor,
					  ReadOnlyBooleanProperty showTrees, boolean hideFirst10PercentTrees, ReadOnlyBooleanProperty showConsensus,
					  double lineWidth, Color edgeColor, Color otherColor) {
		RunAfterAWhile.applyInFXThread(parent, () -> {
			radialLabelLayout.getItems().clear();

			parent.getChildren().clear();

			if (trees0.isEmpty())
				return;
			var trees = new ArrayList<>(hideFirst10PercentTrees ? new ArrayList<>(trees0).subList(trees0.size() / 10, trees0.size()) : trees0);

			var canvas0 = new Canvas(targetBounds.getWidth(), targetBounds.getHeight());
			var canvas1 = (colorIncompatibleEdges ? new Canvas(targetBounds.getWidth(), targetBounds.getHeight()) : null);

			ChangeListener<Boolean> listener = (v, o, n) -> {
				canvas0.setVisible(n);
				if (canvas1 != null)
					canvas1.setVisible(n);
			};
			parent.getChildren().add(canvas0);
			canvas0.setUserData(listener);
			if (canvas1 != null) {
				parent.getChildren().add(canvas1);
				canvas1.setUserData(listener);
			}

			showTrees.addListener(new WeakChangeListener<>(listener));

			var pane = new Pane();
			pane.setOnMouseClicked(e -> {
				if (!e.isShiftDown())
					mainWindow.getTaxonSelectionModel().clearSelection();
			});
			parent.getChildren().add(pane);

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

			service.setCallable(() -> {
				var progress = service.getProgressListener();
				progress.setTasks("Drawing", "Trees");
				progress.setMaximum(trees.size());
				progress.setProgress(0);
				if (progress instanceof ProgressPane progressPane)
					progressPane.setVisible(true);

				final NodeArray<Point2D> consensusNodePointMap = consensusTree.newNodeArray();
				var nodeAngleMap = consensusTree.newNodeDoubleArray();

				var cycle = computeConsensusAndCycle(mainWindow.getWorkingTaxa(), trees, consensusTree);
				final var taxon2pos = new int[cycle.length];
				for (var pos = 1; pos < cycle.length; pos++) {
					taxon2pos[cycle[pos]] = pos;
				}
				var lastTaxon = cycle[cycle.length - 1];

				if (diagramType.isRadialOrCircular()) {
					computeRadialLayout(consensusTree, taxon2pos, mainWindow.getWorkingTaxa().getNtax(), lastTaxon, consensusNodePointMap, nodeAngleMap);
				} else {
					computeTriangularLayout(consensusTree, averaging, taxon2pos, consensusNodePointMap);
					if (vFlip)
						consensusNodePointMap.entrySet().forEach(e -> e.setValue(new Point2D(e.getValue().getX(), -e.getValue().getY())));
				}

				var treeScaleAndAlignment = new TreeScaleAndAlignment(rightAdjust ? TreeScaleAndAlignment.AlignTo.Max : TreeScaleAndAlignment.AlignTo.Center, bounds, consensusNodePointMap);

				Platform.runLater(() -> {
					drawConsensus(mainWindow, consensusTree, consensusNodePointMap, nodeAngleMap, diagramType, getRadialLabelLayout(), showConsensus, pane);
					ProgramExecutorService.submit(consensusNodePointMap::close);
					ProgramExecutorService.submit(nodeAngleMap::close);
				});

				var random = new Random(666);
				var start = System.currentTimeMillis();
				if (colorIncompatibleEdges) {
					for (PhyloTree tree : trees) {
						var consensusClusters = TreesUtils.extractClusters(consensusTree).values();
						final NodeArray<Point2D> nodePointMap = computeTreeCoordinates(mainWindow.getWorkingTaxa(), tree, averaging, vFlip, taxon2pos, lastTaxon,
								treeScaleAndAlignment, diagramType, jitter, random);
						Platform.runLater(() -> {
							try {
								drawTree(progress, tree, nodePointMap, consensusClusters, diagramType, 0, canvas0, lineWidth, edgeColor, otherColor);
								drawTree(progress, tree, nodePointMap, consensusClusters, diagramType, 1, canvas1, lineWidth, otherColor, otherColor);
								progress.incrementProgress();
							} catch (CanceledException ignored) {
							}
							ProgramExecutorService.submit(nodePointMap::close);
						});
						progress.checkForCancel();
						if (System.currentTimeMillis() - start > 200) {
							Thread.sleep(20);
							start = System.currentTimeMillis();
						}
					}
				} else {
					for (PhyloTree tree : trees) {
						final NodeArray<Point2D> nodePointMap = computeTreeCoordinates(mainWindow.getWorkingTaxa(), tree, averaging, vFlip, taxon2pos, lastTaxon,
								treeScaleAndAlignment, diagramType, jitter, random);
						Platform.runLater(() -> {
							try {
								if (false) { // todo: make random colors an option?
									var randomColor = Color.color(random.nextDouble(), random.nextDouble(), random.nextDouble());
									drawTree(progress, tree, nodePointMap, null, diagramType, 2, canvas0, lineWidth, randomColor, otherColor);
								} else {
									drawTree(progress, tree, nodePointMap, null, diagramType, 2, canvas0, lineWidth, edgeColor, otherColor);
								}
								progress.incrementProgress();
							} catch (CanceledException ignored) {
							}
							ProgramExecutorService.submit(nodePointMap::close);
						});
						progress.checkForCancel();
						if (System.currentTimeMillis() - start > 200) {
							Thread.sleep(20);
							start = System.currentTimeMillis();
						}
					}
				}
				progress.reportTaskCompleted();
				return true;
			});
			service.setOnSucceeded(e -> {
				postprocessLabels(mainWindow.getStage(), mainWindow.getWorkingTaxa(), mainWindow.getTaxonSelectionModel(), pane, fontScaleFactor);
				Platform.runLater(() -> invalidationListener.invalidated(null));
				if (diagramType.isRadialOrCircular())
					RunAfterAWhile.applyInFXThread(getRadialLabelLayout(), () -> getRadialLabelLayout().layoutLabels());
			});
			service.restart();
		});
	}

	public RadialLabelLayout getRadialLabelLayout() {
		return radialLabelLayout;
	}

	private static void drawConsensus(MainWindow mainWindow, PhyloTree consensusTree, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap,
									  DensiTreeDiagramType diagramType, RadialLabelLayout radialLabelLayout, ReadOnlyBooleanProperty showConsensus, Pane pane) {

		if (consensusTree.getRoot() == null)
			return;

		var taxaBlock = mainWindow.getWorkingTaxa();

		var edgesGroup = new Group();
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

			var shape = switch (diagramType) {
				case TriangularPhylogram, RadialPhylogram -> new Line(p.getX(), p.getY(), q.getX(), q.getY());
				case RectangularPhylogram ->
						new Path(new MoveTo(p.getX(), p.getY()), new LineTo(p.getX(), q.getY()), new LineTo(q.getX(), q.getY()));
				case RoundedPhylogram ->
						new Path(new MoveTo(p.getX(), p.getY()), new QuadCurveTo(p.getX(), q.getY(), q.getX(), q.getY()));
			};
			shape.setPickOnBounds(false);

			e.setData(shape);
			shape.getStyleClass().add("graph-special-edge");
			shape.setStrokeWidth(1.5);
			edgesGroup.getChildren().add(shape);
			shape.setOnMouseClicked(a -> {
				if (!a.isShiftDown() && ProgramProperties.isDesktop())
					mainWindow.getTaxonSelectionModel().clearSelection();

				consensusTree.preorderTraversal(e.getTarget(), v -> {
					if (v.isLeaf()) {
						var taxon = taxaBlock.get(consensusTree.getTaxon(v));
						mainWindow.getTaxonSelectionModel().toggleSelection(taxon);
					}
				});
				a.consume();
			});
			if (false) {
				if (SplitsTree6.nodeZoomOnMouseOver) {
					shape.setOnMouseEntered(a -> {
						if (!a.isStillSincePress()) {
							shape.setStrokeWidth(5);
						}
					});
					shape.setOnMouseExited(a -> {
						shape.setStrokeWidth(1.5);
					});
				}
			}
			/*if (Icebergs.enabled()) */
			{ // todo: always allow this because the edges are hard to click on
				edgesGroup.getChildren().add(Icebergs.create(shape, true));
			}
		}
		// implement show/hide consensus tree:
		ChangeListener<Boolean> showConsensusListener = (v, o, n) -> edgesGroup.setVisible(n);
		edgesGroup.setUserData(showConsensusListener); // keep it alive
		showConsensus.addListener(new WeakChangeListener<>(showConsensusListener));
		edgesGroup.setVisible(showConsensus.get());

		var labelGroup = new Group();

		double maxLeafX = Double.MIN_VALUE;
		if (!diagramType.isRadialOrCircular()) {
			maxLeafX = IteratorUtils.asStream(consensusTree.leaves()).mapToDouble(v -> nodePointMap.get(v).getX()).max().orElse(maxLeafX);
		}

		var fontSize = Math.min(14, pane.getPrefHeight() / (1.5 * taxaBlock.getNtax() + 1));
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
			var x = (maxLeafX > Double.MIN_VALUE ? maxLeafX + 3 : point.getX());

			radialLabelLayout.addItem(new SimpleDoubleProperty(x), new SimpleDoubleProperty(point.getY()), angle,
					label.widthProperty(), label.heightProperty(), a -> label.setTranslateX(x + a), a -> label.setTranslateY(point.getY() + a));
			labelGroup.getChildren().add(label);
		}
		pane.getChildren().addAll(edgesGroup, labelGroup);
	}

	private static NodeArray<Point2D> computeTreeCoordinates(TaxaBlock taxaBlock, PhyloTree tree, Averaging averaging, boolean vFlip, int[] taxon2pos, int lastTaxon,
															 TreeScaleAndAlignment treeScaleAndAlignment, DensiTreeDiagramType diagramType,
															 boolean jitter, Random random) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		if (diagramType.isRadialOrCircular()) {
			try (var nodeAngleMap = tree.newNodeDoubleArray()) {
				computeRadialLayout(tree, taxon2pos, taxaBlock.getNtax(), lastTaxon, nodePointMap, nodeAngleMap);
			}
			// scale and center based on consensus tree:
			treeScaleAndAlignment.apply(nodePointMap);

		} else {
			computeTriangularLayout(tree, averaging, taxon2pos, nodePointMap);
			if (vFlip)
				nodePointMap.entrySet().forEach(e -> e.setValue(new Point2D(e.getValue().getX(), -e.getValue().getY())));
			// scale and center based on consensus tree:
			treeScaleAndAlignment.apply(nodePointMap);
		}

		if (jitter) {
			var distance = 2 * Math.pow(2 * random.nextGaussian(), 2);
			var angle = 360 * random.nextDouble();
			var direction = GeometryUtilsFX.translateByAngle(0, 0, angle, distance);
			TreeScaleAndAlignment.translate(direction.getX(), direction.getY(), nodePointMap);
		}
		return nodePointMap;
	}

	private static void drawTree(ProgressListener progress, PhyloTree tree, NodeArray<Point2D> nodePointMap, Collection<BitSet> consensusClusters,
								 DensiTreeDiagramType diagramType, int round, Canvas canvas, double lineWidth, Color edgeColor, Color otherColor) throws CanceledException {
		var gc = canvas.getGraphicsContext2D();

		gc.setLineWidth(lineWidth);

		try (var treeClusters = (round < 2 ? TreesUtils.extractClusters(tree) : null)) {
		gc.setStroke(edgeColor);

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
						gc.setStroke(otherColor);
					else
						gc.setStroke(edgeColor);
					if (round == 0 && useColor || round == 1 && !useColor)
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
				progress.checkForCancel();
			}
		}
		}
	}

	/**
	 * compute the greed consensus tree and the cycle to use for layout
	 *
	 * @param taxaBlock     input taxa
	 * @param trees         input trees
	 * @param consensusTree output consensus tree
	 * @return cycle
	 */
	private static int[] computeConsensusAndCycle(TaxaBlock taxaBlock, Collection<PhyloTree> trees, PhyloTree consensusTree) {
		// count all clusters:
		var clusterCountMap = new HashMap<BitSet, Integer>();
		for (var tree : trees) {
			try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
				for (var cluster : nodeClusterMap.values()) {
					var count = clusterCountMap.computeIfAbsent(cluster, k -> 0);
					clusterCountMap.put(cluster, count + 1);
				}
			}
		}

		// sort clusters by decreasing counts:
		var list = new ArrayList<>(clusterCountMap.entrySet());
		list.sort((a, b) -> {
			if (a.getValue() > b.getValue())
				return -1;
			else if (a.getValue() < b.getValue())
				return 1;
			else
				return Integer.compare(a.getKey().cardinality(), b.getKey().cardinality());
		});

		var consensusClusters = new HashSet<BitSet>();

		var pqTree = new PQTree(taxaBlock.getTaxaSet());

		// compute consensus clusters and add to PQ-tree
		for (var entry : list) {
			var cluster = entry.getKey();
			if (isCompatibleWithAll(cluster, consensusClusters)) {
				consensusClusters.add(cluster);
				if (!pqTree.accept(cluster)) { // figure out why this can happen
					System.err.println("Looks like a bug in the PQ-tree code, please contact the author (Daniel Huson) about this");
					pqTree.verbose = true;
					pqTree.accept(cluster);
					pqTree.verbose = false;
				}
			}
		}
		// add additional clusters to PQ-tree for better layout
		var count = 0;
		for (var entry : list) {
			if (count++ == 1000)
				break;
			var cluster = entry.getKey();
			if (!consensusClusters.contains(cluster))
				pqTree.accept(cluster);
		}

		// compute weights for consensus clusters
		var clusterWeightMap = new HashMap<BitSet, Double>();
		{
			// determine all trees that have same clusters as consensus:
			var treesWithConsensusTopology = new ArrayList<PhyloTree>();
			for (var tree : trees) {
				try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
				if (consensusClusters.equals(new HashSet<>(nodeClusterMap.values()))) {
					treesWithConsensusTopology.add(tree);
				}
				}
			}

			// compute weights, if trees with consensus clusters exist, use them, otherwise use all
			var countTrees = 0;
			for (var tree : !treesWithConsensusTopology.isEmpty() ? treesWithConsensusTopology : trees) {
				try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
					countTrees++;
					for (var entry : nodeClusterMap.entrySet()) {
						var v = entry.getKey();
						var cluster = entry.getValue();
						clusterWeightMap.put(cluster, clusterWeightMap.getOrDefault(cluster, 0.0) + (v.getInDegree() > 0 ? tree.getWeight(v.getFirstInEdge()) : 0));
					}
				}
			}
			if (countTrees > 0) {
				for (var cluster : clusterWeightMap.keySet()) {
					clusterWeightMap.put(cluster, clusterWeightMap.get(cluster) / countTrees);
				}
			}
		}

		// create consensus tree:
		ClusterPoppingAlgorithm.apply(consensusClusters, c -> (c == null ? 0.0 : clusterWeightMap.getOrDefault(c, 0.0)), consensusTree);
		var cycle = new ArrayList<Integer>();
		cycle.add(0);

		// extract circular ordering from pqTree:
		var ordering = pqTree.extractAnOrdering();
		cycle.addAll(ordering);
		cycle.addAll(CollectionUtils.difference(BitSetUtils.asSet(taxaBlock.getTaxaSet()), ordering));
		return cycle.stream().mapToInt(a -> a).toArray();
	}

	private static boolean nodeShapeOrLabelEntered = false;

	/**
	 * post process taxon labels so that they can be interacted with
	 */
	private static void postprocessLabels(Stage stage, TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel, Pane labelPane, ReadOnlyDoubleProperty fontScaleFactor) {
		var references = new ArrayList<>();

		for (var label : BasicFX.getAllRecursively(labelPane, RichTextLabel.class)) {
			if (label.getUserData() instanceof Integer t) {
				var taxon = taxaBlock.get(t);
				//label.setOnMousePressed(mousePressedHandler);
				//label.setOnMouseDragged(mouseDraggedHandler);
				final EventHandler<MouseEvent> mouseClickedHandler = e -> {
					if (e.isStillSincePress()) {
						if (!e.isShiftDown() && ProgramProperties.isDesktop())
							taxonSelectionModel.clearSelection();
						taxonSelectionModel.toggleSelection(taxon);
					}
					e.consume();
				};
				label.setOnContextMenuRequested(InteractionSetup.createNodeContextMenuHandler(stage, new UndoManager(), label, null));
				label.setOnMouseClicked(mouseClickedHandler);

				if (taxonSelectionModel.isSelected(taxon)) {
					label.setEffect(SelectionEffectBlue.getInstance());
				}
				DraggableUtils.setupDragMouseTranslate(label);

				InvalidationListener invalidationListener = e -> label.setText(taxon.getDisplayLabelOrName());
				taxon.displayLabelProperty().addListener(new WeakInvalidationListener(invalidationListener));
				references.add(invalidationListener); // avoid garbage collection

				ChangeListener<Number> fontScaleChangeListener = (v, o, n) -> label.setFontSize(label.getFontSize() / o.doubleValue() * n.doubleValue());
				fontScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));
				references.add(fontScaleChangeListener);

				label.setFontSize(label.getFontSize() * fontScaleFactor.get());
			}
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

	public static void computeTriangularLayout(PhyloTree tree, Averaging averaging, int[] taxon2pos, NodeArray<Point2D> nodePointMap) {
		if (false) {
			computeTriangularTopologyLayout(tree, taxon2pos, nodePointMap);
			return;
		}

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
				if (averaging == Averaging.ChildAverage) {
					{
						LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
							if (tree.isLeaf(v) || tree.isLsaLeaf(v)) {
								nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), taxon2pos[tree.getTaxon(v)]));
								firstLastLeafBelowMap.put(v, new Pair<>(v, v));
							} else {
								var firstChildBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
										.min(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
								var lastChildBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
										.max(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
								var y = 0.5 * (nodePointMap.get(firstChildBelow).getY() + nodePointMap.get(lastChildBelow).getY());
								nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), y));
								firstLastLeafBelowMap.put(v, new Pair<>(firstChildBelow, lastChildBelow));
							}
						});
					}
				} else {
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

	public PhyloTree getConsensusTree() {
		return consensusTree;
	}
}


