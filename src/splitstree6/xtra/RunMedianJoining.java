package splitstree6.xtra;

import javafx.application.Application;
import javafx.stage.Stage;
import splitstree6.algorithms.characters.characters2distances.LogDet;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.networks.network2sink.ShowNetworkConsole;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.algorithms.taxa.ShowTaxaConsole;
import splitstree6.data.*;
import splitstree6.io.readers.ImportManager;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.sflow.Workflow;

public class RunMedianJoining extends Application {
	private static Workflow setupWorkflow() {
		var workflow = new Workflow();

		{
			workflow.setupTopAndWorkingNodes(new SourceBlock(), new CharactersLoader(), new TaxaBlock(), new CharactersBlock());
			workflow.newAlgorithmNode(new ShowTaxaConsole(), workflow.getWorkingTaxaNode(), workflow.getWorkingTaxaNode(), workflow.newDataNode(new SinkBlock()));
		}
		var distancesNode = workflow.newDataNode(new DistancesBlock());
		workflow.newAlgorithmNode(new LogDet(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distancesNode);

		{
			var networkNode = workflow.newDataNode(new NetworkBlock());
			workflow.newAlgorithmNode(new MedianJoining(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), networkNode);
			var show = new ShowNetworkConsole();
			show.setOptionShowAllDetails(true);
			workflow.newAlgorithmNode(show, workflow.getWorkingTaxaNode(), networkNode, workflow.newDataNode(new SinkBlock()));
		}

		return workflow;
	}

	@Override
	public void start(Stage stage) throws Exception {
		var workflow = setupWorkflow();

		workflow.busyProperty().addListener((v, o, n) -> {
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
