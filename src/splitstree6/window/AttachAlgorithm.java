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
import splitstree6.workflow.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * code for attaching an algorithm
 * Daniel Huson, 11.2021
 */
public class AttachAlgorithm {
	/**
	 * attaches an algorithm, first find, select or choose the node and the call the callback
	 *
	 * @param mainWindow             main window
	 * @param algorithm              object to show which type of algorithm is desired, this object is not used if algorithm found in workflow
	 * @param algorithmSetupCallback - select options if algorithm found
	 */
	public static void apply(MainWindow mainWindow, Algorithm algorithm, Consumer<Algorithm> algorithmSetupCallback) {
		var algorithmNode = AttachAlgorithm.findSelectOrCreateAlgorithmNode(mainWindow.getWorkflow(), algorithm);
		if (algorithmNode != null) {
			algorithm = algorithmNode.getAlgorithm();
			mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);
			if (algorithmSetupCallback != null)
				algorithmSetupCallback.accept(algorithm);
			algorithmNode.restart();
		}
	}

	/**
	 * find, select or create a node for the given algorithm
	 *
	 * @return node or null
	 */
	public static AlgorithmNode findSelectOrCreateAlgorithmNode(Workflow workflow, Algorithm algorithm) {
		var algorithmNodes = workflow.getNodes(algorithm.getClass()).stream()
				.filter(workflow::isDerivedNode).collect(Collectors.toList());

		if (algorithmNodes.size() == 1)
			return algorithmNodes.iterator().next();
		else if (algorithmNodes.size() > 1) {
			return SetParameterDialog.apply(null, "There are multiple instances of this algorithm:", algorithmNodes, algorithmNodes.iterator().next());
		} else {
			var dataNodes = (List<DataNode>) IteratorUtils.asStream(workflow.dataNodes()).filter(d -> workflow.isDerivedNode(d) || workflow.isWorkingDataNode(d))
					.filter(d -> d.getDataBlock().getClass() == algorithm.getFromClass()).collect(Collectors.toList());
			if (dataNodes.size() > 0) {
				DataNode dataNode;
				if (dataNodes.size() == 1)
					dataNode = dataNodes.get(0);
				else
					dataNode = SetParameterDialog.apply(null, "There are multiple possible targets:", dataNodes, dataNodes.get(0));
				if (dataNode != null) {
					try {
						var outputDataNode = workflow.newDataNode((DataBlock) algorithm.getToClass().getConstructor().newInstance());
						return workflow.newAlgorithmNode(algorithm, workflow.getWorkingTaxaNode(), dataNode, outputDataNode);
					} catch (Exception ex) {
						NotificationManager.showError("Internal error: " + ex);
					}
				}
			}
		}
		return null;
	}

}
