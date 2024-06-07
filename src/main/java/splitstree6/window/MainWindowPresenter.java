/*
 *  MainWindowPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.window;


import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.dialog.ExportImageDialog;
import jloda.fx.dialog.SetParameterDialog;
import jloda.fx.message.MessageWindow;
import jloda.fx.util.*;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.fx.window.SplashScreen;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.ProgramProperties;
import jloda.util.*;
import splitstree6.algorithms.characters.characters2distances.GeneContentDistance;
import splitstree6.algorithms.characters.characters2distances.LogDet;
import splitstree6.algorithms.characters.characters2distances.PDistance;
import splitstree6.algorithms.characters.characters2distances.ProteinMLDistance;
import splitstree6.algorithms.characters.characters2distances.nucleotide.*;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.characters.characters2report.EstimateInvariableSites;
import splitstree6.algorithms.characters.characters2report.PhiTest;
import splitstree6.algorithms.characters.characters2report.TajimaD;
import splitstree6.algorithms.characters.characters2splits.ParsimonySplits;
import splitstree6.algorithms.distances.distances2network.MinSpanningNetwork;
import splitstree6.algorithms.distances.distances2network.PCOA;
import splitstree6.algorithms.distances.distances2report.DeltaScore;
import splitstree6.algorithms.distances.distances2splits.BunemanTree;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.distances.distances2splits.SplitDecomposition;
import splitstree6.algorithms.distances.distances2trees.BioNJ;
import splitstree6.algorithms.distances.distances2trees.MinSpanningTree;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.algorithms.splits.splits2report.ShapleyValues;
import splitstree6.algorithms.splits.splits2splits.BootstrapSplits;
import splitstree6.algorithms.splits.splits2splits.SplitsFilter;
import splitstree6.algorithms.splits.splits2splits.WeightsSlider;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.algorithms.trees.trees2report.PhylogeneticDiversity;
import splitstree6.algorithms.trees.trees2report.TreeDiversityIndex;
import splitstree6.algorithms.trees.trees2report.UnrootedShapleyValues;
import splitstree6.algorithms.trees.trees2splits.*;
import splitstree6.algorithms.trees.trees2trees.*;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.dialog.AskToDiscardChanges;
import splitstree6.dialog.SaveBeforeClosingDialog;
import splitstree6.dialog.SaveDialog;
import splitstree6.dialog.analyzegenomes.AnalyzeGenomesDialog;
import splitstree6.dialog.exporting.ExportTaxonDisplayLabels;
import splitstree6.dialog.exporting.ExportTaxonTraits;
import splitstree6.dialog.importdialog.ImportDialog;
import splitstree6.dialog.importing.ImportMultipleTrees;
import splitstree6.dialog.importing.ImportTaxonDisplayLabels;
import splitstree6.dialog.importing.ImportTaxonTraits;
import splitstree6.dialog.importing.ImportTreeNames;
import splitstree6.io.FileLoader;
import splitstree6.io.nexus.CharactersNexusOutput;
import splitstree6.io.nexus.TaxaNexusOutput;
import splitstree6.io.nexus.TraitsNexusOutput;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.utils.ReaderWriterBase;
import splitstree6.main.CheckForUpdate;
import splitstree6.main.SplitsTree6;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.utils.CollapseIdenticalHyplotypes;
import splitstree6.view.alignment.AlignmentView;
import splitstree6.view.displaytext.DisplayTextViewPresenter;
import splitstree6.view.network.NetworkViewPresenter;
import splitstree6.view.utils.ExportUtils;
import splitstree6.workflow.*;
import splitstree6.xtra.latex.MenusToLaTeX;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MainWindowPresenter {
	private final MainWindow mainWindow;
	private Stage stage;
	private final MainWindowController controller;
	private final ObjectProperty<IDisplayTab> selectedDisplayTab = new SimpleObjectProperty<>();

	private final ObservableMap<WorkflowNode, Tab> workFlowTabs = FXCollections.observableHashMap();

	private final SplitPanePresenter splitPanePresenter;

	private final EventHandler<KeyEvent> keyEventEventHandler;

	private final HashMap<String, Pair<Algorithm, CheckMenuItem>> nameAlgorithmMenuItemMap = new HashMap<>();

	public MainWindowPresenter(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		controller = mainWindow.getController();

		mainWindow.getWorkflow().runningProperty().addListener((v, o, n) -> {
			if (!n) {
				WorkflowUtils.ensureAlignmentView(mainWindow.getWorkflow());
				WorkflowUtils.ensureWorldMapView(mainWindow.getWorkflow(), false);
				TaxonSetSupport.updateSetsMenu(mainWindow, controller.getSelectSetsMenu(),
						label -> {
							var t = mainWindow.getWorkingTaxa().indexOf(label);
							if (t > 0)
								mainWindow.getTaxonSelectionModel().select(mainWindow.getWorkingTaxa().get(t));
						});
			}
		});

		controller.getBottomFlowPane().getChildren().addListener((InvalidationListener) e -> controller.getProgressIndicator().setVisible(!controller.getBottomFlowPane().getChildren().isEmpty()));
		controller.getProgressIndicator().setProgress(-1);

		var workflowTreeView = mainWindow.getWorkflowTreeView();

		controller.getWorkflowBorderPane().setCenter(workflowTreeView);

		keyEventEventHandler = e -> {
			if (mainWindow.getStage().isFocused()) {
				if ((e.getCharacter().equals("=") || e.getCode() == KeyCode.EQUALS || e.getCharacter().equals("+") || e.getCode() == KeyCode.ADD || e.getCode() == KeyCode.PLUS) && e.isShortcutDown()
					&& controller.getIncreaseFontSizeMenuItem().getOnAction() != null && !controller.getIncreaseFontSizeMenuItem().isDisable()) {
					controller.getIncreaseFontSizeMenuItem().fire();
					e.consume();
				} else if ((e.getCharacter().equals("-") || e.getCode() == KeyCode.SUBTRACT || e.getCode() == KeyCode.MINUS) && controller.getDecreaseFontSizeMenuItem().getOnAction() != null && !controller.getDecreaseFontSizeMenuItem().isDisable()) {
					controller.getDecreaseFontSizeMenuItem().fire();
					e.consume();
				}
			}
		};

		InvalidationListener listener = e -> {
			try {
				var tab = controller.getMainTabPane().getSelectionModel().getSelectedItem();
				disableAllMenuItems(controller);
				if (tab instanceof IDisplayTab displayTab) {
					selectedDisplayTab.set(displayTab);
				} else
					selectedDisplayTab.set(null);
				setupCommonMenuItems(mainWindow, controller, selectedDisplayTab);

				if (selectedDisplayTab.get() != null && selectedDisplayTab.get().getPresenter() != null) {
					ExportUtils.setup(mainWindow, null, null);
					selectedDisplayTab.get().getPresenter().setupMenuItems();
				}
				enableAllMenuItemsWithDefinedAction(controller);
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		};

		controller.getMainTabPane().focusedProperty().addListener(listener);
		controller.getMainTabPane().getSelectionModel().selectedItemProperty().addListener(listener);

		if (controller.getMainTabPane().getSelectionModel().getSelectedItem() instanceof IDisplayTab displayTab)
			Platform.runLater(() -> selectedDisplayTab.set(displayTab));

		controller.getAlgorithmTabPane().getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			try {
				disableAllMenuItems(controller);
				setupCommonMenuItems(mainWindow, controller, selectedDisplayTab);
				enableAllMenuItemsWithDefinedAction(controller);
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});

		controller.getMainTabPane().getTabs().addListener((ListChangeListener<? super Tab>) e -> {
			while (e.next()) {
				if (e.wasRemoved()) {
					for (var tab : e.getRemoved()) {
						for (var workflowNode : workFlowTabs.keySet()) {
							if (workFlowTabs.get(workflowNode) == tab) {
								workFlowTabs.remove(workflowNode);
								break;
							}
						}
					}
				}
			}
		});

		RecentFilesManager.getInstance().setFileOpener(fileName -> FileLoader.apply(false, mainWindow, fileName, ex -> NotificationManager.showError("Open recent file failed: " + ex)));

		RecentFilesManager.getInstance().setupMenu(controller.getOpenRecentMenu());

		splitPanePresenter = new SplitPanePresenter(mainWindow.getController());

		applyToAllMenuItems(controller.getMenuBar(),
				m -> !List.of("File", "Edit", "Import", "Window", "Open Recent", "Help").contains(m.getText()),
				m -> m.disableProperty().bind(mainWindow.getWorkflow().runningProperty().or(mainWindow.emptyProperty())));
		applyToAllMenuItems(controller.getMenuBar(),
				m -> m.getText().equals("Edit"),
				m -> m.disableProperty().bind(mainWindow.getWorkflow().runningProperty()));

		controller.getFindButton().setOnAction(e -> {
			if (controller.getReplaceMenuItem().isSelected()) {
				controller.getFindMenuItem().setSelected(false);
				controller.getReplaceMenuItem().setSelected(false);
			} else if (controller.getFindMenuItem().isSelected()) {
				controller.getFindMenuItem().setSelected(false);
				if (controller.getReplaceMenuItem().isSelected()) {
					controller.getReplaceMenuItem().setSelected(false);
				}
				if (!controller.getReplaceMenuItem().isDisable()) {
					controller.getReplaceMenuItem().setSelected(true);
				}
			} else {
				controller.getFindMenuItem().setSelected(true);
			}
		});
		controller.getFindButton().disableProperty().bind(controller.getFindMenuItem().disableProperty());

		ImportButtonUtils.setup(controller.getImportButton());

		SwipeUtils.setConsumeSwipes(controller.getRootPane());
	}


	public void setStage(Stage stage) {
		this.stage = stage;
		stage.addEventFilter(KeyEvent.KEY_TYPED, keyEventEventHandler);
		stage.getScene().focusOwnerProperty().addListener((v, o, n) -> {
			try {
				var displayTab = getContainingDisplayTab(n);
				if (displayTab != null) {
					selectedDisplayTab.set(displayTab);
					disableAllMenuItems(controller);
					setupCommonMenuItems(mainWindow, controller, selectedDisplayTab);
					if (selectedDisplayTab.get() != null && selectedDisplayTab.get().getPresenter() != null)
						selectedDisplayTab.get().getPresenter().setupMenuItems();
					if (false) // todo: if this is enabled, then menu items are enabled that shouldn't be...
						enableAllMenuItemsWithDefinedAction(controller);
					if (true)
						updateEnableStateAlgorithms();
					controller.getNewMenuItem().disableProperty().bind(new SimpleBooleanProperty(false));
				}
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});

		stage.focusedProperty().addListener((v, o, n) -> {
			if (!n && !mainWindow.getTaxonSelectionModel().getSelectedItems().isEmpty()) {
				MainWindowManager.getPreviousSelection().clear();
				mainWindow.getTaxonSelectionModel().getSelectedItems().stream().map(Taxon::getName).filter(s -> !s.isBlank()).forEach(s -> MainWindowManager.getPreviousSelection().add(s));
			}
		});

		stage.setOnCloseRequest(e -> {
			controller.getCloseMenuItem().getOnAction().handle(null);
			e.consume();
		});


		controller.getUseFullScreenMenuItem().disableProperty().unbind();
		BasicFX.setupFullScreenMenuSupport(stage, controller.getUseFullScreenMenuItem());

		Platform.runLater(() -> {
			if (mainWindow.getController().getMainTabPane().getSelectionModel().getSelectedItem() instanceof IDisplayTab displayTab) {
				selectedDisplayTab.set(displayTab);
			}
		});
	}

	private Consumer<MainWindow> additionalCommonMenuSetup = null;

	public void setAdditionalCommonMenuSetup(Consumer<MainWindow> additionalCommonMenuSetup) {
		this.additionalCommonMenuSetup = additionalCommonMenuSetup;
	}

	private void setupCommonMenuItems(MainWindow mainWindow, MainWindowController controller, ObjectProperty<IDisplayTab> focusedDisplayTab) {
		var workflow = mainWindow.getWorkflow();

		controller.getNewMenuItem().setOnAction(e -> MainWindowManager.getInstance().createAndShowWindow(false));

		var loadingFile = new SimpleBooleanProperty(this, "loadingFile", false);

		controller.getOpenMenuItem().setOnAction(e -> {
			{
				var previousDir = new File(ProgramProperties.get("InputDir", ""));
				var fileChooser = new FileChooser();
				if (previousDir.isDirectory())
					fileChooser.setInitialDirectory(previousDir);
				fileChooser.setTitle("Open input file");
				fileChooser.getExtensionFilters().addAll(ImportManager.getInstance().getExtensionFilters());
				var selectedFile = fileChooser.showOpenDialog(stage);
				if (selectedFile != null) {
					if (!selectedFile.getParent().isBlank())
						ProgramProperties.put("InputDir", selectedFile.getParent());
					if (!loadingFile.get()) {
						try {
							loadingFile.set(true);
							FileLoader.apply(false, mainWindow, selectedFile.getPath(), ex -> NotificationManager.showError("Open file failed: " + ex));
						} finally {
							loadingFile.set(false);
						}
					}
				}
			}
		});
		controller.getOpenMenuItem().disableProperty().bind(workflow.runningProperty().or(loadingFile));

		controller.getOpenMenuItem().setOnAction(controller.getOpenMenuItem().getOnAction());
		controller.getOpenMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getImportDialogMenuItem().setOnAction(e -> ImportDialog.show(mainWindow, ""));
		controller.getImportDialogMenuItem().disableProperty().bind(workflow.runningProperty().or(mainWindow.emptyProperty().not()));

		controller.getImportTaxonDisplayMenuItem().setOnAction(e -> ImportTaxonDisplayLabels.apply(mainWindow));
		controller.getImportTaxonDisplayMenuItem().disableProperty().bind(workflow.runningProperty().or(mainWindow.emptyProperty()));

		controller.getImportTaxonTraitsMenuItem().setOnAction(e -> ImportTaxonTraits.apply(mainWindow));
		controller.getImportTaxonTraitsMenuItem().disableProperty().bind(workflow.runningProperty().or(mainWindow.emptyProperty()));

		controller.getImportMultipleTreeFilesMenuItem().setOnAction(e -> ImportMultipleTrees.apply(mainWindow));
		controller.getImportMultipleTreeFilesMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getImportTreeNamesMenuItem().setOnAction(e -> ImportTreeNames.apply(mainWindow));
		controller.getImportTreeNamesMenuItem().disableProperty().bind(workflow.runningProperty().or(
				Bindings.createBooleanBinding(() -> !(mainWindow.getWorkflow().getInputDataBlock() instanceof TreesBlock), mainWindow.getWorkflow().runningProperty())));

		setupReplaceDataMenuItem(mainWindow, controller.getReplaceDataMenuItem());

		controller.getEditInputMenuItem().setOnAction(e -> showInputEditor());
		controller.getEditInputMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getAnalyzeGenomesMenuItem().setOnAction(e -> (new AnalyzeGenomesDialog(stage)).show());
		controller.getAnalyzeGenomesMenuItem().disableProperty().bind(controller.getOpenMenuItem().disableProperty());

		controller.getSaveMenuItem().setOnAction(e -> {
			if (mainWindow.isHasSplitsTree6File())
				SaveDialog.save(mainWindow, false, new File(mainWindow.getFileName()));
			else
				controller.getSaveAsMenuItem().getOnAction().handle(e);
		});
		controller.getSaveMenuItem().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()).or(mainWindow.dirtyProperty().not()));

		controller.getSaveAsMenuItem().setOnAction(e -> SaveDialog.showSaveDialog(mainWindow, false));
		controller.getSaveAsMenuItem().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()));

		controller.getExportTaxonDisplayLabelsMenuItem().setOnAction(e -> ExportTaxonDisplayLabels.apply(mainWindow));
		controller.getExportTaxonDisplayLabelsMenuItem().disableProperty().bind(workflow.runningProperty().or(Bindings.createBooleanBinding(() -> workflow.getWorkingTaxaBlock() == null, workflow.validProperty())));

		controller.getExportTaxonTraitsMenuItem().setOnAction(e -> ExportTaxonTraits.apply(mainWindow));
		controller.getExportTaxonTraitsMenuItem().disableProperty().bind(workflow.runningProperty().or(Bindings.createBooleanBinding(() -> workflow.getWorkingTaxaBlock() == null || workflow.getWorkingTaxaBlock().getTraitsBlock() == null, workflow.validProperty())));

		controller.getExportWorkflowMenuItem().setOnAction(e -> SaveDialog.showSaveDialog(mainWindow, true));
		controller.getExportWorkflowMenuItem().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()));

		controller.getPageSetupMenuItem().setOnAction(e -> Print.showPageLayout(stage));
		controller.getPageSetupMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getGroupIdenticalHaplotypesMenuItem().setOnAction(e -> {
			if (workflow.getWorkingDataBlock() instanceof CharactersBlock charactersBlock) {
				var output = CollapseIdenticalHyplotypes.apply(workflow.getWorkingTaxaBlock(), charactersBlock);
				if (output != null) {
					try {
						var w = new StringWriter();
						w.write("#nexus\n");
						(new TaxaNexusOutput()).write(w, output.getFirst());
						(new TraitsNexusOutput()).write(w, output.getFirst(), output.getSecond());
						(new CharactersNexusOutput()).write(w, output.getFirst(), output.getThird());
						ImportButtonUtils.openString(w.toString());
					} catch (IOException ex) {
						NotificationManager.showError("Failed: " + ex.getMessage());
					}
				}
			}

		});
		controller.getGroupIdenticalHaplotypesMenuItem().disableProperty().bind(controller.getFilterCharactersMenuItem().disableProperty());

		controller.getQuitMenuItem().setOnAction(e -> {
			while (MainWindowManager.getInstance().size() > 0) {
				final MainWindow aWindow = (MainWindow) MainWindowManager.getInstance().getMainWindow(MainWindowManager.getInstance().size() - 1);
				if (SaveBeforeClosingDialog.apply(aWindow) == SaveBeforeClosingDialog.Result.cancel || !MainWindowManager.getInstance().closeMainWindow(aWindow))
					break;
			}
		});

		controller.getCloseMenuItem().setOnAction(e -> {
			if (SaveBeforeClosingDialog.apply(mainWindow) != SaveBeforeClosingDialog.Result.cancel) {
				if (MainWindowManager.getInstance().closeMainWindow(mainWindow))
					mainWindow.getWorkflow().cancel();
			}
		});
		controller.getCloseMenuItem().setDisable(false);

		updateUndoRedo();
		setupSelectButton();

		workflow.runningProperty().addListener((v, o, n) -> {
			// todo: need to do this more generically, at present only network view sets up its own select button
			if (!n && !(getSelectedDisplayTab() != null && getSelectedDisplayTab().getPresenter() instanceof NetworkViewPresenter))
				setupSelectButton();
		});

		if (!controller.getCutMenuItem().disableProperty().isBound())
			controller.getCutMenuItem().setDisable(false);
		if (!controller.getCopyMenuItem().disableProperty().isBound())
			controller.getCopyMenuItem().setDisable(false);

		if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getMainNode() != null) {
			controller.getCopyImageMenuItem().setOnAction(e -> {
				try {
					var snapshot = focusedDisplayTab.get().getMainNode().snapshot(null, null);
					ClipboardUtils.putImage(snapshot);
				} catch (Exception ex) {
					Basic.caught(ex);
				}
			});
		}
		controller.getCopyImageMenuItem().disableProperty().bind(focusedDisplayTab.isNull());

		controller.getCopyNewickMenuItem().setDisable(true);

		controller.getPasteMenuItem().setDisable(true);

		controller.getExportImageMenuItem().setOnAction(e -> {
			if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getMainNode() != null)
				ExportImageDialog.show(mainWindow.getFileName(), mainWindow.getStage(), focusedDisplayTab.get().getMainNode());
		});
		controller.getExportImageMenuItem().disableProperty().bind(focusedDisplayTab.isNull());


		// controller.getDuplicateMenuItem().setOnAction(null);
		// controller.getDeleteMenuItem().setOnAction(null);

		if (controller.getFindMenuItem().getUserData() instanceof BooleanProperty booleanProperty) {
			controller.getFindMenuItem().selectedProperty().unbindBidirectional(booleanProperty);
			if (!MenusToLaTeX.active)
				controller.getFindMenuItem().setUserData(null);
		}
		controller.getFindMenuItem().setDisable(true);
		if (controller.getReplaceMenuItem().getUserData() instanceof BooleanProperty booleanProperty) {
			controller.getReplaceMenuItem().selectedProperty().unbindBidirectional(booleanProperty);
			if (!MenusToLaTeX.active)
				controller.getReplaceMenuItem().setUserData(null);
		}
		controller.getReplaceMenuItem().setDisable(true);

		if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getPresenter() != null) {
			var findToolBar = focusedDisplayTab.get().getPresenter().getFindToolBar();
			if (findToolBar != null) {
				{
					var booleanProperty = findToolBar.showFindToolBarProperty();
					controller.getFindMenuItem().selectedProperty().bindBidirectional(booleanProperty);
					if (!MenusToLaTeX.active)
						controller.getFindMenuItem().setUserData(booleanProperty);
					controller.getFindMenuItem().setDisable(false);
				}
				{
					if (focusedDisplayTab.get().getPresenter().allowFindReplace()) {
						var booleanProperty = findToolBar.showReplaceToolBarProperty();
						controller.getReplaceMenuItem().selectedProperty().bindBidirectional(booleanProperty);
						if (!MenusToLaTeX.active)
							controller.getReplaceMenuItem().setUserData(booleanProperty);
						controller.getReplaceMenuItem().setDisable(false);
					}
				}
				{
					controller.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
					controller.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
				}
			} else {
				controller.getFindMenuItem().setSelected(false);
				controller.getFindMenuItem().setDisable(true);
				controller.getReplaceMenuItem().setSelected(false);
				controller.getReplaceMenuItem().setDisable(true);
				controller.getFindAgainMenuItem().setDisable(true);
			}
		}

		// controller.getGotoLineMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()));
		controller.getSelectAllMenuItem().disableProperty().bind(mainWindow.emptyProperty());

		controller.getSelectNoneMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().clearSelection());
		controller.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		controller.getSelectInverseMenuItem().setOnAction(e -> mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa().forEach(t -> mainWindow.getTaxonSelectionModel().toggleSelection(t)));
		controller.getSelectInverseMenuItem().disableProperty().bind(mainWindow.emptyProperty());

		controller.getSelectFromPreviousMenuItem().setOnAction(e -> {
			var taxonBlock = workflow.getWorkingTaxaBlock();
			if (taxonBlock != null) {
				MainWindowManager.getPreviousSelection().stream().map(taxonBlock::get).filter(Objects::nonNull).forEach(t -> mainWindow.getTaxonSelectionModel().select(t));
			}
		});
		controller.getSelectFromPreviousMenuItem().disableProperty().bind(Bindings.isEmpty(MainWindowManager.getPreviousSelection()).or(mainWindow.emptyProperty()));

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		/*
		controller.getZoomInMenuItem().setOnAction(null);
		controller.getZoomOutMenuItem().setOnAction(null);
		*/

		// controller.getResetMenuItem().setOnAction(null);
		// controller.getRotateLeftMenuItem().setOnAction(null);
		// controller.getRotateRightMenuItem().setOnAction(null);
		//controller.getFlipMenuItem().setOnAction(null);
		// controller.getWrapTextMenuItem().setOnAction(null);
		// controller.getFormatNodesMenuItem().setOnAction(null);
		// controller.getLayoutLabelsMenuItem().setOnAction(null);
		// controller.getSparseLabelsCheckMenuItem().setOnAction(null);
		// controller.getShowScaleBarMenuItem().setOnAction(null);

		controller.getUseDarkThemeMenuItem().selectedProperty().bindBidirectional(MainWindowManager.useDarkThemeProperty());
		controller.getUseDarkThemeMenuItem().setSelected(MainWindowManager.isUseDarkTheme());
		controller.getUseDarkThemeMenuItem().setDisable(false);

		controller.getFilterTaxaMenuItem().setOnAction(e -> {
			var nodes = workflow.getNodes(TaxaFilter.class);
			if (nodes.size() == 1)
				mainWindow.getAlgorithmTabsManager().showTab(nodes.iterator().next(), true);
		});
		controller.getFilterTaxaMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> workflow.getNodes(TaxaFilter.class).size() != 1, workflow.nodes()));

		controller.getFilterCharactersMenuItem().setOnAction(e -> {
			var tab = controller.getMainTabPane().getTabs().stream().filter(t -> t instanceof ViewTab && ((ViewTab) t).getView() instanceof AlignmentView).findAny();
			tab.ifPresent(value -> controller.getMainTabPane().getSelectionModel().select(value));
		});
		controller.getFilterCharactersMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> !(workflow.getWorkingDataNode() != null && workflow.getWorkingDataNode().getDataBlock() instanceof CharactersBlock), workflow.validProperty()));

		controller.getFilterTreesMenuItem().setOnAction(null);

		controller.getFilterTreesMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new TreesFilter()));
		controller.getFilterTreesMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new TreesFilter()));

		controller.getFilterSplitsMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new SplitsFilter()));
		controller.getFilterSplitsMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new SplitsFilter()));

		controller.getSplitsSliderMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new WeightsSlider()));
		controller.getSplitsSliderMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new WeightsSlider()));

		controller.getTraitsMenuItem().setOnAction(null);

		TaxonSetSupport.setupMenuItems(mainWindow);
		controller.getSelectSetsMenu().setOnShowing(e -> TaxonSetSupport.updateSetsMenu(mainWindow, controller.getSelectSetsMenu()));

		controller.getWorldMapMenuItem().setOnAction(e -> WorkflowUtils.ensureWorldMapView(mainWindow.getWorkflow(), true));
		controller.getWorldMapMenuItem().disableProperty().bind(workflow.runningProperty().or(mainWindow.emptyProperty()));

		setupAlgorithmMenuItem(controller.getpDistanceMenuItem(), new PDistance());
		setupAlgorithmMenuItem(controller.getLogDetMenuItem(), new LogDet());
		setupAlgorithmMenuItem(controller.getHky85MenuItem(), new HKY85Distance());
		setupAlgorithmMenuItem(controller.getJukesCantorMenuItem(), new JukesCantorDistance());

		setupAlgorithmMenuItem(controller.getK2pMenuItem(), new K2PDistance());
		setupAlgorithmMenuItem(controller.getF81MenuItem(), new F81Distance());
		setupAlgorithmMenuItem(controller.getF84MenuItem(), new F84Distance());
		setupAlgorithmMenuItem(controller.getProteinMLDistanceMenuItem(), new ProteinMLDistance());
		setupAlgorithmMenuItem(controller.getGeneContentDistanceMenuItem(), new GeneContentDistance());
		setupAlgorithmMenuItem(controller.getNjMenuItem(), new NeighborJoining());
		setupAlgorithmMenuItem(controller.getBioNJMenuItem(), new BioNJ());
		setupAlgorithmMenuItem(controller.getUpgmaMenuItem(), new UPGMA());

		setupAlgorithmMenuItem(controller.getBunemanTreeMenuItem(), new BunemanTree());
		setupAlgorithmMenuItem(controller.getConsensusTreeMenuItem(), new ConsensusTree(), a -> ((ConsensusTree) a).setOptionConsensus(ConsensusTree.Consensus.Majority));
		setupAlgorithmMenuItem(controller.getMinSpanningTreeMenuItem(), new MinSpanningTree());
		setupAlgorithmMenuItem(controller.getRerootOrReorderTreesMenuItem(), new RerootOrReorderTrees());

		controller.getViewSingleTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.TreeView))));
		controller.getViewSingleTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ShowTrees()));

		controller.getViewTreePagesMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.TreePages))));
		controller.getViewTreePagesMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ShowTrees()));

		controller.getViewTanglegramMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.Tanglegram))));
		controller.getViewTanglegramMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ShowTrees()));

		controller.getViewDensiTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.DensiTree))));
		controller.getViewDensiTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ShowTrees()));

		setupTreeViewMenuItem(controller.getViewSingleTreeMenuItem(), ShowTrees.ViewType.TreeView);
		setupTreeViewMenuItem(controller.getViewTreePagesMenuItem(), ShowTrees.ViewType.TreePages);
		setupTreeViewMenuItem(controller.getViewTanglegramMenuItem(), ShowTrees.ViewType.Tanglegram);
		setupTreeViewMenuItem(controller.getViewDensiTreeMenuItem(), ShowTrees.ViewType.DensiTree);

		setupAlgorithmMenuItem(controller.getNeighborNetMenuItem(), new NeighborNet());
		setupAlgorithmMenuItem(controller.getSplitDecompositionMenuItem(), new SplitDecomposition());
		setupAlgorithmMenuItem(controller.getParsimonySplitsMenuItem(), new ParsimonySplits());

		setupAlgorithmMenuItem(controller.getConsensusNetworkMenuItem(), new ConsensusNetwork());
		setupAlgorithmMenuItem(controller.getConsensusOutlineMenuItem(), new ConsensusOutline());
		setupAlgorithmMenuItem(controller.getConsensusSplitsMenuItem(), new ConsensusSplits());
		setupAlgorithmMenuItem(controller.getSuperNetworkMenuItem(), new SuperNetwork());
		setupAlgorithmMenuItem(controller.getMedianJoiningMenuItem(), new MedianJoining());

		setupAlgorithmMenuItem(controller.getMinSpanningNetworkMenuItem(), new MinSpanningNetwork());
		setupAlgorithmMenuItem(controller.getHybridizationNetworkMenuItem(), new PhyloFusion());
		setupAlgorithmMenuItem(controller.getClusterNetworkMenuItem(), new ClusterNetwork());

		setupAlgorithmMenuItem(controller.getPcoaMenuItem(), new PCOA());
		//setupAlgorithmMenuItem(controller.getTsneMenuItem(), new TSne());

		controller.getBootStrapTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BootstrapTree()));
		controller.getBootStrapTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BootstrapTree()));

		controller.getBootstrapTreeAsNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BootstrapTreeSplits()));
		controller.getBootstrapTreeAsNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BootstrapTreeSplits()));

		controller.getBootStrapNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BootstrapSplits()));
		controller.getBootStrapNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BootstrapSplits()));

		controller.getComputeDeltaScoreMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new DeltaScore()));
		controller.getComputeDeltaScoreMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new DeltaScore()));

		controller.getPhiTestMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new PhiTest()));
		controller.getPhiTestMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new PhiTest()));

		controller.getTajimaDMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new TajimaD()));
		controller.getTajimaDMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new TajimaD()));

		controller.getEstimateInvariableSitesMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new EstimateInvariableSites()));
		controller.getEstimateInvariableSitesMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new EstimateInvariableSites()));

		controller.getComputeSplitsShapleyValuesMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShapleyValues()));
		controller.getComputeSplitsShapleyValuesMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ShapleyValues()));

		controller.getComputeSplitsPhylogeneticDiversityMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new splitstree6.algorithms.splits.splits2report.PhylogeneticDiversity()));
		controller.getComputeSplitsPhylogeneticDiversityMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new splitstree6.algorithms.splits.splits2report.PhylogeneticDiversity()));

		controller.getComputeUnrootedTreeShapleyMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new UnrootedShapleyValues()));
		controller.getComputeUnrootedTreeShapleyMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new UnrootedShapleyValues()));

		controller.getComputeRootedTreeFairProportionMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new TreeDiversityIndex(), t -> ((TreeDiversityIndex) t).setOptionMethod(TreeDiversityIndex.Method.FairProportions)));
		controller.getComputeRootedTreeFairProportionMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new TreeDiversityIndex()));

		controller.getComputeRootedTreeEqualSplitsMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new TreeDiversityIndex(), t -> ((TreeDiversityIndex) t).setOptionMethod(TreeDiversityIndex.Method.EqualSplits)));
		controller.getComputeRootedTreeEqualSplitsMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new TreeDiversityIndex()));

		controller.getComputeTreePhylogeneticDiversityMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new PhylogeneticDiversity()));
		controller.getComputeTreePhylogeneticDiversityMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new PhylogeneticDiversity()));

		controller.getShowWorkflowMenuItem().setOnAction(e -> controller.getMainTabPane().getSelectionModel().select(mainWindow.getTabByClass(WorkflowTab.class)));

		controller.getShowMessageWindowMenuItem().setOnAction(e -> MessageWindow.getInstance().setVisible(!MessageWindow.getInstance().isVisible()));
		MessageWindow.visibleProperty().addListener((v, o, n) -> controller.getShowMessageWindowMenuItem().setSelected(n));

		controller.getSetWindowSizeMenuItem().setOnAction(e -> {
			var result = SetParameterDialog.apply(stage, "Enter size (width x height)",
					"%.0f x %.0f".formatted(stage.getWidth(), stage.getHeight()));

			if (result != null) {
				var tokens = StringUtils.split(result, 'x');
				if (tokens.length == 2 && NumberUtils.isInteger(tokens[0]) && NumberUtils.isInteger(tokens[1])) {
					var width = Math.max(50, NumberUtils.parseDouble(tokens[0]));
					var height = Math.max(50, NumberUtils.parseDouble(tokens[1]));
					stage.setWidth(width);
					stage.setHeight(height);
				}
			}
		});

		if (controller.getFileMenuButton().getItems().isEmpty()) {
			if (SplitsTree6.isDesktop()) {
				controller.getFileMenuButton().getItems().setAll(BasicFX.copyMenu(List.of(
						controller.getNewMenuItem(), controller.getEditInputMenuItem(), controller.getOpenMenuItem(), new SeparatorMenuItem(),
						controller.getCloseMenuItem(), new SeparatorMenuItem(),
						controller.getSaveAsMenuItem(), new SeparatorMenuItem())));
			} else { // mobile
				controller.getFileMenuButton().getItems().setAll(BasicFX.copyMenu(List.of(
						controller.getEditInputMenuItem(), controller.getOpenMenuItem(), new SeparatorMenuItem(),
						controller.getCloseMenuItem(), new SeparatorMenuItem(),
						controller.getSaveMenuItem(), new SeparatorMenuItem())));
				controller.getFileMenuButton().getItems().get(0).setText("Edit");
			}

			var recentFilesFirstIndex = controller.getFileMenuButton().getItems().size();
			controller.getFileMenuButton().getItems().addAll(BasicFX.copyMenu(controller.getOpenRecentMenu().getItems()));
			controller.getOpenRecentMenu().getItems().addListener((InvalidationListener) e -> {
				controller.getFileMenuButton().getItems().remove(recentFilesFirstIndex, controller.getFileMenuButton().getItems().size());
				controller.getFileMenuButton().getItems().addAll(BasicFX.copyMenu(controller.getOpenRecentMenu().getItems()));
			});
		}

		controller.getAboutMenuItem().setOnAction((e) -> SplashScreen.showSplash(Duration.ofMinutes(1)));

		controller.getCheckForUpdatesMenuItem().setOnAction(e -> CheckForUpdate.apply());
		controller.getCheckForUpdatesMenuItem().disableProperty().bind(mainWindow.emptyProperty().not().or(MainWindowManager.getInstance().sizeProperty().greaterThan(1)));

		controller.getMainTabPane().getSelectionModel().selectedItemProperty().addListener(a -> updateEnableStateAlgorithms());

		workflow.runningProperty().addListener((v, o, n) -> {
			if (!n) {
				updateEnableStateAlgorithms();
				updateFindReplace();
			}
		});

		updateFindReplace();

		if (stage != null)
			BasicFX.setupFullScreenMenuSupport(stage, controller.getUseFullScreenMenuItem());

		if (additionalCommonMenuSetup != null)
			additionalCommonMenuSetup.accept(mainWindow);
	}

	public void showInputEditor() {
		final MainWindow emptyWindow;
		if (mainWindow.isEmpty())
			emptyWindow = mainWindow;
		else
			emptyWindow = (MainWindow) MainWindowManager.getInstance().createAndShowWindow(false);

		final Tab tab;
		if (emptyWindow.getController().getMainTabPane().findTab(InputEditorTab.NAME) != null)
			tab = emptyWindow.getController().getMainTabPane().findTab(InputEditorTab.NAME);
		else {
			tab = new InputEditorTab(emptyWindow);
			//emptyWindow.getController().getMainTabPane().getTabs().add(tab);
		}
		Platform.runLater(() -> mainWindow.getController().getMainTabPane().getSelectionModel().select(tab));
	}

	private void disableAllMenuItems(MainWindowController controller) {
		var stack = new Stack<MenuItem>();
		stack.addAll(controller.getMenuBar().getMenus());
		while (!stack.isEmpty()) {
			var item = stack.pop();
			if (item instanceof Menu menu) {
				if (menu != controller.getOpenRecentMenu() && menu != controller.getWindowMenu() && menu != controller.getHelpMenu())
					stack.addAll(menu.getItems());
			} else if (!(item instanceof SeparatorMenuItem)) {
				item.onActionProperty().unbind();
				item.setOnAction(null);
				item.disableProperty().unbind();
				item.setDisable(true);
			}
		}
	}

	private void enableAllMenuItemsWithDefinedAction(MainWindowController controller) {
		var stack = new Stack<MenuItem>();
		stack.addAll(controller.getMenuBar().getMenus());
		while (!stack.isEmpty()) {
			var item = stack.pop();
			if (item instanceof Menu menu) {
				if (menu != controller.getOpenRecentMenu() && menu != controller.getWindowMenu() && menu != controller.getHelpMenu())
					stack.addAll(menu.getItems());
			} else if (!(item instanceof SeparatorMenuItem)) {
				if (item.getOnAction() != null && !item.disableProperty().isBound()) {
					item.setDisable(false);
				}
			}
		}
	}

	public IDisplayTab getContainingDisplayTab(Node node) {
		while (node != null) {
			if (node instanceof TabPane tabPane) {
				var tab = tabPane.getSelectionModel().getSelectedItem();
				if (tab instanceof IDisplayTab displayTab)
					return displayTab;
			} else if (node instanceof IDisplayTab displayTab)
				return displayTab;
			node = node.getParent();
		}
		return null;
	}

	public SplitPanePresenter getSplitPanePresenter() {
		return splitPanePresenter;
	}

	public void updateUndoRedo() {
		if (selectedDisplayTab.get() != null && selectedDisplayTab.get().getUndoManager() != null) {
			var undoManager = selectedDisplayTab.get().getUndoManager();
			controller.getUndoButton().setOnAction(e -> undoManager.undo());
			controller.getUndoButton().disableProperty().bind(undoManager.undoableProperty().not());

			controller.getUndoMenuItem().textProperty().bind(undoManager.undoNameProperty());
			controller.getUndoMenuItem().onActionProperty().bind(controller.getUndoButton().onActionProperty());
			controller.getUndoMenuItem().disableProperty().bind(controller.getUndoButton().disableProperty());

			controller.getRedoButton().setOnAction(e -> undoManager.redo());
			controller.getRedoButton().disableProperty().bind(undoManager.redoableProperty().not());

			controller.getRedoMenuItem().textProperty().bind(undoManager.redoNameProperty());
			controller.getRedoMenuItem().onActionProperty().bind(controller.getRedoButton().onActionProperty());
			controller.getRedoMenuItem().disableProperty().bind(controller.getRedoButton().disableProperty());
		} else {
			controller.getUndoButton().disableProperty().unbind();
			controller.getUndoButton().setDisable(true);
			controller.getUndoMenuItem().textProperty().unbind();
			controller.getUndoMenuItem().setText("Undo");
			controller.getUndoMenuItem().disableProperty().unbind();
			controller.getUndoMenuItem().setDisable(true);

			controller.getRedoButton().disableProperty().unbind();
			controller.getRedoButton().setDisable(true);
			controller.getRedoMenuItem().textProperty().unbind();
			controller.getRedoMenuItem().setText("Redo");
			controller.getRedoMenuItem().disableProperty().unbind();
			controller.getRedoMenuItem().setDisable(true);
		}

	}

	public void setupSelectButton() {
		controller.getSelectButton().setOnAction(e -> {
			if (selectedDisplayTab.get().getPresenter() instanceof DisplayTextViewPresenter presenter) {
				presenter.processSelectButtonPressed();
			} else if (selectedDisplayTab.get() instanceof WorkflowTab workflowTab) {
				workflowTab.getPresenter().processSelectButtonPressed();
			} else if (mainWindow.getWorkingTaxa() != null) {
				if (mainWindow.getWorkingTaxa().getNtax() > 0) {
					if (mainWindow.getTaxonSelectionModel().size() == mainWindow.getWorkingTaxa().getNtax()) {
						mainWindow.getTaxonSelectionModel().clearSelection();
					} else {
						mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkingTaxa().getTaxa());
					}
				}
			}
		});
		if (selectedDisplayTab.get() != null) {
			controller.getSelectButton().disableProperty().bind(selectedDisplayTab.get().emptyProperty());
		} else {
			controller.getSelectButton().disableProperty().bind(new SimpleBooleanProperty(true));
		}
	}

	public IDisplayTab getSelectedDisplayTab() {
		return selectedDisplayTab.get();
	}

	public ObjectProperty<IDisplayTab> selectedDisplayTabProperty() {
		return selectedDisplayTab;
	}

	private void setupAlgorithmMenuItem(CheckMenuItem menuItem, Algorithm<? extends DataBlock, ? extends DataBlock> algorithm) {
		setupAlgorithmMenuItem(menuItem, algorithm, null);
	}

	private void setupAlgorithmMenuItem(CheckMenuItem menuItem, Algorithm<? extends DataBlock, ? extends DataBlock> algorithm, Consumer<Algorithm> algorithmSetupCallback) {
		menuItem.setOnAction(e -> AttachAlgorithm.apply(mainWindow, algorithm, algorithmSetupCallback));
		nameAlgorithmMenuItemMap.put(algorithm.getName(), new Pair<>(algorithm, menuItem));
	}

	public void updateEnableStateAlgorithms() {
		if (!mainWindow.getWorkflow().isRunning() && mainWindow.getController().getMainTabPane().getSelectionModel().getSelectedItem() instanceof ViewTab tab) {
			var dataNode = tab.getDataNode();
			var selected = new HashSet<String>();
			var applicable = new HashSet<String>();
			while (dataNode != null) {
				var algorithmNode = dataNode.getPreferredParent();
				if (algorithmNode != null) {
					var algorithm = algorithmNode.getAlgorithm();
					selected.add(algorithm.getName());
					dataNode = algorithmNode.getPreferredParent();
					for (var otherEntry : nameAlgorithmMenuItemMap.entrySet()) {
						var otherName = otherEntry.getKey();
						var otherAlgorithm = otherEntry.getValue().getFirst();
						if (dataNode != null && otherAlgorithm.isApplicable(mainWindow.getWorkingTaxa(), dataNode))
							applicable.add(otherName);
					}
				} else
					break;
			}
			for (var entry : nameAlgorithmMenuItemMap.entrySet()) {
				var name = entry.getKey();
				var menuItem = entry.getValue().getSecond();
				menuItem.setSelected(selected.contains(name));
				menuItem.setDisable(!applicable.contains(name));
			}
		}
	}

	private void setupTreeViewMenuItem(CheckMenuItem menuItem, ShowTrees.ViewType viewType) {
		// 		setupAlgorithmMenuItem(controller.getViewSingleTreeMenuItem(), new ShowTrees(), a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.TreeView)));
		menuItem.setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(), a -> ((ShowTrees) a).setOptionView(viewType)));
		menuItem.disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ShowTrees()));
		InvalidationListener listener = e -> {
			if (mainWindow.getController().getMainTabPane().getSelectionModel().getSelectedItem() instanceof ViewTab tab) {
				var dataNode = tab.getDataNode();
				if (dataNode != null) {
					var algorithmNode = dataNode.getPreferredParent();
					menuItem.setSelected(algorithmNode != null && algorithmNode.getAlgorithm() instanceof ShowTrees showTrees && showTrees.getOptionView() == viewType);
				}
			}
		};
		mainWindow.getController().getMainTabPane().getSelectionModel().selectedItemProperty().addListener(listener);
		mainWindow.getWorkflow().runningProperty().addListener(listener);
	}

	private static void setupReplaceDataMenuItem(MainWindow mainWindow, MenuItem replaceDataMenuItem) {
		replaceDataMenuItem.setOnAction(e -> {
			final Workflow workflow = mainWindow.getWorkflow();
			if (mainWindow.isDirty() && !AskToDiscardChanges.apply(mainWindow.getStage(), "Overwrite"))
				return;

			var importManager = ImportManager.getInstance();

			var readers = importManager.getReaders(workflow.getInputDataNode().getDataBlock().getClass());
			var extensionsFilter = ImportManager.mergeExtensionFilters(readers.stream().map(ReaderWriterBase::getExtensionFilter).collect(Collectors.toList()));

			var previousDir = new File(jloda.fx.util.ProgramProperties.get("InputDir", ""));
			var fileChooser = new FileChooser();
			if (previousDir.isDirectory())
				fileChooser.setInitialDirectory(previousDir);
			fileChooser.setTitle("Load data file");
			fileChooser.getExtensionFilters().add(extensionsFilter);
			fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All (*.*)", "*.*"));

			final var selectedFile = fileChooser.showOpenDialog(mainWindow.getStage());
			if (selectedFile != null) {
				if (importManager.determineInputType(selectedFile.getPath()) != workflow.getInputDataNode().getDataBlock().getClass()) {
					NotificationManager.showError("Can't replace data, selected file must contain data of type: " + workflow.getInputDataNode().getDataBlock().getClass());
					return;
				}
				var inputFormat = ImportManager.getInstance().getFileFormat(selectedFile.getPath());

				try {
					jloda.fx.util.ProgramProperties.put("InputDir", selectedFile.getParent());
					mainWindow.setFileName(FileUtils.replaceFileSuffix(mainWindow.getFileName(), ".stree6"));
					WorkflowDataLoader.load(workflow, selectedFile.getPath(), inputFormat);
					workflow.getInputTaxaFilterNode().restart();
				} catch (Exception ex) {
					NotificationManager.showError("Load data failed: " + ex.getMessage());
				}
			}
		});

		replaceDataMenuItem.setDisable(mainWindow.getWorkflow().isRunning() || mainWindow.getWorkflow().getWorkingTaxaBlock() == null);
	}

	private void updateFindReplace() {
		if (controller.getFindMenuItem().getUserData() instanceof BooleanProperty booleanProperty) {
			controller.getFindMenuItem().selectedProperty().unbindBidirectional(booleanProperty);
			if (!MenusToLaTeX.active)
				controller.getFindMenuItem().setUserData(null);
		}
		controller.getFindMenuItem().setDisable(true);
		if (controller.getReplaceMenuItem().getUserData() instanceof BooleanProperty booleanProperty) {
			controller.getReplaceMenuItem().selectedProperty().unbindBidirectional(booleanProperty);
			if (!MenusToLaTeX.active)
				controller.getReplaceMenuItem().setUserData(null);
		}
		controller.getReplaceMenuItem().setDisable(true);

		if (selectedDisplayTab.get() != null && selectedDisplayTab.get().getPresenter() != null) {
			var findToolBar = selectedDisplayTab.get().getPresenter().getFindToolBar();
			if (findToolBar != null) {
				{
					var booleanProperty = findToolBar.showFindToolBarProperty();
					controller.getFindMenuItem().selectedProperty().bindBidirectional(booleanProperty);
					if (!MenusToLaTeX.active)
						controller.getFindMenuItem().setUserData(booleanProperty);
					controller.getFindMenuItem().setDisable(false);
				}
				{
					if (selectedDisplayTab.get().getPresenter().allowFindReplace()) {
						var booleanProperty = findToolBar.showReplaceToolBarProperty();
						controller.getReplaceMenuItem().selectedProperty().bindBidirectional(booleanProperty);
						if (!MenusToLaTeX.active)
							controller.getReplaceMenuItem().setUserData(booleanProperty);
						controller.getReplaceMenuItem().setDisable(false);
					}
				}
				{
					controller.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
					controller.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
				}
			} else {
				controller.getFindMenuItem().setSelected(false);
				controller.getFindMenuItem().setDisable(true);
				controller.getReplaceMenuItem().setSelected(false);
				controller.getReplaceMenuItem().setDisable(true);
				controller.getFindAgainMenuItem().setDisable(true);
			}
		}
	}

	public static void applyToAllMenuItems(MenuBar menuBar, Function<Menu, Boolean> accept, Consumer<MenuItem> callback) {
		var queue = new LinkedList<>(menuBar.getMenus());
		while (!queue.isEmpty()) {
			var menu = queue.pop();
			if (accept.apply(menu)) {
				for (var item : menu.getItems()) {
					if (!(item instanceof SeparatorMenuItem)) {
						callback.accept(item);
					}
					if (item instanceof Menu other) {
						queue.add(other);
					}
				}
			}
		}
	}
}
