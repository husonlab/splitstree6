/*
 *  InputEditorTabPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.inputeditor;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.NotificationManager;
import jloda.util.Basic;
import jloda.util.ProgramProperties;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.io.nexus.AlgorithmNexusOutput;
import splitstree6.io.nexus.CharactersNexusOutput;
import splitstree6.io.nexus.DistancesNexusOutput;
import splitstree6.io.nexus.TaxaNexusOutput;
import splitstree6.io.readers.ImportManager;
import splitstree6.tabs.textdisplay.TextDisplayTab;
import splitstree6.tabs.textdisplay.TextDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * input editor tab presenter
 * Daniel Huson, 10.2021
 */
public class InputEditorTabPresenter extends TextDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final InputEditorTab tab;

	private final BooleanBinding selectionEmpty;

	private final ReadOnlyBooleanProperty TRUE = new SimpleBooleanProperty(true);

	public InputEditorTabPresenter(MainWindow mainWindow, InputEditorTab tab) {
		super(mainWindow, tab, true);
		this.mainWindow = mainWindow;
		this.tab = tab;

		tab.setShowLineNumbers(true);
		tab.setWrapText(false);

		tab.setGraphic(new ImageView(ResourceManagerFX.getIcon("sun/Import16.gif")));

		var tabController = tab.getController();
		var toolBarController = tab.getToolBarController();

		var codeArea = tabController.getCodeArea();
		codeArea.setEditable(true);

		codeArea.getStyleClass().add("text");

		var list = new ArrayList<>(toolBarController.getFirstToolBar().getItems());
		list.addAll(tabController.getToolBar().getItems());
		list.addAll(toolBarController.getLastToolBar().getItems());
		tabController.getToolBar().getItems().setAll(list);

		toolBarController.getParseAndLoadButton().setOnAction(e -> tab.parseAndLoad());
		toolBarController.getParseAndLoadButton().disableProperty().bind(tab.isEmptyProperty().or(mainWindow.getWorkflow().runningProperty()));

		toolBarController.getOpenButton().setOnAction(e -> {
			final var previousDir = new File(ProgramProperties.get("InputDir", ""));
			final var fileChooser = new FileChooser();
			if (previousDir.isDirectory())
				fileChooser.setInitialDirectory(previousDir);
			fileChooser.setTitle("Open input file");
			fileChooser.getExtensionFilters().addAll(ImportManager.getInstance().getExtensionFilters());
			final var selectedFile = fileChooser.showOpenDialog(mainWindow.getStage());
			if (selectedFile != null) {
				if (selectedFile.getParentFile().isDirectory())
					ProgramProperties.put("InputDir", selectedFile.getParent());
				tab.importFromFile(selectedFile.getPath());
			}
		});
		toolBarController.getOpenButton().disableProperty().bind(tab.isEmptyProperty().not());

		toolBarController.getSaveButton().setOnAction(e -> {
			var fileChooser = new FileChooser();
			fileChooser.setTitle("Save input text");
			var previousDir = new File(ProgramProperties.get("InputDir", ""));
			if (previousDir.isDirectory()) {
				fileChooser.setInitialDirectory(previousDir);
			}
			fileChooser.setInitialFileName(tab.getInputFileName());
			var selectedFile = fileChooser.showSaveDialog(mainWindow.getStage());
			if (selectedFile != null) {
				tab.saveToFile(selectedFile);
			}
		});
		toolBarController.getSaveButton().disableProperty().bind(tab.isEmptyProperty());

		selectionEmpty = new BooleanBinding() {
			{
				super.bind(codeArea.selectionProperty());
			}

			@Override
			protected boolean computeValue() {
				return codeArea.getSelection().getLength() == 0;
			}
		};

		// prevent double paste:
		{
			codeArea.addEventFilter(KeyEvent.ANY, e -> {
				if (e.getCode() == KeyCode.V && e.isShortcutDown()) {
					e.consume();
				}
			});
		}

		codeArea.focusedProperty().addListener((c, o, n) -> {
			if (n)
				mainWindow.getController().getPasteMenuItem().disableProperty().set(!Clipboard.getSystemClipboard().hasString());
		});

		// todo: for testing:
		mainWindow.getWorkflow().runningProperty().addListener((v, o, n) -> {
			if (!n) {
				var workflow = mainWindow.getWorkflow();
				if (workflow.getException() != null)
					NotificationManager.showError("Workflow failed: " + workflow.getException().getMessage());
				if (true) {
					if (workflow.getWorkingTaxaNode() != null) {
						try (var w = new StringWriter()) {
							var textTab = new TextDisplayTab(mainWindow, "Working taxa", true, false);
							(new TaxaNexusOutput()).write(w, workflow.getWorkingTaxaNode().getDataBlock());
							textTab.replaceText(w.toString());
							mainWindow.addTabToMainTabPane(textTab);
						} catch (IOException ex) {
							Basic.caught(ex);
						}
					}
				}
				if (true) {
					for (var node : workflow.dataNodes()) {
						var data = (DataBlock) node.getDataBlock();
						if (data instanceof DistancesBlock distancesBlock) {
							try (var w = new StringWriter()) {
								var textTab = new TextDisplayTab(mainWindow, distancesBlock.getName(), true, false);
								(new DistancesNexusOutput()).write(w, workflow.getWorkingTaxaNode().getDataBlock(), distancesBlock);
								textTab.replaceText(w.toString());
								mainWindow.addTabToMainTabPane(textTab);
							} catch (IOException ex) {
								Basic.caught(ex);
							}
						}
					}
				}
				if (true) {
					for (var node : workflow.dataNodes()) {
						var data = (DataBlock) node.getDataBlock();
						if (data instanceof CharactersBlock charactersBlock) {
							try (var w = new StringWriter()) {
								String name;
								if (node == workflow.getInputDataNode())
									name = "Input Characters";
								else if (node == workflow.getWorkingDataNode())
									name = "Working Characters";
								else
									name = "Characters";
								var textTab = new TextDisplayTab(mainWindow, name, true, false);
								(new CharactersNexusOutput()).write(w, workflow.getWorkingTaxaNode().getDataBlock(), charactersBlock);
								textTab.replaceText(w.toString());
								mainWindow.addTabToMainTabPane(textTab);
							} catch (IOException ex) {
								Basic.caught(ex);
							}
						}
					}
				}
				if (false) {
					for (var node : workflow.algorithmNodes()) {
						var algorithm = (Algorithm) node.getAlgorithm();
						try (var w = new StringWriter()) {
							var textTab = new TextDisplayTab(mainWindow, algorithm.getName(), true, false);
							(new AlgorithmNexusOutput()).write(w, algorithm);
							textTab.replaceText(w.toString());
							mainWindow.addTabToMainTabPane(textTab);
						} catch (IOException ex) {
							Basic.caught(ex);
						}
					}
				}
			}
		});
	}

	public void setup() {
		super.setup();

		var controller = mainWindow.getController();
		var toolBarController = tab.getToolBarController();

		controller.getOpenMenuItem().setOnAction(toolBarController.getOpenButton().getOnAction());
		controller.getOpenMenuItem().disableProperty().bind(toolBarController.getOpenButton().disableProperty());

		controller.getImportMenuItem().disableProperty().bind(TRUE);
		controller.getReplaceDataMenuItem().disableProperty().bind(TRUE);
		controller.getAnalyzeGenomesMenuItem().disableProperty().bind(TRUE);
		controller.getInputEditorMenuItem().disableProperty().bind(TRUE);

		controller.getOpenRecentMenu().disableProperty().bind(TRUE);

		controller.getImportMultipleTreeFilesMenuItem().disableProperty().bind(TRUE);
	}
}
