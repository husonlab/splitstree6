/*
 * RunSequences.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra;

import javafx.application.Application;
import javafx.stage.Stage;
import jloda.fx.util.ProgramExecutorService;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.workflow.WorkflowSetup;

public class RunSequences extends Application {

	@Override
    public void start(Stage stage) {
        var file = "/Users/huson/IdeaProjects/community/splitstree6/examples/bees.fasta";
        var workflow = WorkflowSetup.apply(file, null);

        workflow.validProperty().addListener((v, o, n) -> {
            if (!n) {
                System.err.println(workflow.toReportString());
                System.err.println(ExtractMethodsText.getInstance().apply(workflow));
            }
        });

		workflow.getLoaderNode().restart();

		ProgramExecutorService.submit(2000, () -> System.exit(0));
	}
}
