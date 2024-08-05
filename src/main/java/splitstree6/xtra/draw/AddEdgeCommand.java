/*
 *  AddEdgeCommand.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.geometry.Point2D;
import javafx.scene.shape.*;
import jloda.fx.undo.UndoableRedoableCommand;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.Basic;
import jloda.util.Triplet;

public class AddEdgeCommand extends UndoableRedoableCommand {
	private final Runnable undo;
	private final Runnable redo;

	private int sourceNodeId = -1;
	private boolean sourceNodeCreated = false;

	private int sourceEdgeId = -1;
	private Path sourceEdgePath = null;
	private int sourceEdgeSourceId = -1;
	private int sourceEdgeTargetId = -1;

	private int sourceAddedEdge1Id = -1;
	private int sourceAddedNodeId = -1;
	private int sourceAddedEdge2Id = -1;

	private int targetNodeId = -1;
	private boolean targetNodeCreated = false;

	private int targetEdgeId = -1;
	private Path targetEdgePath = null;
	private int targetEdgeSourceId = -1;
	private int targetEdgeTargetId = -1;

	private int targetAddedEdge1Id = -1;
	private int targetAddedNodeId = -1;
	private int targetAddedEdge2Id = -1;

	private int createdEdgeId = -1;


	public AddEdgeCommand(Point2D start, Point2D end, Path newPath, DrawPane drawPane) {
		super("add edge");

		var network = drawPane.getNetwork();
		var tolerance = drawPane.getTolerance();
		var nodesGroup = drawPane.getNodesGroup();
		var edgesGroup = drawPane.getEdgesGroup();

		// 1. does start lie on some existing node?
		{
			var v = DrawUtils.snapToExistingNode(start, nodesGroup, tolerance).getKey();
			if (v != null)
				sourceNodeId = v.getId();
		}

		// 2. does start lie on some existing edge?
		if (sourceNodeId == -1) {
			var e = DrawUtils.snapToExistingEdge(start, edgesGroup, tolerance).getKey();
			if (e != null) {
				sourceEdgeId = e.getId();
				sourceEdgeSourceId = e.getSource().getId();
				sourceEdgeTargetId = e.getTarget().getId();
				sourceEdgePath = (Path) e.getData();
			}
		}

		// 3. does end lie on existing node?
		{
			var v = DrawUtils.snapToExistingNode(end, nodesGroup, tolerance).getKey();
			if (v != null)
				targetNodeId = v.getId();
		}

		// 2. does end lie on some existing edge?
		if (targetNodeId == -1) {
			var e = DrawUtils.snapToExistingEdge(end, edgesGroup, tolerance).getKey();
			if (e != null) {
				targetEdgeId = e.getId();
				targetEdgeSourceId = e.getSource().getId();
				targetEdgeTargetId = e.getTarget().getId();
				targetEdgePath = (Path) e.getData();
			}
		}

		redo = () -> {
			try {
				Node source;
				if (sourceNodeId != -1 && network.findNodeById(sourceNodeId) != null) {
					source = network.findNodeById(sourceNodeId);
				} else if (sourceEdgeId != -1) {
					var sourceEdge = network.findEdgeById(sourceEdgeId);
					var triplet = splitExistingEdge(start, sourceEdge, sourceEdgePath, drawPane);
					if (triplet == null)
						throw new NullPointerException();
					edgesGroup.getChildren().remove(sourceEdgePath);

					drawPane.getEdgeSelectionModel().getSelectedItems().remove(sourceEdge);
					network.deleteEdge(sourceEdge);

					source = triplet.getFirst();
					sourceAddedNodeId = source.getId();

					var e1 = triplet.getSecond();
					sourceAddedEdge1Id = e1.getId();

					var e2 = triplet.getThird();
					sourceAddedEdge2Id = e2.getId();
				} else {
					var shape = new Circle(3);
					source = drawPane.createNode(shape);
					shape.setTranslateX(start.getX());
					shape.setTranslateY(start.getY());
					sourceNodeId = source.getId();
					sourceNodeCreated = true;
				}
				Node target;
				if (targetNodeId != -1 && network.findNodeById(targetNodeId) != null) {
					target = network.findNodeById(targetNodeId);
				} else if (targetEdgeId != -1) {
					var targetEdge = network.findEdgeById(targetEdgeId);
					var triplet = splitExistingEdge(end, targetEdge, targetEdgePath, drawPane);
					if (triplet == null)
						throw new NullPointerException();

					edgesGroup.getChildren().remove(targetEdgePath);
					drawPane.getEdgeSelectionModel().getSelectedItems().remove(targetEdge);
					network.deleteEdge(targetEdge);

					target = triplet.getFirst();
					targetAddedNodeId = target.getId();

					var e1 = triplet.getSecond();
					targetAddedEdge1Id = e1.getId();

					var e2 = triplet.getThird();
					targetAddedEdge2Id = e2.getId();
				} else {
					var shape = new Circle(3);
					target = drawPane.createNode(shape);
					shape.setTranslateX(end.getX());
					shape.setTranslateY(end.getY());
					targetNodeId = target.getId();
					targetNodeCreated = true;
				}

				var edge = drawPane.createEdge(source, target, newPath);
				createdEdgeId = edge.getId();
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		};
		undo = () -> {
			try {
				// remove the edge
				{
					var e = network.findEdgeById(createdEdgeId);
					var path = (Path) e.getData();
					edgesGroup.getChildren().remove(path);
					drawPane.getEdgeSelectionModel().getSelectedItems().remove(e);
					network.deleteEdge(e);
				}

				if (sourceNodeCreated) {
					var v = network.findNodeById(sourceNodeId);
					var shape = (Shape) v.getData();
					nodesGroup.getChildren().remove(shape);
					if (v.getInfo() instanceof javafx.scene.Node node)
						drawPane.getNodeLabelsGroup().getChildren().remove(node);
					drawPane.getNodeSelectionModel().getSelectedItems().remove(v);
					drawPane.deleteNode(v);
				} else if (sourceAddedEdge1Id != -1) {
					var e1 = network.findEdgeById(sourceAddedEdge1Id);
					var path1 = (Path) e1.getData();
					edgesGroup.getChildren().remove(path1);
					drawPane.getEdgeSelectionModel().getSelectedItems().remove(e1);
					network.deleteEdge(e1);
					var e2 = network.findEdgeById(sourceAddedEdge2Id);
					var path2 = (Path) e2.getData();
					edgesGroup.getChildren().remove(path2);
					drawPane.getEdgeSelectionModel().getSelectedItems().remove(e2);
					network.deleteEdge(e2);

					var v = network.findNodeById(sourceAddedNodeId);
					var shape = (Shape) v.getData();
					nodesGroup.getChildren().remove(shape);
					if (v.getInfo() instanceof javafx.scene.Node node)
						drawPane.getNodeLabelsGroup().getChildren().remove(node);
					drawPane.getNodeSelectionModel().getSelectedItems().remove(v);
					drawPane.deleteNode(v);

					drawPane.createEdge(network.findNodeById(sourceEdgeSourceId), network.findNodeById(sourceEdgeTargetId), sourceEdgePath, sourceEdgeId);
				}

				if (targetNodeCreated) {
					var v = network.findNodeById(targetNodeId);
					var shape = (Shape) v.getData();
					nodesGroup.getChildren().remove(shape);
					if (v.getInfo() instanceof javafx.scene.Node node)
						drawPane.getNodeLabelsGroup().getChildren().remove(node);
					drawPane.getNodeSelectionModel().getSelectedItems().remove(v);
					drawPane.deleteNode(v);
				} else if (targetAddedEdge1Id != -1) {
					var e1 = network.findEdgeById(targetAddedEdge1Id);
					var path1 = (Path) e1.getData();
					edgesGroup.getChildren().remove(path1);
					drawPane.getEdgeSelectionModel().getSelectedItems().remove(e1);
					network.deleteEdge(e1);
					var e2 = network.findEdgeById(targetAddedEdge2Id);
					var path2 = (Path) e2.getData();
					edgesGroup.getChildren().remove(path2);
					drawPane.getEdgeSelectionModel().getSelectedItems().remove(e2);
					network.deleteEdge(e2);

					var v = network.findNodeById(targetAddedNodeId);
					var shape = (Shape) v.getData();
					nodesGroup.getChildren().remove(shape);
					if (v.getInfo() instanceof javafx.scene.Node node)
						drawPane.getNodeLabelsGroup().getChildren().remove(node);
					drawPane.getNodeSelectionModel().getSelectedItems().remove(v);
					drawPane.deleteNode(v);

					drawPane.createEdge(network.findNodeById(targetEdgeSourceId), network.findNodeById(targetEdgeTargetId), targetEdgePath, targetEdgeId);
				}
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		};
	}

	private Triplet<Node, Edge, Edge> splitExistingEdge(Point2D point, Edge edge, Path path, DrawPane drawPane) {
		var index = DrawUtils.hitPathElement(point, path, drawPane.getTolerance());
		if (index != -1) {
			var sourceLocation = new Point2D(((Shape) edge.getSource().getData()).getTranslateX(), ((Shape) edge.getSource().getData()).getTranslateY());
			var path1 = new Path();
			Node start1 = null;
			for (var i = 0; i <= index; i++) {
				var other = DrawUtils.getLocation(path, i);
				if (other != null) {
					if (i == 0) {
						start1 = (other.distance(sourceLocation) <= drawPane.getTolerance() ? edge.getSource() : edge.getTarget());
						path1.getElements().add(new MoveTo(other.getX(), other.getY()));
					} else
						path1.getElements().add(new LineTo(other.getX(), other.getY()));
				}
			}
			var path2 = new Path();

			Node start2 = edge.getOpposite(start1);
			for (var i = index; i < path.getElements().size(); i++) {
				var other = DrawUtils.getLocation(path, i);
				if (other != null) {
					if (i == index)
						path2.getElements().add(new MoveTo(other.getX(), other.getY()));
					else
						path2.getElements().add(new LineTo(other.getX(), other.getY()));
				}
			}

			var shape = new Circle(3);
			var v = drawPane.createNode(shape);
			{
				shape.setTranslateX(point.getX());
				shape.setTranslateY(point.getY());
			}

			Edge edge1;
			Edge edge2;
			if (start1 == edge.getSource()) {
				edge1 = drawPane.createEdge(start1, v, path1);
				edge2 = drawPane.createEdge(v, start2, path2);
			} else {
				edge1 = drawPane.createEdge(v, start1, path1);
				edge2 = drawPane.createEdge(start2, v, path2);
			}
			return new Triplet<>(v, edge1, edge2);
		} else return null;
	}


	@Override
	public boolean isUndoable() {
		return undo != null;
	}

	@Override
	public boolean isRedoable() {
		return redo != null;
	}

	@Override
	public void undo() {
		undo.run();

	}

	@Override
	public void redo() {
		redo.run();
	}
}
