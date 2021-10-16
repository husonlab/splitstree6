/*
 *  WorkflowSetup.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.workflow;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import splitstree6.algorithms.characters.characters2distances.HammingDistances;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.algorithms.source.source2distances.DistancesLoader;
import splitstree6.algorithms.source.source2splits.SplitsLoader;
import splitstree6.algorithms.source.source2trees.TreesLoader;
import splitstree6.algorithms.splits.splits2sink.ShowSplitsConsole;
import splitstree6.algorithms.trees.trees2splits.ConsensusNetwork;
import splitstree6.data.*;
import splitstree6.io.readers.ImportManager;

/**
 * methods for setting up different splitstree workflows
 * Daniel Huson, 10.2021
 */
public class WorkflowSetup {
	public static Workflow apply(String fileName) {
		return apply(fileName, new Workflow(), null);
	}

	public static Workflow apply(String fileName, Workflow workflow, EventHandler<WorkerStateEvent> failedHandler) {
		workflow.clear();

		var sourceBlock = new SourceBlock();
		sourceBlock.getSources().add(fileName);
		var clazz = ImportManager.getInstance().determineInputType(fileName);
		if (clazz.equals(CharactersBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new CharactersLoader(), new TaxaBlock(), new CharactersBlock());
			var distancesNode = workflow.newDataNode(new DistancesBlock());
			workflow.newAlgorithmNode(new HammingDistances(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distancesNode);
			var splitsNode = workflow.newDataNode(new SplitsBlock());
			workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), distancesNode, splitsNode);
			workflow.newAlgorithmNode(new ShowSplitsConsole(), workflow.getWorkingTaxaNode(), splitsNode, workflow.newDataNode(new SinkBlock()));
			// todo: replace by calculation of network
		} else if (clazz.equals(DistancesBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new DistancesLoader(), new TaxaBlock(), new DistancesBlock());
			var distancesNode = workflow.getInputDataNode();
			var splitsNode = workflow.newDataNode(new SplitsBlock());
			workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), distancesNode, splitsNode);
			workflow.newAlgorithmNode(new ShowSplitsConsole(), workflow.getWorkingTaxaNode(), splitsNode, workflow.newDataNode(new SinkBlock()));
			// todo: replace by calculation of network
		} else if (clazz.equals(SplitsBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new SplitsLoader(), new TaxaBlock(), new SplitsBlock());
			var splitsNode = workflow.getInputDataNode();
			workflow.newAlgorithmNode(new ShowSplitsConsole(), workflow.getWorkingTaxaNode(), splitsNode, workflow.newDataNode(new SinkBlock()));
			// todo: replace by calculation of network
		} else if (clazz.equals(TreesBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock, new TreesLoader(), new TaxaBlock(), new TreesBlock());
			var splitsNode = workflow.newDataNode(new SplitsBlock());
			workflow.newAlgorithmNode(new ConsensusNetwork(), workflow.getWorkingTaxaNode(), workflow.getInputDataNode(), splitsNode);
			workflow.newAlgorithmNode(new ShowSplitsConsole(), workflow.getWorkingTaxaNode(), splitsNode, workflow.newDataNode(new SinkBlock()));
			// todo: replace by calculation of network
		}
		/*
		else if (clazz.equals(NetworkBlock.class)) {
			workflow.setupInputAndWorkingNodes(sourceBlock,new NetworkLoader(),new TaxaBlock(), new NetworksBlock());
		var sourceNode = workflow.newSourceNode(new SourceBlock());
		workflow.newLoaderNode(new NetworkLoader(), sourceNode, workflow.getTopTaxaNode(), workflow.getTopDataNode());
		workflow.newAlgorithmNode(new ShowNetworkConsole(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), workflow.newDataNode(new SinkBlock()));
		// todo: replace by calculation of network
		 */
		System.err.println("Workflow: " + workflow.size());
		if (workflow.size() > 0) {
			if (failedHandler != null)
				workflow.getLoaderNode().getService().setOnFailed(failedHandler);
			workflow.getSourceNode().setValid(true);
		}
		return workflow;
	}
}
