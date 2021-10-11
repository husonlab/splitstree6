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
import splitstree6.algorithms.networks.network2sink.ShowNetworkConsole;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.algorithms.splits.splits2sink.ShowSplitsConsole;
import splitstree6.algorithms.taxa.ShowTaxaConsole;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.algorithms.trees.trees2sink.ShowTreesConsole;
import splitstree6.data.*;
import splitstree6.io.readers.ImportManager;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.sflow.Workflow;

public class SimplePipeline extends Application {
	private static Workflow setupWorkflow() {
		var workflow = new Workflow();

		var source = new SourceBlock();

		source.getSources().add("/Users/huson/IdeaProjects/community/splitstree6/examples/bees.fasta");

		System.err.println("Input type: " + ImportManager.getInstance().determineInputType(source.getSources().get(0)).getSimpleName());
		for (var reader : ImportManager.getInstance().getReaders(source.getSources().get(0))) {
			System.err.println("Applicable reader: " + reader.getClass().getSimpleName());
		}

		{
			workflow.setupTopAndWorkingNodes(source, new CharactersLoader(), new TaxaBlock(), new CharactersBlock());
			workflow.newAlgorithmNode(new ShowTaxaConsole(), null, workflow.getWorkingTaxaNode(), workflow.newDataNode(new SinkBlock()));
		}

		var distancesNode = workflow.newDataNode(new DistancesBlock());
		workflow.newAlgorithmNode(new HammingDistances(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distancesNode);

		if (false) {
			var treesNode = workflow.newDataNode(new TreesBlock());
			workflow.newAlgorithmNode(new NeighborJoining(), workflow.getWorkingTaxaNode(), distancesNode, treesNode);
			workflow.newAlgorithmNode(new ShowTreesConsole(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new SinkBlock()));
		}

		if (true) {
			var treesNode = workflow.newDataNode(new TreesBlock());
			workflow.newAlgorithmNode(new BioNJ(), workflow.getWorkingTaxaNode(), distancesNode, treesNode);
			workflow.newAlgorithmNode(new ShowTreesConsole(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new SinkBlock()));
		}
		if (false) {
			var treesNode = workflow.newDataNode(new TreesBlock());
			workflow.newAlgorithmNode(new UPGMA(), workflow.getWorkingTaxaNode(), distancesNode, treesNode);
			workflow.newAlgorithmNode(new ShowTreesConsole(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new SinkBlock()));
		}

		if (false) {
			var splitsNode = workflow.newDataNode(new SplitsBlock());
			workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), distancesNode, splitsNode);
			workflow.newAlgorithmNode(new ShowSplitsConsole(), workflow.getWorkingTaxaNode(), splitsNode, workflow.newDataNode(new SinkBlock()));
		}
		if (false) {
			var networkNode = workflow.newDataNode(new NetworkBlock());
			workflow.newAlgorithmNode(new MedianJoining(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), networkNode);
			workflow.newAlgorithmNode(new ShowNetworkConsole(), workflow.getWorkingTaxaNode(), networkNode, workflow.newDataNode(new SinkBlock()));
		}

		return workflow;
	}

	@Override
	public void start(Stage stage) throws Exception {
		var workflow = setupWorkflow();

		System.err.println(workflow.toReportString());

		workflow.getSourceNode().setValid(true);

		workflow.busyProperty().addListener((v, o, n) -> {
			if (!n) {
				System.err.println(workflow.toReportString());
				System.err.println(ExtractMethodsText.getInstance().apply(workflow));
			}
		});


		if (true)
			ProgramExecutorService.submit(1000, () -> {
				for (var node : workflow.getNodes(TaxaFilter.class)) {
					var taxaFilter = (TaxaFilter) node.getAlgorithm();
					taxaFilter.getOptionDisabledTaxa().add(workflow.getTopTaxonBlock().getLabel(1));

					if (!workflow.getBusy()) {
						System.err.println("Rerunning Analysis");
						Platform.runLater(node::restart);
					} else
						System.err.println("Busy");
					break;
				}
				if (true)
					workflow.busyProperty().addListener((v, o, n) -> {
						if (!n) ProgramExecutorService.submit(5000, () -> System.exit(0));
					});
			});
	}
}
