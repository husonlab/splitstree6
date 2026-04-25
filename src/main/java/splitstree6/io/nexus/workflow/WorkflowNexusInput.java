/*
 *  WorkflowNexusInput.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.nexus.workflow;

import javafx.application.Platform;
import jloda.fx.util.AService;
import jloda.fx.window.NotificationManager;
import jloda.util.Basic;
import jloda.util.FileUtils;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.Pair;
import jloda.util.parse.NexusStreamParser;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.*;
import splitstree6.io.nexus.AlgorithmNexusInput;
import splitstree6.io.nexus.NexusExporter;
import splitstree6.io.nexus.SplitsTree6NexusInput;
import splitstree6.io.nexus.TaxaNexusInput;
import splitstree6.main.AppProfile;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.view.inputeditor.InputEditorView;
import splitstree6.window.MainWindow;
import splitstree6.workflow.*;

import java.io.*;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * imports a file in SplitsTree6 nexus input format
 * Daniel Huson, 10.2021
 */
public class WorkflowNexusInput {
	public static final String WORKFLOW_FILE_SUFFIX = ".wflow6";

	public static boolean isApplicable(String fileName) {
		try (NexusStreamParser np = new NexusStreamParser(new FileReader(fileName))) {
			if (np.peekMatchIgnoreCase("#nexus")) {
				np.matchIgnoreCase("#nexus");
				return np.peekMatchBeginBlock(SplitsTree6Block.BLOCK_NAME);
			}
		} catch (IOException ex) {
			return false;
		}
		return false;
	}

	public static void open(MainWindow mainWindow, String fileName, Consumer<Throwable> exceptionHandler, Runnable runOnSuccess) {
		// Workflow-only files (.wflow6) are a SplitsTree6 power-user feature;
		// host applications that are extensions of SplitsTree6 don't support them.
		if (isWorkflowFile(fileName) && AppProfile.getProfile().isExtension()) {
			NotificationManager.showError(
					"Workflow files are not supported in " +
					AppProfile.getProfile().getName() +
					".\nPlease open a data file (characters, distances, or .stree6) instead.");
			return;
		}
		var workflow = mainWindow.getWorkflow();
		workflow.clear();
		mainWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);

		var newWorkflow = new Workflow(mainWindow);

		{
			var inputWorkFlow = newWorkflow;
			var service = new AService<Workflow>(mainWindow.getController().getBottomFlowPane());
			service.setCallable(() -> {
				try (var reader = new BufferedReader(new FileReader(fileName))) {
					input(service.getProgressListener(), inputWorkFlow, reader);
				}
				return inputWorkFlow;
			});
			service.setOnSucceeded(e -> {
				var resultWorkflow = service.getValue();
				mainWindow.setFileName(fileName);
				NotificationManager.showInformation("Loaded file: " + fileName + ", workflow nodes: " + resultWorkflow.size());
				workflow.shallowCopy(resultWorkflow);

				if (!isWorkflowFile(fileName)) {
					for (var node : workflow.algorithmNodes()) {
						if (((AlgorithmNode) node).getAlgorithm().getToClass() == ViewBlock.class) {
							node.restart();
						}
					}
					// Profile may want to discard and rebuild this workflow
					if (splitstree6.main.AppProfile.getProfile().shouldReplaceWorkflow(workflow)) {
						if (!rebuildAroundInput(mainWindow, fileName, exceptionHandler))
							return;   // failure already reported
					}
					if (runOnSuccess != null)
						runOnSuccess.run();
				} else {
					NotificationManager.showInformation("Workflow loaded, now use the File-> Replace Data... menu item to load data");
				}
			});

			service.setOnFailed(e -> {
				// Try to salvage: if we have taxa + an input data block, rebuild from those.
				var taxa = newWorkflow.getInputTaxaBlock();
				var data = (DataBlock) newWorkflow.getInputDataBlock();
				if (taxa != null && taxa.size() > 0 && data != null) {
					if (splitstree6.main.AppProfile.getProfile().shouldReplaceWorkflow(newWorkflow)) {
						// The profile would have replaced the workflow anyway — try the rebuild.
						if (rebuildAroundInput(mainWindow, fileName, exceptionHandler)) {
							if (runOnSuccess != null)
								runOnSuccess.run();
							return;
						}
					}
					// Default fallback: open in the input editor
					mainWindow.setFileName(fileName);
					mainWindow.getPresenter().showInputEditor();
					if (mainWindow.getTabByClass(InputEditorTab.class) instanceof InputEditorTab editorTab) {
						try {
							var w = new StringWriter();
							(new NexusExporter()).export(w, taxa, data);
							Platform.runLater(() -> {
								if (editorTab.getView() instanceof InputEditorView view) {
									view.replaceText("#nexus\n\n" + w.toString());
								}
							});
						} catch (IOException ignored) {
						}
					}
				} else {
					if (exceptionHandler != null) {
						if (AppProfile.getProfile().isExtension())
							exceptionHandler.accept(new IOException(AppProfile.getProfile().getName() + ": File not suitable: " + FileUtils.getFileNameWithoutPath(fileName)));
						else
							exceptionHandler.accept(service.getException());
					} else
						NotificationManager.showError("Open file failed : " + service.getException());
				}
			});
			service.setOnCancelled(e -> NotificationManager.showError("Open file : canceled"));
			service.start();
		}
		if (fileName.endsWith(".stree6"))
			mainWindow.setHasSplitsTree6File(true);
		mainWindow.setFileName(fileName);
	}

	/**
	 * input a work flow from a reader
	 */
	public static void input(ProgressListener progress, Workflow workflow, Reader reader) throws IOException {
		try (NexusStreamParser np = new NexusStreamParser(reader)) {
			np.setCollectAllComments(false);
			np.setCollectAllCommentsWithExclamationMark(true);
			np.matchIgnoreCase("#nexus");

			final SplitsTree6Block splitsTree6Block = new SplitsTree6Block();
			(new SplitsTree6NexusInput()).parse(np, splitsTree6Block);
			// todo: check input based on splitsTree6Block

			final NexusDataBlockInput dataInput = new NexusDataBlockInput();

			var inputTaxaBlock = dataInput.parse(np);
			TaxaNexusInput.captureComments(np, inputTaxaBlock);

			if (np.peekMatchBeginBlock("traits")) {
				inputTaxaBlock.setTraitsBlock((TraitsBlock) dataInput.parse(np, inputTaxaBlock));
			}

			if (np.peekMatchBeginBlock("sets")) {
				inputTaxaBlock.setSetsBlock((SetsBlock) dataInput.parse(np, inputTaxaBlock));
			}

			var taxaFilter = (new AlgorithmNexusInput()).parse(np);
			if (!(taxaFilter instanceof TaxaFilter))
				throw new IOExceptionWithLineNumber("Expected TaxaFilter", np.lineno());
			var workingTaxaBlock = dataInput.parse(np);
			workingTaxaBlock.overwriteTaxa(inputTaxaBlock);
			if (np.peekMatchBeginBlock("traits")) {
				workingTaxaBlock.setTraitsBlock((TraitsBlock) dataInput.parse(np, workingTaxaBlock));
			}
			if (np.peekMatchBeginBlock("sets")) {
				workingTaxaBlock.setSetsBlock((SetsBlock) dataInput.parse(np, workingTaxaBlock));
			}
			var workingTaxaTitle = dataInput.getTitle();
			var inputDataBlock = dataInput.parse(np, inputTaxaBlock);
			var dataTaxaFilter = (new AlgorithmNexusInput()).parse(np);
			if (!(dataTaxaFilter instanceof DataTaxaFilter))
				throw new IOExceptionWithLineNumber("Expected DataTaxaFilter", np.lineno());
			if (dataTaxaFilter.getFromClass() != inputDataBlock.getClass())
				throw new IOExceptionWithLineNumber("Input data and DataTaxaFilter of incompatible types", np.lineno());
			var workingDataBlock = dataInput.parse(np, workingTaxaBlock);
			var workingDataTitle = dataInput.getTitle();
			if (dataTaxaFilter.getToClass() != workingDataBlock.getClass())
				throw new IOExceptionWithLineNumber("Working data and DataTaxaFilter of incompatible types", np.lineno());

			workflow.setupInputAndWorkingNodes(new SourceBlock(), inputTaxaBlock, (TaxaFilter) taxaFilter, workingTaxaBlock, inputDataBlock, (DataTaxaFilter) dataTaxaFilter, workingDataBlock);

			final var titleNodeMap = new HashMap<String, DataNode>();
			titleNodeMap.put(workingTaxaTitle, workflow.getWorkingTaxaNode());
			titleNodeMap.put(workingDataTitle, workflow.getWorkingDataNode());

			final var title2algorithmAndLink = new HashMap<String, Pair<Algorithm, String>>();

			while (np.peekMatchIgnoreCase("begin")) {
				if (np.peekMatchBeginBlock("algorithm")) {
					final AlgorithmNexusInput algorithmInput = new AlgorithmNexusInput();
					final Algorithm algorithm = algorithmInput.parse(np);
					title2algorithmAndLink.put(algorithmInput.getTitle(), new Pair<>(algorithm, algorithmInput.getLink().getSecond()));
				} else {
					final DataBlock newDataBlock = dataInput.parse(np, workingTaxaBlock);
					/*
					if (dataBlock instanceof TraitsBlock)
						taxaBlock.setTraitsBlock((TraitsBlock) dataBlock);

					 */
					final DataNode newDataNode = workflow.newDataNode(newDataBlock);
					if (dataInput.getLink() != null) {
						final var algorithmAndLink = title2algorithmAndLink.get(dataInput.getLink().getSecond());
						final Algorithm algorithm = algorithmAndLink.getFirst();
						final DataNode parentDataNode = titleNodeMap.get(algorithmAndLink.getSecond());
						workflow.newAlgorithmNode(algorithm, workflow.getWorkingTaxaNode(), parentDataNode, newDataNode);
					}
					titleNodeMap.put(dataInput.getTitle(), newDataNode);
				}
				progress.setProgress(np.lineno());
			}
		} catch (Exception ex) {
			Basic.caught(ex);
			throw ex;
		}
	}


	private static boolean isWorkflowFile(String inputFile) {
		return inputFile.toLowerCase().endsWith(WORKFLOW_FILE_SUFFIX) || inputFile.toLowerCase().endsWith(WORKFLOW_FILE_SUFFIX + ".gz");
	}

	/**
	 * Inspect the working data block; if it's a CharactersBlock or DistancesBlock,
	 * tear down any current pipeline and rebuild via WorkflowSetup (which invokes
	 * the host profile's setupWorkflow). Returns true on success, false if the
	 * file's contents are not salvageable for this profile (an error is shown).
	 */
	private static boolean rebuildAroundInput(MainWindow mainWindow, String fileName,
											  Consumer<Throwable> exceptionHandler) {
		var workflow = mainWindow.getWorkflow();
		var working = workflow.getWorkingDataNode();
		Class<? extends DataBlock> inputType = null;
		if (working != null) {
			var block = working.getDataBlock();
			if (block instanceof DistancesBlock) inputType = DistancesBlock.class;
			else if (block instanceof CharactersBlock) inputType = CharactersBlock.class;
		}
		if (inputType == null) {
			NotificationManager.showError(
					"File does not contain distances or characters — cannot open in " +
					splitstree6.main.AppProfile.getProfile().getName());
			workflow.clear();
			mainWindow.setFileName("");
			mainWindow.setDirty(false);
			return false;
		}
		splitstree6.workflow.WorkflowSetup.apply(fileName, workflow, exceptionHandler, null, inputType);
		mainWindow.setDirty(false);
		return true;
	}
}
