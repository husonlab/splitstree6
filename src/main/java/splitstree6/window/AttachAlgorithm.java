/*
 *  AttachAlgorithm.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.fx.dialog.SetParameterDialog;
import jloda.fx.window.NotificationManager;
import jloda.util.IteratorUtils;
import splitstree6.algorithms.network.network2view.ShowNetwork;
import splitstree6.algorithms.splits.splits2view.ShowSplits;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.data.NetworkBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.workflow.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * code for attaching an algorithm
 * Daniel Huson, 11.2021
 */
public class AttachAlgorithm {
	/**
	 * attaches an algorithm, first find, select or choose the node and the call the callback
	 *
	 * @param mainWindow main window
	 * @param algorithm  object to show which type of algorithm is desired, this object is not used if algorithm found in workflow
	 */
	public static void apply(MainWindow mainWindow, Algorithm algorithm) {
		apply(mainWindow, algorithm, null, null);
	}

	/**
	 * attaches an algorithm, first find, select or choose the node and then call the callback
	 *
	 * @param mainWindow             main window
	 * @param algorithm              object to show which type of algorithm is desired, this object is not used if algorithm found in workflow
	 * @param algorithmSetupCallback - select options if algorithm found
	 */
	public static void apply(MainWindow mainWindow, Algorithm algorithm, Consumer<Algorithm> algorithmSetupCallback) {
		apply(mainWindow, algorithm, algorithmSetupCallback, null);
	}

	/**
	 * attaches an algorithm, first find, select or choose the node and then call the callback
	 *
	 * @param mainWindow             main window
	 * @param algorithm              object to show which type of algorithm is desired, this object is not used if algorithm found in workflow
	 * @param algorithmSetupCallback - select options if algorithm found
	 */
	public static void apply(MainWindow mainWindow, Algorithm algorithm, Consumer<Algorithm> algorithmSetupCallback, Consumer<Algorithm> targetSetupCallback) {
		var workflow = mainWindow.getWorkflow();
		var algorithmNode = AttachAlgorithm.findSelectOrCreateAlgorithmNode(mainWindow.getWorkflow(), algorithm);
		if (algorithmNode != null) {
			algorithm = algorithmNode.getAlgorithm();
			mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);
			if (algorithmSetupCallback != null)
				algorithmSetupCallback.accept(algorithm);

			if (algorithm.getToClass() == SplitsBlock.class) {
				var targetDataNode = (DataNode) algorithmNode.getPreferredChild();
				if (targetDataNode.getPreferredChild() == null) {
					var targetDataNode2 = workflow.newDataNode(new ViewBlock());
					var targetAlgorithmNode = workflow.newAlgorithmNode(new ShowSplits(), workflow.getWorkingTaxaNode(), targetDataNode, targetDataNode2);
					if (targetSetupCallback != null)
						targetSetupCallback.accept(targetAlgorithmNode.getAlgorithm());
				}
			} else if (algorithm.getToClass() == TreesBlock.class) {
				var targetDataNode = (DataNode) algorithmNode.getPreferredChild();
				if (targetDataNode.getPreferredChild() == null) {
					var targetDataNode2 = workflow.newDataNode(new ViewBlock());
					var targetAlgorithmNode = workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), targetDataNode, targetDataNode2);
					if (targetSetupCallback != null)
						targetSetupCallback.accept(targetAlgorithmNode.getAlgorithm());
				}
			} else if (algorithm.getToClass() == NetworkBlock.class) {
				var targetDataNode = (DataNode) algorithmNode.getPreferredChild();
				if (targetDataNode.getPreferredChild() == null) {
					var targetDataNode2 = workflow.newDataNode(new ViewBlock());
					var targetAlgorithmNode = workflow.newAlgorithmNode(new ShowNetwork(), workflow.getWorkingTaxaNode(), targetDataNode, targetDataNode2);
					if (targetSetupCallback != null)
						targetSetupCallback.accept(targetAlgorithmNode.getAlgorithm());
				}
			}
			if (algorithm.isApplicable(workflow.getWorkingTaxaBlock(), algorithmNode.getPreferredParent().getDataBlock()))
				algorithmNode.restart();
		}
	}

	@SafeVarargs
	public static BooleanProperty createDisableProperty(MainWindow mainWindow, Algorithm algorithm, Supplier<Boolean>... disableConditions) {
		var workflow = mainWindow.getWorkflow();
		var disableProperty = new SimpleBooleanProperty(false);
		Callable<Boolean> disableSuppler = () -> {
			if (workflow.isRunning() || workflow.dataNodesStream().noneMatch(d -> algorithm.getFromClass() == d.getDataBlock().getClass()))
				return true;
			for (var condition : disableConditions) {
				if (!condition.get())
					return true;
			}
			return false;
		};
		disableProperty.bind(Bindings.createBooleanBinding(disableSuppler, workflow.validProperty()));
		return disableProperty;
	}

	/**
	 * find, select or create a node for the given algorithm
	 *
	 * @return node or null
	 */
	public static AlgorithmNode findSelectOrCreateAlgorithmNode(Workflow workflow, Algorithm algorithm) {
		var algorithmNodes = (ArrayList<AlgorithmNode>) workflow.algorithmNodesStream()
				.filter(n -> workflow.isDerivedNode(n)).filter(n -> n.getAlgorithm().getFromClass() == algorithm.getFromClass())
				.filter(n -> n.getAlgorithm().getToClass() == algorithm.getToClass()).collect(Collectors.toCollection(ArrayList::new));

		if (algorithmNodes.size() == 1) {
			var algorithmNode = algorithmNodes.get(0);
			if (algorithmNode.getAlgorithm().getClass() != algorithm.getClass()) {
				algorithmNode.setAlgorithm(algorithm);
				algorithmNode.setTitle(algorithmNode.getAlgorithm().getName());
				algorithmNode.restart();
				return null; // have restarted algorithm, no need to do anything further
			} else {
				return algorithmNode;
			}
		} else if (algorithmNodes.size() > 1) {
			var algorithmNode = SetParameterDialog.apply(null, "There are multiple possible choices for this algorithm:", algorithmNodes, algorithmNodes.get(0));
			if (algorithmNode != null && algorithmNode.getAlgorithm().getClass() != algorithm.getClass()) {
				algorithmNode.setAlgorithm(algorithm);
				algorithmNode.setTitle(algorithmNode.getAlgorithm().getName());
				algorithmNode.restart();
				return null;  // have restarted algorithm, no need to do anything further
			} else {
				return algorithmNode;
			}
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
