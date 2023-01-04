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
import jloda.fx.control.ProgressPane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.*;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.graph.algorithms.PQTree;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.RadialLabelLayout;
import splitstree6.view.trees.InteractionSetup;
import splitstree6.window.MainWindow;

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

	public void apply(Bounds targetBounds, List<PhyloTree> trees0, StackPane parent, DensiTreeDiagramType diagramType, boolean jitter,
					  boolean colorIncompatibleEdges, double horizontalZoomFactor, double verticalZoomFactor, ReadOnlyDoubleProperty fontScaleFactor,
					  ReadOnlyBooleanProperty showTrees, ReadOnlyBooleanProperty showConsensus,
					  double lineWidth, Color edgeColor, Color otherColor) {
		radialLabelLayout.getItems().clear();

		var trees = trees0.stream().filter(t -> !t.getName().equals("STATE_0")).collect(Collectors.toList());

		RunAfterAWhile.applyInFXThread(parent, () -> {
			parent.getChildren().clear();

			var canvas0 = new Canvas(targetBounds.getWidth(), targetBounds.getHeight());
			var canvas1 = (colorIncompatibleEdges ? new Canvas(targetBounds.getWidth(), targetBounds.getHeight()) : null);

			ChangeListener<Boolean> listener = (v, o, n) -> {
				canvas0.setVisible(n);
				if (canvas1 != null)
					canvas0.setVisible(n);
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

				var cycle = computeConsensusAndCycle(mainWindow.getWorkingTaxa(), consensusTree, trees);
				final var taxon2pos = new int[cycle.length];
				for (var pos = 1; pos < cycle.length; pos++) {
					taxon2pos[cycle[pos]] = pos;
				}
				var lastTaxon = cycle[cycle.length - 1];

				Point2D consensusScale;
				if (diagramType.isRadialOrCircular()) {
					computeRadialLayout(consensusTree, taxon2pos, mainWindow.getWorkingTaxa().getNtax(), lastTaxon, consensusNodePointMap, nodeAngleMap);
					consensusScale = LayoutUtils.scaleToBounds(bounds, consensusNodePointMap);
				} else {
					computeTriangularLayout(consensusTree, taxon2pos, consensusNodePointMap);
					consensusScale = LayoutUtils.scaleToBounds(bounds, consensusNodePointMap);
				}
				var consensusCenter = computeCenter(consensusNodePointMap.values());
				Platform.runLater(() -> {
					drawConsensus(mainWindow, consensusTree, consensusNodePointMap, nodeAngleMap, diagramType, getRadialLabelLayout(), showConsensus, pane);
					ProgramExecutorService.submit(consensusNodePointMap::close);
					ProgramExecutorService.submit(nodeAngleMap::close);
				});

				var random = new Random(666);
				var start = System.currentTimeMillis();
				if (colorIncompatibleEdges) {
					for (PhyloTree tree : trees) {
						var consensusClusters = TreesUtilities.extractClusters(consensusTree).values();
						final NodeArray<Point2D> nodePointMap = computeTreeCoordinates(mainWindow.getWorkingTaxa(), tree, taxon2pos, lastTaxon, consensusCenter, consensusScale,
								diagramType, jitter, random);
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
						final NodeArray<Point2D> nodePointMap = computeTreeCoordinates(mainWindow.getWorkingTaxa(), tree, taxon2pos, lastTaxon, consensusCenter, consensusScale,
								diagramType, jitter, random);
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
				RunAfterAWhile.applyInFXThread(getRadialLabelLayout(), () -> getRadialLabelLayout().layoutLabels());
			});
			service.restart();
		});
	}

	public RadialLabelLayout getRadialLabelLayout() {
		return radialLabelLayout;
	}

	private static void drawConsensus(MainWindow mainWindow, PhyloTree consensusTree, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap,
									  DensiTreeDiagramType diagramType, RadialLabelLayout radialLabelLayout, ReadOnlyBooleanProperty showConsensus, Pane labelPane) {

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
				case RectangularPhylogram -> new Polyline(p.getX(), p.getY(), p.getX(), q.getY(), q.getX(), q.getY());
				case RoundedPhylogram -> new Path(new MoveTo(p.getX(), p.getY()), new QuadCurveTo(p.getX(), q.getY(), q.getX(), q.getY()));
			};
			e.setData(shape);
			shape.getStyleClass().add("graph-special-edge");
			edgesGroup.getChildren().add(shape);
			shape.setOnMouseClicked(a -> {
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
			shape.setOnMouseEntered(a -> {
				if (!a.isStillSincePress()) {
					shape.setStrokeWidth(5);
				}
			});
			shape.setOnMouseExited(a -> {
				if (!a.isStillSincePress()) {
					shape.setStrokeWidth(1);
				}
			});
		}
		// implement show/hide consensus tree:
		ChangeListener<Boolean> showConsensusListener = (v, o, n) -> {
			edgesGroup.setVisible(n);
		};
		edgesGroup.setUserData(showConsensusListener); // keep it alive
		showConsensus.addListener(new WeakChangeListener<>(showConsensusListener));
		edgesGroup.setVisible(showConsensus.get());

		var labelGroup = new Group();

		double maxLeafX = Double.MIN_VALUE;
		if (!diagramType.isRadialOrCircular()) {
			maxLeafX = IteratorUtils.asStream(consensusTree.leaves()).mapToDouble(v -> nodePointMap.get(v).getX()).max().orElse(maxLeafX);
		}

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
			var x = (maxLeafX > Double.MIN_VALUE ? maxLeafX + 3 : point.getX());

			radialLabelLayout.addItem(new SimpleDoubleProperty(x), new SimpleDoubleProperty(point.getY()), angle,
					label.widthProperty(), label.heightProperty(), a -> label.setTranslateX(x + a), a -> label.setTranslateY(point.getY() + a));
			labelGroup.getChildren().add(label);
		}
		labelPane.getChildren().addAll(edgesGroup, labelGroup);
	}

	private static NodeArray<Point2D> computeTreeCoordinates(TaxaBlock taxaBlock, PhyloTree tree, int[] taxon2pos, int lastTaxon,
															 Point2D consensusCenter, Point2D consensusScale, DensiTreeDiagramType diagramType,
															 boolean jitter, Random random) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

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
		return nodePointMap;
	}

	private static void drawTree(ProgressListener progress, PhyloTree tree, NodeArray<Point2D> nodePointMap, Collection<BitSet> consensusClusters,
								 DensiTreeDiagramType diagramType, int round, Canvas canvas, double lineWidth, Color edgeColor, Color otherColor) throws CanceledException {
		var gc = canvas.getGraphicsContext2D();

		gc.setLineWidth(lineWidth);

		var treeClusters = (round < 2 ? TreesUtilities.extractClusters(tree) : null);

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

	private static int[] computeConsensusAndCycle(TaxaBlock taxaBlock, PhyloTree consensusTree, Collection<PhyloTree> trees) {
		var clusterCountWeightMap = new HashMap<BitSet, CountWeight>();
		for (var tree : trees) {
			try (NodeArray<BitSet> clusterMap = tree.newNodeArray()) {
				tree.postorderTraversal(v -> {
					var cluster = new BitSet();
					if (v.isLeaf()) {
						cluster.set(tree.getTaxon(v));
					} else {
						for (var w : v.children()) {
							cluster.or(clusterMap.get(w));
						}
					}
					clusterMap.put(v, cluster);
					var clusterCountWeight = clusterCountWeightMap.getOrDefault(cluster, new CountWeight(0, 0.0));
					var weight = (v.getFirstInEdge() != null ? tree.getWeight(v.getFirstInEdge()) : 0);
					clusterCountWeightMap.put(cluster, new CountWeight(clusterCountWeight.count() + 1, clusterCountWeight.weight() + weight));
				});
			}
		}
		var list = new ArrayList<CountWeightCluster>();
		for (var entry : clusterCountWeightMap.entrySet()) {
			var cw = entry.getValue();
			list.add(new CountWeightCluster(cw.count(), cw.weight(), entry.getKey()));
		}
		list.sort((a, b) -> -Integer.compare(a.count(), b.count()));

		var consensusClusterWeightMap = new HashMap<BitSet, Double>();

		var pqTree = new PQTree(taxaBlock.getTaxaSet());

		// compute consensus clusters and add to PQ-tree
		for (var cwc : list) {
			if (cwc.count() > 0.5 * trees.size() || isCompatibleWithAll(cwc.cluster(), consensusClusterWeightMap.keySet())) {
				consensusClusterWeightMap.put(cwc.cluster(), cwc.count() > 0 ? cwc.weight() / cwc.count() : 0.0);
				if (!pqTree.accept(cwc.cluster())) { // figure out why this can happen
					System.err.println("Looks like a bug in the PQ-tree code, please contact the author (Daniel Huson) about this");
					pqTree.verbose = true;
					pqTree.accept(cwc.cluster());
					pqTree.verbose = false;
				}
			}
		}
		// add additional clusters to PQ-tree for better layout
		var count = 0;
		for (var cwc : list) {
			if (count++ == 1000)
				break;
			if (!consensusClusterWeightMap.containsKey(cwc.cluster()))
				pqTree.accept(cwc.cluster());
		}

		// create consensus tree:
		ClusterPoppingAlgorithm.apply(consensusClusterWeightMap.keySet(), c -> (c == null ? 0.0 : consensusClusterWeightMap.getOrDefault(c, 0.0)), consensusTree);
		var cycle = new ArrayList<Integer>();
		cycle.add(0);
		var ordering = pqTree.extractAnOrdering();
		cycle.addAll(ordering);
		cycle.addAll(CollectionUtils.difference(BitSetUtils.asSet(taxaBlock.getTaxaSet()), ordering));
		return cycle.stream().mapToInt(a -> a).toArray();
	}

	private static Point2D computeCenter(Collection<Point2D> points) {
		if (points.size() == 0)
			return new Point2D(0, 0);
		else if (true) {
			return new Point2D(points.stream().mapToDouble(Point2D::getX).sum() / points.size(),
					points.stream().mapToDouble(Point2D::getY).sum() / points.size());
		} else {
			var xMid = 0.5 * (points.stream().mapToDouble(Point2D::getX).max().orElse(0.0) + points.stream().mapToDouble(Point2D::getX).min().orElse(0.0));
			var yMid = 0.5 * (points.stream().mapToDouble(Point2D::getY).max().orElse(0.0) + points.stream().mapToDouble(Point2D::getY).min().orElse(0.0));
			return new Point2D(xMid, yMid);
		}
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

	private static record CountWeight(int count, double weight) {
	}

	private static record CountWeightCluster(int count, double weight, BitSet cluster) {
	}
}


