/*
 *  RunMedianJoining.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.stage.Stage;
import splitstree6.algorithms.characters.characters2distances.LogDet;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.network.network2view.ShowNetworkConsole;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.algorithms.taxa.taxa2view.ShowTaxaConsole;
import splitstree6.data.*;
import splitstree6.io.readers.ImportManager;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.workflow.Workflow;

public class RunMedianJoining extends Application {
	private static Workflow setupWorkflow() {
		var workflow = new Workflow(null);

		{
			workflow.setupInputAndWorkingNodes(new SourceBlock(), new CharactersLoader(), new TaxaBlock(), new CharactersBlock());
			workflow.newAlgorithmNode(new ShowTaxaConsole(), workflow.getWorkingTaxaNode(), workflow.getWorkingTaxaNode(), workflow.newDataNode(new ViewBlock()));
		}
		var distancesNode = workflow.newDataNode(new DistancesBlock());
		workflow.newAlgorithmNode(new LogDet(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distancesNode);

		{
			var networkNode = workflow.newDataNode(new NetworkBlock());
			workflow.newAlgorithmNode(new MedianJoining(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), networkNode);
			var show = new ShowNetworkConsole();
			workflow.newAlgorithmNode(show, workflow.getWorkingTaxaNode(), networkNode, workflow.newDataNode(new ViewBlock()));
		}

		return workflow;
	}

	@Override
	public void start(Stage stage) throws Exception {
		var workflow = setupWorkflow();

		workflow.validProperty().addListener((v, o, n) -> {
			if (!n) {
				System.err.println(workflow.toReportString());
				System.err.println(ExtractMethodsText.getInstance().apply(workflow));
			}
		});

		var source = workflow.getSourceBlock();
		workflow.getSourceBlock().getSources().add("/Users/huson/IdeaProjects/community/splitstree6/examples/bees.fasta");

		System.err.println("Input type: " + ImportManager.getInstance().determineInputType(source.getSources().get(0)).getSimpleName());
		for (var reader : ImportManager.getInstance().getReaders(source.getSources().get(0))) {
			System.err.println("Applicable reader: " + reader.getClass().getSimpleName());
		}

		workflow.getLoaderNode().restart();
	}
}
