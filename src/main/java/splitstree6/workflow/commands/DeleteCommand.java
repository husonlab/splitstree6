/*
 *  DeleteCommand.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.fx.undo.UndoableRedoableCommand;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.workflow.Workflow;

import java.util.ArrayList;
import java.util.Collections;

/**
 * delete an algorithm node and all dependent nodes
 * Daniel Huson, 10.2021
 */
public class DeleteCommand {
	public static UndoableRedoableCommand create(Workflow workflow, WorkflowNode node) {
		final var nodes = workflow.getAllDescendants(node, true);

		return UndoableRedoableCommand.create("delete nodes",
				() -> workflow.addNodes(nodes),
				() -> {
					var reverse = new ArrayList<>(nodes);
					Collections.reverse(reverse);
					workflow.deleteNodes(reverse);
				});
	}
}
