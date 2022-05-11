/*
 * WorkflowSetup.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.workflow;

import javafx.beans.value.WeakChangeListener;
import javafx.concurrent.Worker;
import jloda.fx.window.NotificationManager;
import splitstree6.algorithms.characters.characters2distances.HammingDistances;
import splitstree6.algorithms.distances.distances2network.distances2splits.NeighborNet;
import splitstree6.algorithms.network.network2view.ShowNetwork;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.algorithms.source.source2distances.DistancesLoader;
import splitstree6.algorithms.source.source2network.NetworkLoader;
import splitstree6.algorithms.source.source2splits.SplitsLoader;
import splitstree6.algorithms.source.source2trees.TreesLoader;
import splitstree6.algorithms.splits.splits2view.ShowSplits;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.data.*;
import splitstree6.io.readers.ImportManager;
import splitstree6.window.MainWindow;

import java.util.function.Consumer;

/**
 * methods for setting up different splitstree workflows
 * Daniel Huson, 10.2021
 */
public class WorkflowSetup {

	public static Workflow apply(String fileName, MainWindow mainWindow) {
		var workflow = new Workflow(mainWindow);
		workflow.setServiceConfigurator(s -> s.setProgressParentPane(mainWindow.getController().getBottomFlowPane()));
		return apply(fileName, workflow, null, null);
	}

	public static Workflow apply(String fileName, Workflow workflow, Consumer<Throwable> exceptionHandler, Runnable runOnSuccess) {
		workflow.clear();

		var sourceBlock = new SourceBlock();
		sourceBlock.getSources().add(fileName);
		var clazz = ImportManager.getInstance().determineInputType(fileName);
		if (clazz == null) {
			NotificationManager.showError("No suitable importer found");
			return workflow;
		}
		if (clazz.equals(CharactersBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new CharactersLoader(), new TaxaBlock(), new CharactersBlock());
			workflow.ensureAlignmentView();
			var distancesNode = workflow.newDataNode(new DistancesBlock());
			workflow.newAlgorithmNode(new HammingDistances(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distancesNode);
			var splitsNode = workflow.newDataNode(new SplitsBlock());
			workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), distancesNode, splitsNode);
			var viewerNode = workflow.newDataNode(new ViewBlock());
			workflow.newAlgorithmNode(new ShowSplits(), workflow.getWorkingTaxaNode(), splitsNode, viewerNode);
		} else if (clazz.equals(DistancesBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new DistancesLoader(), new TaxaBlock(), new DistancesBlock());
			var distancesNode = workflow.getInputDataNode();
			var splitsNode = workflow.newDataNode(new SplitsBlock());
			workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), distancesNode, splitsNode);
			var viewerNode = workflow.newDataNode(new ViewBlock());
			workflow.newAlgorithmNode(new ShowSplits(), workflow.getWorkingTaxaNode(), splitsNode, viewerNode);
		} else if (clazz.equals(SplitsBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new SplitsLoader(), new TaxaBlock(), new SplitsBlock());
			var viewerNode = workflow.newDataNode(new ViewBlock());
			workflow.newAlgorithmNode(new ShowSplits(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), viewerNode);
		} else if (clazz.equals(TreesBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new TreesLoader(), new TaxaBlock(), new TreesBlock());
			var viewerNode = workflow.newDataNode(new ViewBlock());
			workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), viewerNode);
		} else if (clazz.equals(NetworkBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new NetworkLoader(), new TaxaBlock(), new NetworkBlock());
			var dataNode = workflow.getWorkingDataNode();
			workflow.newAlgorithmNode(new ShowNetwork(), workflow.getWorkingTaxaNode(), dataNode, workflow.newDataNode(new ViewBlock()));
		}
		/*
		else if (clazz.equals(NetworkBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock,new NetworkLoader(),new TaxaBlock(), new NetworksBlock());
		var sourceNode = workflow.newSourceNode(new SourceBlock());
		workflow.newLoaderNode(new NetworkLoader(), sourceNode, workflow.getTopTaxaNode(), workflow.getTopDataNode());
		workflow.newAlgorithmNode(new ShowNetworkConsole(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), workflow.newDataNode(new ViewBlock()));
		// todo: replace by calculation of network
		 */
		System.err.println("Workflow: " + workflow.size());
		if (workflow.size() > 0) {
			if (exceptionHandler != null || runOnSuccess != null)
				workflow.getLoaderNode().getService().stateProperty().addListener(new WeakChangeListener<>((v, o, n) -> {
					if (n == Worker.State.FAILED && exceptionHandler != null)
						exceptionHandler.accept(workflow.getLoaderNode().getService().getException());
					if (n == Worker.State.SUCCEEDED && runOnSuccess != null)
						runOnSuccess.run();
				}));
			workflow.getSourceNode().setValid(true);
			workflow.getInputDataLoaderNode().restart();
		}
		return workflow;
	}
}
