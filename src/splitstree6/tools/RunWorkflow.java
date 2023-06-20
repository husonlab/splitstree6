/*
 * RunWorkflow.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.beans.value.ChangeListener;
import javafx.stage.Stage;
import jloda.fx.util.ArgsOptions;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.NotificationManager;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.nexus.workflow.WorkflowNexusOutput;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.writers.ExportManager;
import splitstree6.main.SplitsTree6;
import splitstree6.main.Version;
import splitstree6.window.MainWindow;
import splitstree6.workflow.WorkflowDataLoader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * runs a workflow on one or more input files
 * Daniel Huson, 9.2018
 */
public class RunWorkflow extends Application {
	private static String[] args;

	@Override
	public void init() {
		Basic.setDebugMode(false);
		ProgramProperties.setProgramName("RunWorkflow");
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
	 * run workflow main
	 */
	public static void main(String[] args) {
		RunWorkflow.args = args;
		Application.launch();
	}

	@Override
	public void start(Stage primaryStage) {
		ProgramExecutorService.getInstance().submit(() -> {
			try {
				run(args);
			} catch (Exception ex) {
				if (!ex.getMessage().startsWith("Help"))
					Basic.caught(ex);
			}
			System.exit(0);
		});
	}

	private void run(String[] args) throws Exception {
		ResourceManagerFX.addResourceRoot(SplitsTree6.class, "splitsTree6/resources");
		final var options = new ArgsOptions(args, RunWorkflow.class, "Runs a SplitsTree6 workflow on input data");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson and David Bryant");

		options.comment("Input Output:");
		final var inputWorkflowFile = new File(options.getOptionMandatory("-w", "workflow", "File containing SplitsTree6 workflow", ""));
		var inputFiles = options.getOptionMandatory("-i", "input", "File(s) containing input data (or directory)", new String[0]);
		final var inputFormat = options.getOption("-f", "format", "Input format", ImportManager.getInstance().getAllFileFormats(), ImportManager.UNKNOWN_FORMAT);
		var outputFiles = options.getOption("-o", "output", "Output file(s) (or directory or stdout)", new String[]{"stdout"});

		final var nodeName = options.getOption("-n", "node", "Title of node to be exported (if none given, will save whole file)", "");
		final var exportFormat = options.getOption("-e", "exporter", "Name of exporter to use",
				CollectionUtils.concatenate(ExportManager.getInstance().getExporterNames(), List.of("NexusWithTaxa")), "");

		options.comment(ArgsOptions.OTHER);
		final var inputFileExtension = options.getOption("-x", "inputExt", "File extension for input files (when providing directory for input)", "");
		final var inputRecursively = options.getOption("-r", "recursive", "Recursively visit all sub-directories (when providing directory for input)", false);

		final String defaultPreferenceFile;
		if (ProgramProperties.isMacOS())
			defaultPreferenceFile = System.getProperty("user.home") + "/Library/Preferences/SplitsTree6.def";
		else
			defaultPreferenceFile = System.getProperty("user.home") + File.separator + ".SplitsTree6.def";
		final var propertiesFile = options.getOption("-p", "propertiesFile", "Properties file", defaultPreferenceFile);

		final var silent = options.getOption("-s", "silent", "Silent mode (hide all stderr output)", false);
		if (silent)
			Basic.hideSystemErr();
		options.done();

		ProgramProperties.load(propertiesFile);

		if (!inputWorkflowFile.canRead())
			throw new IOException("File not found or unreadable: " + inputWorkflowFile);

		if ((nodeName.length() == 0) != (exportFormat.length() == 0))
			throw new IOException("Must specify both node name (using -n or --node) and exporter (using -e or --exporter), or none");

		final boolean exportCompleteWorkflow = (nodeName.length() == 0);

		// Setup and check input files:
		if (inputFiles.length == 1) {
			final var input = new File(inputFiles[0]);
			if (input.isDirectory()) {
				final var inputList = FileUtils.getAllFilesInDirectory(input, inputRecursively, inputFileExtension);
				inputFiles = new String[inputList.size()];
				for (var i = 0; i < inputList.size(); i++) {
					inputFiles[i] = inputList.get(i).getPath();
				}
				System.err.println("Number of input files found: " + inputFiles.length);
			}
		}

		for (var fileName : inputFiles) {
			if (!(new File(fileName)).canRead())
				throw new IOException("Input file not found or not readable: " + fileName);
		}

		// setup and check output files:
		if (inputFiles.length == 0) {
			throw new IOException("No input file(s)");
		} else { // one or more input files
			if (outputFiles.length == 1) {
				var output = new File(outputFiles[0]);
				if (output.isDirectory()) {
					final String extension;
					if (exportCompleteWorkflow)
						extension = ".stree6";
					else {
						extension = "." + ExportManager.getInstance().getExporterByName(exportFormat).getFileExtensions().get(0);
					}

					outputFiles = new String[inputFiles.length];
					for (var i = 0; i < inputFiles.length; i++) {
						final var input = new File(inputFiles[i]);
						var name = FileUtils.replaceFileSuffix(input.getName(), "-out" + extension);
						outputFiles[i] = (new File(output.getPath(), name)).getPath();
					}
				} else if (inputFiles.length > 1 && !outputFiles[0].equals("stdout")) {
					throw new IOException("Too few output files specified");
				}
			}
			if (!outputFiles[0].equals("stdout") && outputFiles.length != inputFiles.length) {
				throw new IOException("Number of output files " + outputFiles.length + " does not match number of input files " + inputFiles.length);
			}
		}

		if (!WorkflowNexusInput.isApplicable(inputWorkflowFile.getPath()))
			throw new IOException("Workflow not valid: " + inputWorkflowFile);

		final var mainWindow = new MainWindow();
		final var workflow = mainWindow.getWorkflow();

		try (final var progress = new ProgressPercentage("Loading workflow from file: " + inputWorkflowFile);
			 var r = FileUtils.getReaderPossiblyZIPorGZIP(inputWorkflowFile.getPath())) {
			WorkflowNexusInput.input(progress, workflow, r);
		}

		final var inputTaxaNode = workflow.getInputTaxaNode();
		if (inputTaxaNode == null)
			throw new IOException("Workflow does not have top taxon node");
		final var inputDataNode = workflow.getInputDataNode();
		if (inputDataNode == null)
			throw new IOException("Workflow does not have top data node");

		System.err.println("Loaded workflow has " + workflow.getNumberOfDataNodes() + " data nodes and " + IteratorUtils.size(workflow.algorithmNodes()) + " algorithms");
		System.err.println("Number of input taxa: " + inputTaxaNode.getDataBlock().getNtax());

		for (var i = 0; i < inputFiles.length; i++) {
			final var inputFile = inputFiles[i];
			System.err.println("++++ Processing " + inputFile + " (" + (i + 1) + " of " + inputFiles.length + ") ++++");

			if (false) {
				System.out.println("++++++++orig++++++++:");
				(new WorkflowNexusOutput()).save(workflow, "stdout", false);
			}

			workflow.clearData();
			if (false) {
				System.out.println("++++++++cleared++++++++:");
				(new WorkflowNexusOutput()).save(workflow, "stdout", false);
			}

			WorkflowDataLoader.load(workflow, inputFile, inputFormat);
			if (false) {
				System.out.println("++++++++loaded++++++++:");
				(new WorkflowNexusOutput()).save(workflow, "stdout", false);
			}

			// update workflow:
			{
				final var latch = new CountDownLatch(1);
				var start = System.currentTimeMillis();
				ChangeListener<Boolean> listener = (v, o, n) -> {
					if (o)
						System.err.println("Running workflow...");
					if (n) {
						latch.countDown();
						System.err.printf("done (%.1fs)%n", (System.currentTimeMillis() - start) / 1000.0);
					}
				};
				//workflow.getWorkingDataNode().setValid(false);
				workflow.validProperty().addListener(listener);
				try {
					Platform.runLater(() -> workflow.getInputTaxaFilterNode().restart());
					// wait for end of update:
					latch.await();
				} finally {
					workflow.validProperty().removeListener(listener);
				}
			}

			if (false) {
				System.out.println("++++++++processed++++++++:");
				(new WorkflowNexusOutput()).save(workflow, "stdout", false);
			}

			// save updated workflow:
			try {
				final var outputFile = (outputFiles.length == inputFiles.length ? outputFiles[i] : outputFiles[0]);
				System.err.println("Saving to: " + outputFile);
				if (exportCompleteWorkflow) {
					(new WorkflowNexusOutput()).save(workflow, outputFile, false);
					System.err.println("done");
					System.err.println("Saved workflow has " + workflow.getNumberOfDataNodes() + " data nodes and " + IteratorUtils.size(workflow.algorithmNodes()) + " algorithms");
				} else {
					final var dataNode = workflow.findDataNode(nodeName);
					if (dataNode == null)
						throw new IOException("Node with title '" + nodeName + "': not found");
					ExportManager.getInstance().exportFile(outputFile, workflow.getWorkingTaxaBlock(), dataNode.getDataBlock(), exportFormat);
				}
			} catch (IOException e) {
				System.err.println("Save FAILED: " + e.getMessage());
			}
		}
	}
}
