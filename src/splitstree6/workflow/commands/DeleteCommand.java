/*
 *  DeleteCommand.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.workflow.commands;

import javafx.util.Pair;
import jloda.fx.undo.UndoableRedoableCommand;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.workflow.Workflow;

import java.util.*;

/**
 * delete an algorithm node and all dependent nodes
 * Daniel Huson, 10.2021
 */
public class DeleteCommand extends UndoableRedoableCommand {
	private final Collection<WorkflowNode> nodes;
	private Collection<Pair<WorkflowNode, WorkflowNode>> parentChildPairs;


	private final Runnable undo;
	private final Runnable redo;

	public DeleteCommand(Workflow workflow, WorkflowNode node) {
		super("delete nodes");
		nodes = workflow.getAllDescendants(node, true);
		parentChildPairs = getParentChildPairs(nodes);
		undo = () -> {
			workflow.addNodes(nodes, parentChildPairs);
		};

		redo = () -> {
			var reverse = new ArrayList<>(nodes);
			Collections.reverse(reverse);
			workflow.deleteNodes(reverse);
		};
	}

	@Override
	public void undo() {
		undo.run();
	}

	@Override
	public void redo() {
		redo.run();
	}

	public static Set<Pair<WorkflowNode, WorkflowNode>> getParentChildPairs(Collection<WorkflowNode> nodes) {
		var set = new HashSet<Pair<WorkflowNode, WorkflowNode>>();

		for (var node : nodes) {
			for (var parent : node.getParents()) {
				set.add(new Pair<>(parent, node));
			}
			for (var child : node.getChildren()) {
				set.add(new Pair<>(node, child));
			}
		}
		return set;
	}
}
