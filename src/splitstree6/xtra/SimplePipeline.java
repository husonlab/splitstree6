/*
 *  SimplePipeline.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.xtra;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import jloda.fx.util.ProgramExecutorService;
import splitstree6.algorithms.characters.characters2distances.HammingDistances;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.distances.distances2trees.BioNJ;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaEditor;
import splitstree6.algorithms.taxa.taxa2view.ShowTaxaConsole;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.data.*;
import splitstree6.io.readers.ImportManager;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.workflow.Workflow;

public class SimplePipeline extends Application {
	private static Workflow setupWorkflow() {
		var workflow = new Workflow(null);

		var source = new SourceBlock();

		source.getSources().add("/Users/huson/IdeaProjects/community/splitstree6/examples/bees.fasta");

		System.err.println("Input type: " + ImportManager.getInstance().determineInputType(source.getSources().get(0)).getSimpleName());
		for (var reader : ImportManager.getInstance().getReaders(source.getSources().get(0))) {
			System.err.println("Applicable reader: " + reader.getClass().getSimpleName());
		}

		{
			workflow.setupInputAndWorkingNodes(source, new CharactersLoader(), new TaxaBlock(), new CharactersBlock());
			workflow.newAlgorithmNode(new ShowTaxaConsole(), null, workflow.getWorkingTaxaNode(), workflow.newDataNode(new ViewBlock()));
		}

		var distancesNode = workflow.newDataNode(new DistancesBlock());
		workflow.newAlgorithmNode(new HammingDistances(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distancesNode);

		if (false) {
			var treesNode = workflow.newDataNode(new TreesBlock());
			workflow.newAlgorithmNode(new NeighborJoining(), workflow.getWorkingTaxaNode(), distancesNode, treesNode);
			workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new ViewBlock()));
		}

		if (true) {
			var treesNode = workflow.newDataNode(new TreesBlock());
			workflow.newAlgorithmNode(new BioNJ(), workflow.getWorkingTaxaNode(), distancesNode, treesNode);
			workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new ViewBlock()));
		}
		if (false) {
			var treesNode = workflow.newDataNode(new TreesBlock());
			workflow.newAlgorithmNode(new UPGMA(), workflow.getWorkingTaxaNode(), distancesNode, treesNode);
			workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new ViewBlock()));
		}

		if (true) {
			var splitsNode = workflow.newDataNode(new SplitsBlock());
			workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), distancesNode, splitsNode);
			workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), splitsNode, workflow.newDataNode(new ViewBlock()));
		}
		if (false) {
			var networkNode = workflow.newDataNode(new NetworkBlock());
			workflow.newAlgorithmNode(new MedianJoining(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), networkNode);
			workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), networkNode, workflow.newDataNode(new ViewBlock()));
		}

		return workflow;
	}

	@Override
	public void start(Stage stage) throws Exception {
		var workflow = setupWorkflow();

		System.err.println(workflow.toReportString());

		workflow.getSourceNode().setValid(true);

		workflow.validProperty().addListener((v, o, n) -> {
			if (!n) {
				System.err.println(workflow.toReportString());
				System.err.println(ExtractMethodsText.getInstance().apply(workflow));
			}
		});


		if (true)
			ProgramExecutorService.submit(1000, () -> {
				for (var node : workflow.getNodes(TaxaEditor.class)) {
					var taxaFilter = (TaxaEditor) node.getAlgorithm();
					taxaFilter.setDisabled(workflow.getInputTaxonBlock().getLabel(1), true);

					if (!workflow.isValid()) {
						System.err.println("Rerunning Analysis");
						Platform.runLater(node::restart);
					} else
						System.err.println("Busy");
					break;
				}
				if (true)
					workflow.validProperty().addListener((v, o, n) -> {
						if (!n) ProgramExecutorService.submit(5000, () -> System.exit(0));
					});
			});
	}
}
