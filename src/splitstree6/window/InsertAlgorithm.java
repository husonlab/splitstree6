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

/*
 *  AttachAlgorithm.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.window;

import jloda.fx.dialog.SetParameterDialog;
import jloda.fx.window.NotificationManager;
import jloda.util.IteratorUtils;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.Workflow;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * code for inserting an algorithm
 * Daniel Huson, 11.2021
 */
public class InsertAlgorithm {
	/**
	 * attaches an algorithm, first find, select or choose the target node and the call the callback
	 *
	 * @param mainWindow             main window
	 * @param algorithm              object to show which type of algorithm is desired, this object is not used if algorithm found in workflow
	 * @param algorithmSetupCallback - select options if algorithm found
	 */
	public static void apply(MainWindow mainWindow, Algorithm algorithm, Consumer<Algorithm> algorithmSetupCallback) {
		var workflow = mainWindow.getWorkflow();

		{
			var algorithmNode = findOrSelectMatchingNode(mainWindow.getWorkflow(), algorithm);
			if (algorithmNode != null) {
				algorithmNode.setAlgorithm(algorithm);
				mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);
				if (algorithmSetupCallback != null)
					algorithmSetupCallback.accept(algorithm);
				algorithmNode.restart();
				return;
			}
		}
		var targetNode = findOrSelectAlgorithmNodeToInsertInfrontOf(mainWindow.getWorkflow(), algorithm);
		if (targetNode != null) {
			try {
				var inputDataBlock = targetNode.getPreferredParent();
				var outputDataBlock = workflow.newDataNode((DataBlock) algorithm.getToClass().getConstructor().newInstance());
				var algorithmNode = workflow.newAlgorithmNode(algorithm, workflow.getWorkingTaxaNode(), inputDataBlock, outputDataBlock);
				targetNode.getParents().add(outputDataBlock);
				targetNode.getParents().remove(inputDataBlock);
				mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);
				if (algorithmSetupCallback != null)
					algorithmSetupCallback.accept(algorithm);
				algorithmNode.restart();
			} catch (Exception ex) {
				NotificationManager.showError("Internal error: " + ex);
			}
		}
	}

	/**
	 * find or select algorithm node to set algorithm on
	 *
	 * @return node or null
	 */
	public static AlgorithmNode findOrSelectMatchingNode(Workflow workflow, Algorithm algorithm) {
		var nodes = (List<AlgorithmNode>) IteratorUtils.asStream(workflow.algorithmNodes())
				.filter(d -> workflow.isDerivedNode(d)).filter(d -> ((Algorithm) d.getAlgorithm()).getFromClass() == algorithm.getFromClass())
				.filter(d -> ((Algorithm) d.getAlgorithm()).getToClass() == algorithm.getToClass())
				.collect(Collectors.toList());

		if (nodes.size() == 1)
			return (AlgorithmNode) nodes.get(0);
		else if (nodes.size() > 1) {
			return SetParameterDialog.apply(null, "There are multiple possible targets:", nodes, nodes.get(0));
		}
		return null;
	}


	/**
	 * find or select algorithm node to attach before
	 *
	 * @return node or null
	 */
	public static AlgorithmNode findOrSelectAlgorithmNodeToInsertInfrontOf(Workflow workflow, Algorithm algorithm) {
		var nodes = (List<AlgorithmNode>) IteratorUtils.asStream(workflow.algorithmNodes())
				.filter(d -> workflow.isDerivedNode(d)).filter(d -> ((Algorithm) d.getAlgorithm()).getFromClass() == algorithm.getToClass())
				.collect(Collectors.toList());

		if (nodes.size() == 1)
			return (AlgorithmNode) nodes.get(0);
		else if (nodes.size() > 1) {
			return SetParameterDialog.apply(null, "There are multiple possible targets:", nodes, nodes.get(0));
		}
		return null;
	}

}
