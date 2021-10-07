package splitstree6.xtra;

import javafx.application.Application;
import javafx.stage.Stage;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.source.source2distances.DistancesLoader;
import splitstree6.algorithms.trees.trees2sink.ShowTaxaConsole;
import splitstree6.algorithms.trees.trees2sink.ShowTreesConsole;
import splitstree6.data.*;
import splitstree6.workflow.Workflow;

public class SimplePipeline extends Application {
	public static void main(String[] args) {
		var workflow = setupWorkflow();

		var source = workflow.getSourceNode().getDataBlock();

		source.getSources().add("/Users/huson/IdeaProjects/community/splitstree6/examples/square.dist");

		workflow.getLoaderNode().restart();

		System.err.println(workflow.toReportString());
	}

	private static Workflow setupWorkflow() {
		var taxa = new TaxaBlock();
		var distances = new DistancesBlock();
		var source = new SourceBlock();
		var loadDistances = new DistancesLoader();
		var nj = new NeighborJoining();
		var trees = new TreesBlock();

		var workflow = new Workflow();

		workflow.setupTopAndWorkingNodes(taxa, distances);

		var sourceNode = workflow.newSourceNode(source);

		workflow.newLoaderNode(loadDistances, sourceNode, workflow.getTopTaxaNode(), workflow.getTopDataNode());

		var treesNode = workflow.newDataNode(trees);

		workflow.newAlgorithmNode(nj, workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), treesNode);

		workflow.newAlgorithmNode(new ShowTreesConsole(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new SinkBlock()));
		workflow.newAlgorithmNode(new ShowTaxaConsole(), workflow.getWorkingTaxaNode(), treesNode, workflow.newDataNode(new SinkBlock()));

		return workflow;
	}

	@Override
	public void start(Stage stage) throws Exception {
	}
}
