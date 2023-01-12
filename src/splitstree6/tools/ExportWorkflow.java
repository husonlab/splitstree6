/*
 * ExportWorkflow.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tools;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import jloda.fx.util.ArgsOptions;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.NotificationManager;
import jloda.util.Basic;
import jloda.util.FileUtils;
import jloda.util.IteratorUtils;
import jloda.util.PeakMemoryUsageMonitor;
import jloda.util.progress.ProgressPercentage;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.writers.ExportManager;
import splitstree6.main.SplitsTree6;
import splitstree6.main.Version;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.window.MainWindow;

import java.io.File;
import java.io.IOException;

/**
 * runs a workflow on one or more input files
 * Daniel Huson, 9.2018
 */
public class ExportWorkflow extends Application {
    private static String[] args;

    @Override
    public void init() {
        Basic.setDebugMode(false);
        ProgramProperties.setProgramName("ExportWorkflow");
        ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);
        NotificationManager.setEchoToConsole(false);

        PeakMemoryUsageMonitor.start();
    }

    @Override
    public void stop() {
        System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
        System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
        System.exit(0);
    }

    /**
     * add functional annotations to DNA alignments
     */
    public static void main(String[] args) {
        ExportWorkflow.args = args;
        Application.launch();
    }

    @Override
    public void start(Stage primaryStage) {
        ProgramExecutorService.getInstance().submit(() -> {
            try {
                run(args, primaryStage);
            } catch (Exception ex) {
                if (!ex.getMessage().startsWith("Help"))
                    Basic.caught(ex);
            }
            Platform.exit();
        });
    }

    private void run(String[] args, Stage primaryStage) throws Exception {
        ResourceManagerFX.addResourceRoot(SplitsTree6.class, "splitstree6/resources");

        final var options = new ArgsOptions(args, ExportWorkflow.class, "Exports data from a SplitsTree6 workflow");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("Copyright (C) 2023 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
		options.setAuthors("Daniel H. Huson");

        options.comment("Input Output:");
        final var inputWorkflowFile = new File(options.getOptionMandatory("-w", "workflow", "File containing SplitsTree5 workflow", ""));
        final var nodeNames = options.getOption("-n", "node", "Title(s) of node(s) to be exported", new String[0]);
        final var exportFormats = options.getOption("-e", "exporter", "Name of exporter(s) to use", ExportManager.getInstance().getExporterNames(), new String[0]);
        var outputFiles = options.getOption("-o", "output", "Output file(s) (or directory or stdout)", new String[]{"stdout"});

        options.comment(ArgsOptions.OTHER);
        final var methods = options.getOption("-m", "methods", "Report methods used by workflow", false);

        final String defaultPreferenceFile;
        if (ProgramProperties.isMacOS())
            defaultPreferenceFile = System.getProperty("user.home") + "/Library/Preferences/SplitsTree6.def";
        else
            defaultPreferenceFile = System.getProperty("user.home") + File.separator + ".SplitsTree6.def";
        final String propertiesFile = options.getOption("-p", "propertiesFile", "Properties file", defaultPreferenceFile);

        final var silent = options.getOption("-s", "silent", "Silent mode (hide all stderr output)", false);
        if (silent)
            Basic.hideSystemErr();
        options.done();

        ProgramProperties.load(propertiesFile);

        if (!inputWorkflowFile.canRead())
            throw new IOException("File not found or unreadable: " + inputWorkflowFile);

        if (nodeNames.length != exportFormats.length)
            throw new IOException("Number of specified nodes " + nodeNames.length + " does not match number of specified formats " + exportFormats.length);

        // setup and check output files:
        if (nodeNames.length == 0 && !methods) {
            throw new IOException("No node name specified");
        } else { // one or more nodes
            if (outputFiles.length == 1) {
                File output = new File(outputFiles[0]);
                if (output.isDirectory()) {
                    outputFiles = new String[nodeNames.length];
                    for (int i = 0; i < nodeNames.length; i++) {
                        final var suffix = ExportManager.getInstance().getExporterByName(exportFormats[i]).getFileExtensions().get(0);
                        outputFiles[i] = FileUtils.replaceFileSuffix(inputWorkflowFile.getPath(), "-" + nodeNames[i] + "." + suffix);
                    }
                } else if (nodeNames.length > 1 && !outputFiles[0].equals("stdout")) {
                    throw new IOException("Too few output files specified");
                }
            }
            if (!outputFiles[0].equals("stdout") && outputFiles.length != nodeNames.length) {
                throw new IOException("Number of output files " + outputFiles.length + " does not match number of specified nodes" + nodeNames.length);
            }
        }

        if (!WorkflowNexusInput.isApplicable(inputWorkflowFile.getPath()))
            throw new IOException("Invalid workflow in file: " + inputWorkflowFile);

        var mainWindow = new MainWindow();
        var workflow = mainWindow.getWorkflow();

        try (var progress = new ProgressPercentage("Loading workflow from file: " + inputWorkflowFile);
             var r = FileUtils.getReaderPossiblyZIPorGZIP(inputWorkflowFile.getPath())) {
            WorkflowNexusInput.input(progress, workflow, r);
        }

        final var inputTaxaNpde = workflow.getInputTaxaNode();
        if (inputTaxaNpde == null)
            throw new IOException("Incomplete workflow: top taxon node not found");
        final var inputDataNode = workflow.getInputDataNode();
        if (inputDataNode == null)
            throw new IOException("Incomplete workflow: top data node not found");

        System.err.println("Loaded workflow has " + workflow.getNumberOfDataNodes() + " data nodes and " + IteratorUtils.size(workflow.algorithmNodes()) + " algorithms");

        if (methods) {
            System.out.println(ExtractMethodsText.getInstance().apply(workflow));
            System.out.flush();
        }

        for (var i = 0; i < nodeNames.length; i++) {
            final var dataNode = workflow.findDataNode(nodeNames[i]);
            if (dataNode == null)
                throw new IOException("Node with title '" + nodeNames[i] + "': not found");
            var outputFile = (outputFiles.length == 1 ? outputFiles[0] : outputFiles[i]);
            System.err.println("Exporting node '" + nodeNames[i] + "' to " + outputFile);
            ExportManager.getInstance().exportFile(outputFile, workflow.getWorkingTaxaBlock(), dataNode.getDataBlock(), exportFormats[i]);
        }
    }
}
