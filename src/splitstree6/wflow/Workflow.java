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

package splitstree6.wflow;

import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Workflow {
	private final BooleanProperty busy = new SimpleBooleanProperty(false);
	private final ChangeListener<Boolean> nodeValidListener;

	private final ObservableList<WorkflowNode> nodes = FXCollections.observableArrayList();
	private int numberOfNodesCreated = 0;

	private final IntegerProperty numberOfEdges = new SimpleIntegerProperty(0);
	private final ListChangeListener<WorkflowNode> parentsChangedListener;

	public Workflow() {
		parentsChangedListener = change -> {
			var count = 0;
			while (change.next()) {
				count += change.getAddedSize();
				count -= change.getRemovedSize();
			}
			numberOfEdges.set(numberOfEdges.get() + count);
		};

		nodeValidListener = (v, o, n) -> {
			if (n)
				busy.set(nodes.size() > 0 && !nodes.stream().allMatch(WorkflowNode::isValid));
			else
				busy.set(true);
		};

		nodes.addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var node : e.getAddedSubList()) {
						node.validProperty().addListener(nodeValidListener);
						node.getParents().addListener(parentsChangedListener);
					}
				} else if (e.wasRemoved()) {
					for (var node : e.getRemoved()) {
						node.validProperty().removeListener(nodeValidListener);
					}
				}
			}
		});
	}

	public Collection<WorkflowNode> nodes() {
		return nodes;
	}

	public Stream<WorkflowNode> nodeStream() {
		return nodes.stream();
	}

	public Iterable<WorkflowNode> roots() {
		return () -> nodeStream().filter(v -> v.getInDegree() == 0).iterator();
	}

	public Iterable<WorkflowNode> leaves() {
		return () -> nodeStream().filter(v -> v.getOutDegree() == 0).iterator();
	}

	public Iterable<DataNode> dataNodes() {
		return () -> nodeStream().filter(v -> v instanceof DataNode).map(v -> (DataNode) v).iterator();
	}

	public Iterable<AlgorithmNode> algorithmNodes() {
		return () -> nodeStream().filter(v -> v instanceof AlgorithmNode).map(v -> (AlgorithmNode) v).iterator();
	}

	public void deleteNode(WorkflowNode v) {
		checkOwner(v.getOwner());

		for (var w : v.getParents()) {
			w.getChildren().remove(v);
		}

		for (var w : v.getParents()) {
			w.getParents().remove(v);
		}

		nodes.remove(v);
	}

	public int getNumberOfNodes() {
		return nodes.size();
	}

	public int getNumberOfEdges() {
		return numberOfEdges.get();
	}

	public boolean isDAG() {
		return true;
	}

	public boolean isConnected() {
		return true;
	}

	public void checkOwner(Workflow owner) {
		assert owner != null : "Owner is null";
		assert owner == this : "Wrong owner";
	}


	public boolean getBusy() {
		return busy.get();
	}

	public ReadOnlyBooleanProperty busyProperty() {
		return busy;
	}

	public String toReportString() {
		var buf = new StringBuilder("Workflow (" + nodes.size() + " nodes):\n");
		var seen = new HashSet<WorkflowNode>();
		var queue = nodeStream().filter(n -> n.getInDegree() == 0).collect(Collectors.toCollection(LinkedList::new));
		while (queue.size() > 0) {
			var node = queue.pop();
			if (!seen.contains(node)) {
				seen.add(node);
				buf.append(node.toReportString(true));
				buf.append("\n");
				queue.addAll(node.getChildren().stream().filter(n -> !seen.contains(n)).collect(Collectors.toList()));
			}
		}
		return buf.toString();
	}

	public void clear() {
		// cancel all computations:
		for (var node : nodes()) {
			node.setValid(false);
		}
		nodes.clear();
	}

	public int getNodeUID() {
		return ++numberOfNodesCreated;
	}
}
