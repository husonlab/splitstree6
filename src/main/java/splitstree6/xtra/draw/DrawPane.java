/*
 *  DrawMain.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.SetChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.fx.graph.GraphFX;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.GrayOutlineEffect;
import jloda.fx.util.Icebergs;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.util.SelectionEffect;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.algorithms.IsDAG;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;

import java.util.ArrayList;

public class DrawPane extends Pane {
	private final PhyloTree network;
	private final GraphFX<PhyloTree> networkFX;

	private final SelectionModel<Node> nodeSelectionModel;
	private final SelectionModel<Edge> edgeSelectionModel;

	private final Group icebergsGroup = new Group();
	private final Group edgesGroup = new Group();
	private final Group nodesGroup = new Group();
	private final Group nodeLabelsGroup = new Group();
	private final Group otherGroup = new Group();

	private final DoubleProperty tolerance = new SimpleDoubleProperty(this, "tolerance", 5);

	private final BooleanProperty valid = new SimpleBooleanProperty(this, "isValidNetwork", false);

	private final Group world = new Group();

	private final UndoManager undoManager = new UndoManager();

	public DrawPane() {
		Icebergs.setEnabled(true);

		network = new PhyloTree();
		networkFX = new GraphFX<>(network);
		nodeSelectionModel = new SetSelectionModel<>();
		edgeSelectionModel = new SetSelectionModel<>();

		networkFX.lastUpdateProperty().addListener(e -> {
			valid.set(network.getNumberOfNodes() > 0 && network.nodeStream().filter(v -> v.getInDegree() == 0).count() == 1
					  && IsDAG.apply(network) && network.nodeStream().filter(Node::isLeaf).allMatch(v -> network.getLabel(v) != null));

			nodeSelectionModel.getSelectedItems().removeAll(nodeSelectionModel.getSelectedItems().stream().filter(a -> a.getOwner() == null).toList());
			edgeSelectionModel.getSelectedItems().removeAll(edgeSelectionModel.getSelectedItems().stream().filter(a -> a.getOwner() == null).toList());

			for (var v : network.nodes()) {
				var shape = (Shape) v.getData();
				shape.getStyleClass().add("graph-node");
				shape.setStrokeWidth(v.getInDegree() == 0 ? 2 : 1);
				shape.setFill(Color.WHITE);

				if (shape instanceof Circle circle)
					circle.setRadius(v.getInDegree() == 0 || v.getOutDegree() == 0 ? 3 : 1.5);

				shape.setStroke(Color.BLACK);

				SelectionManager.setupNodeSelection(v, shape, nodeSelectionModel, edgeSelectionModel);
			}

			for (var edge : network.edges()) {
				var path = (Path) edge.getData();
				SelectionManager.setupEdgeSelection(edge, path, nodeSelectionModel, edgeSelectionModel);
			}
		});

		nodeSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Node>) e -> {
			if (e.wasAdded()) {
				var node = e.getElementAdded();
				if (node.getOwner() != null)
					((Shape) node.getData()).setEffect(SelectionEffect.create(Color.GOLD));
			} else if (e.wasRemoved()) {
				var node = e.getElementRemoved();
				if (node.getOwner() != null)
					((Shape) node.getData()).setEffect(null);
			}
		});

		edgeSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Edge>) e -> {
			if (e.wasAdded()) {
				var edge = e.getElementAdded();
				if (edge.getOwner() != null)
					((Path) edge.getData()).setEffect(SelectionEffect.create(Color.GOLD));
			} else if (e.wasRemoved()) {
				var edge = e.getElementRemoved();
				if (edge.getOwner() != null)
					((Path) edge.getData()).setEffect(null);
			}
		});

		world.getChildren().addAll(icebergsGroup, edgesGroup, nodesGroup, nodeLabelsGroup, otherGroup);
		getChildren().add(world);

		setupMouseInteraction(this);

		setStyle("-fx-background-color: lightblue;");

		SelectionManager.setupPaneSelection(this, nodeSelectionModel, edgeSelectionModel);
	}


	public void clear() {
		network.clear();
		for (var child : world.getChildren()) {
			if (child instanceof Group group)
				group.getChildren().clear();
		}
		undoManager.clear();
	}

	private static double mouseDownX;
	private static double mouseDownY;

	private static double mouseX;
	private static double mouseY;

	private static Path path;
	private static Point2D pathStart;
	private static Point2D pathEnd;

	private static Shape hitShape;
	private static Path hitPath;

	private static boolean inDragSelected;
	private static boolean wasDragged;

	private static final ArrayList<PathElement> internalElements = new ArrayList<>();

	private void setupMouseInteraction(Pane pane) {

		pane.setOnMousePressed(e -> {
			mouseX = e.getScreenX();
			mouseY = e.getScreenY();
			internalElements.clear();
			path = null;

			hitPath = null;
			hitShape = null;
			inDragSelected = false;
			wasDragged = false;
			mouseDownX = e.getScreenX();
			mouseDownY = e.getScreenY();

			{
				var location = pane.screenToLocal(mouseX, mouseY);

				var nodePointPair = DrawUtils.snapToExistingNode(location, nodesGroup, getTolerance());
				if (nodePointPair.getKey() != null && nodeSelectionModel.isSelected(nodePointPair.getKey())) {
					inDragSelected = true;
				}
			}
			e.consume();
		});

		pane.setOnMouseDragged(e -> {
			var previous = pane.screenToLocal(mouseX, mouseY);

			if (inDragSelected) {
				var location = pane.screenToLocal(e.getScreenX(), e.getScreenY());
				var delta = new Point2D(location.getX() - previous.getX(), location.getY() - previous.getY());
				if (!DrawUtils.hasCollisions(network, nodeSelectionModel.getSelectedItems(), delta.getX(), delta.getY())) {
					for (var v : nodeSelectionModel.getSelectedItems()) {
						var shape = (Shape) v.getData();
						shape.setTranslateX(shape.getTranslateX() + delta.getX());
						shape.setTranslateY(shape.getTranslateY() + delta.getY());
					}
					MoveEdges.apply(network, nodeSelectionModel.getSelectedItems(), delta.getX(), delta.getY());
					mouseX = e.getScreenX();
					mouseY = e.getScreenY();
					wasDragged = true;
				}
				e.consume();
				return;
			}

			var nodePointPair = DrawUtils.snapToExistingNode(previous, nodesGroup, getTolerance());

			if (nodePointPair.getKey() != null) {
				if (hitShape != null) {
					hitShape.setEffect(null);
				}
				hitShape = (Shape) nodePointPair.getKey().getData();
				hitShape.setEffect(GrayOutlineEffect.getInstance());
			} else {
				var paths = edgesGroup.getChildren().stream().filter(n -> n instanceof Path).map(n -> (Path) n).toList();
				var pointOnPath = PathUtils.pointOnPath(previous, paths, getTolerance());
				if (pointOnPath != null) {
					if (hitPath != null)
						hitPath.setEffect(null);
					hitPath = pointOnPath.getFirst();
					hitPath.setEffect(GrayOutlineEffect.getInstance());
				}
			}

			if (path == null) {
				path = new Path();
				icebergsGroup.getChildren().add(Icebergs.create(path, true));

				previous = snapToExisting(previous, getTolerance());
				pathStart = previous;

				path.getElements().add(new MoveTo(previous.getX(), previous.getY()));
			}
			var point = pane.screenToLocal(e.getScreenX(), e.getScreenY());
			//point=snapToExisting(point,pane,path,tolerance.doubleValue());

			if (point.distance(previous) > getTolerance()) {
				path.getElements().add(new LineTo(point.getX(), point.getY()));
				if (path.getElements().size() == 2) {
					// previous = snapToExisting(previous, tolerance.get());
					// var start = new Circle(previous.getX(), previous.getY(), 3);
					var end = new Circle(3);
					path.getElements().addListener((InvalidationListener) a -> {
						if (!path.getElements().isEmpty() && path.getElements().get(path.getElements().size() - 1) instanceof LineTo lineTo) {
							end.setCenterX(lineTo.getX());
							end.setCenterY(lineTo.getY());
						}
					});
					edgesGroup.getChildren().add(path);
				}
				mouseX = e.getScreenX();
				mouseY = e.getScreenY();
			}

			RunAfterAWhile.apply(path, () -> Platform.runLater(() -> {
				if (path != null) {
					var first = path.getElements().get(0);
					var last = path.getElements().get(path.getElements().size() - 1);
					var middle = DrawUtils.asRectangular(path);
					internalElements.clear();
					for (var element : path.getElements()) {
						if (element != first && element != last) {
							internalElements.add(element);
						}
					}
					if (middle != null) {
						path.getElements().setAll(first, middle, last);
					} else {
						path.getElements().setAll(first, last);
					}
				}
			}), 1000);

			if (!internalElements.isEmpty()) {
				var first = (MoveTo) path.getElements().get(0);
				var last = (LineTo) path.getElements().get(path.getElements().size() - 1);
				path.getElements().setAll(first);
				path.getElements().addAll(internalElements);
				path.getElements().add(last);
				internalElements.clear();
			}
			e.consume();
		});

		pane.setOnMouseReleased(e -> {
			if (wasDragged) {
				var mouseDown = pane.screenToLocal(mouseDownX, mouseDownY);
				var location = pane.screenToLocal(e.getScreenX(), e.getScreenY());
				var delta = new Point2D(location.getX() - mouseDown.getX(), location.getY() - mouseDown.getY());
				if (delta.magnitude() > 0) {
					undoManager.add("drag", () -> {
						for (var v : nodeSelectionModel.getSelectedItems()) {
							var shape = (Shape) v.getData();
							shape.setTranslateX(shape.getTranslateX() - delta.getX());
							shape.setTranslateY(shape.getTranslateY() - delta.getY());
						}
						MoveEdges.apply(network, nodeSelectionModel.getSelectedItems(), -delta.getX(), -delta.getY());
					}, () -> {
						for (var v : nodeSelectionModel.getSelectedItems()) {
							var shape = (Shape) v.getData();
							shape.setTranslateX(shape.getTranslateX() + delta.getX());
							shape.setTranslateY(shape.getTranslateY() + delta.getY());
						}
						MoveEdges.apply(network, nodeSelectionModel.getSelectedItems(), delta.getX(), delta.getY());
					});
				}
			}
			if (path != null) {
				RunAfterAWhile.apply(path, null);

				if (!e.isStillSincePress()) {

					pathEnd = pane.screenToLocal(e.getScreenX(), e.getScreenY());

					pathEnd = snapToExisting(pathEnd, tolerance.get());
					path.getElements().add(new LineTo(pathEnd.getX(), pathEnd.getY()));


					if (path != null && !path.getElements().isEmpty()) {
						if (pathStart.distance(pathEnd) >= tolerance.get()) {
							undoManager.doAndAdd(new AddEdgeCommand(pathStart, pathEnd, path, this));
						} else edgesGroup.getChildren().remove(path);
					}

					if (false) {
						var paths = edgesGroup.getChildren().stream().filter(n -> n instanceof Path).map(n -> (Path) n).toList();
						var intersections = PathUtils.allIntersections(path, paths, false);
						for (var intersection : intersections) {
							var circle = new Circle(intersection.getSecond().getX(), intersection.getSecond().getY(), 3);
							circle.setFill(Color.TRANSPARENT);
							circle.setStroke(Color.RED);
							otherGroup.getChildren().add(circle);
						}
					}

					path = null;

					if (hitPath != null) {
						hitPath.setEffect(null);
						hitPath = null;
					}
					if (hitShape != null) {
						hitShape.setEffect(null);
						hitShape = null;
					}
				}
			}
			e.consume();
		});
	}

	public Point2D snapToExisting(Point2D point, double tolerance) {
		var pair = DrawUtils.snapToExistingNode(point, nodesGroup, tolerance);
		if (pair.getKey() != null)
			return pair.getValue();
		else
			return DrawUtils.snapToExistingEdge(point, edgesGroup, tolerance).getValue();
	}

	public String toBracketString(boolean showWeights) {
		var root = network.nodeStream().filter(v -> v.getInDegree() == 0).findAny();
		if (root.isPresent()) {
			network.setRoot(root.get());
			network.edgeStream().forEach(f -> {
				var reticulate = (f.getTarget().getInDegree() > 1);
				network.setWeight(f, reticulate ? 0 : 1);
				network.setReticulate(f, reticulate);
			});
			return network.toBracketString(showWeights) + ";";
		} else return "?";
	}

	public double getTolerance() {
		return tolerance.get();
	}

	public DoubleProperty toleranceProperty() {
		return tolerance;
	}

	public void setTolerance(double tolerance) {
		this.tolerance.set(tolerance);
	}

	public PhyloTree getNetwork() {
		return network;
	}

	public GraphFX<PhyloTree> getNetworkFX() {
		return networkFX;
	}

	public Group getIcebergsGroup() {
		return icebergsGroup;
	}

	public Group getEdgesGroup() {
		return edgesGroup;
	}

	public Group getNodesGroup() {
		return nodesGroup;
	}

	public Group getNodeLabelsGroup() {
		return nodeLabelsGroup;
	}

	public Group getOtherGroup() {
		return otherGroup;
	}

	public boolean getValid() {
		return valid.get();
	}

	public BooleanProperty validProperty() {
		return valid;
	}

	public UndoManager getUndoManager() {
		return undoManager;
	}

	public SelectionModel<Node> getNodeSelectionModel() {
		return nodeSelectionModel;
	}

	public SelectionModel<Edge> getEdgeSelectionModel() {
		return edgeSelectionModel;
	}

	public Node createNode(Shape shape) {
		var v = network.newNode();
		v.setData(shape);
		shape.setUserData(v);
		if (!nodesGroup.getChildren().contains(shape))
			nodesGroup.getChildren().add(shape);
		return v;
	}

	public Node createNode(Shape shape, int recycledId) {
		var v = network.newNode(null, recycledId);
		v.setData(shape);
		shape.setUserData(v);
		if (!nodesGroup.getChildren().contains(shape))
			nodesGroup.getChildren().add(shape);
		return v;
	}

	public void deleteNode(Node v) {
		for (var e : IteratorUtils.asList(v.adjacentEdges())) {
			deleteEdge(e);
		}
		nodeSelectionModel.getSelectedItems().remove(v);
		var shape = (Shape) v.getData();
		nodesGroup.getChildren().remove(shape);
		if (v.getInfo() instanceof javafx.scene.Node label)
			nodeLabelsGroup.getChildren().remove(label);
		network.deleteNode(v);
	}


	public Edge createEdge(Node v, Node w, Path path) {
		var e = network.newEdge(v, w);
		e.setData(path);
		path.setUserData(e);
		if (!edgesGroup.getChildren().contains(path))
			edgesGroup.getChildren().add(path);
		return e;
	}

	public Edge createEdge(Node v, Node w, Path path, int recycledId) {
		var e = network.newEdge(v, w, null, recycledId);
		e.setData(path);
		path.setUserData(e);
		if (!edgesGroup.getChildren().contains(path))
			edgesGroup.getChildren().add(path);
		return e;
	}

	public void deleteEdge(Edge e) {
		if (e.getOwner() != null) {
			edgeSelectionModel.getSelectedItems().remove(e);
			if (e.getData() instanceof Path p)
				edgesGroup.getChildren().remove(p);
		}
	}
}
