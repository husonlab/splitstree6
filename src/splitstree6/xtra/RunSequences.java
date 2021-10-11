package splitstree6.xtra;

import javafx.application.Application;
import javafx.stage.Stage;
import jloda.fx.util.ProgramExecutorService;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.sflow.WorkflowSetup;

public class RunSequences extends Application {

	@Override
	public void start(Stage stage) throws Exception {
		var file = "/Users/huson/IdeaProjects/community/splitstree6/examples/bees.fasta";
		var workflow = WorkflowSetup.apply(file);

		workflow.busyProperty().addListener((v, o, n) -> {
			if (!n) {
				System.err.println(workflow.toReportString());
				System.err.println(ExtractMethodsText.getInstance().apply(workflow));
			}
		});

		workflow.getLoaderNode().restart();

		ProgramExecutorService.submit(2000, () -> System.exit(0));
	}
}
