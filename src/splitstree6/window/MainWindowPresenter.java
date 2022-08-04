/*
 * MainWindowPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.window;


import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import jloda.fx.dialog.SetParameterDialog;
import jloda.fx.message.MessageWindow;
import jloda.fx.util.BasicFX;
import jloda.fx.util.Print;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.fx.window.PresentationMode;
import jloda.fx.window.SplashScreen;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.Basic;
import jloda.util.NumberUtils;
import jloda.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.algorithms.characters.characters2distances.GeneContentDistance;
import splitstree6.algorithms.characters.characters2distances.LogDet;
import splitstree6.algorithms.characters.characters2distances.ProteinMLdist;
import splitstree6.algorithms.characters.characters2distances.Uncorrected_P;
import splitstree6.algorithms.characters.characters2distances.nucleotide.*;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.characters.characters2splits.ParsimonySplits;
import splitstree6.algorithms.distances.distances2network.MinSpanningNetwork;
import splitstree6.algorithms.distances.distances2network.PCoA;
import splitstree6.algorithms.distances.distances2network.TSne;
import splitstree6.algorithms.distances.distances2splits.BunemanTree;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.distances.distances2splits.SplitDecomposition;
import splitstree6.algorithms.distances.distances2trees.BioNJ;
import splitstree6.algorithms.distances.distances2trees.MinSpanningTree;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.algorithms.splits.splits2splits.BootstrapSplits;
import splitstree6.algorithms.splits.splits2splits.SplitsFilter;
import splitstree6.algorithms.splits.splits2splits.WeightsSlider;
import splitstree6.algorithms.splits.splits2view.ShowSplits;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.algorithms.trees.trees2splits.BootstrapTreeSplits;
import splitstree6.algorithms.trees.trees2splits.ConsensusNetwork;
import splitstree6.algorithms.trees.trees2splits.ConsensusTreeSplits;
import splitstree6.algorithms.trees.trees2splits.FilteredSuperNetwork;
import splitstree6.algorithms.trees.trees2trees.*;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.data.CharactersBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.dialog.SaveBeforeClosingDialog;
import splitstree6.dialog.SaveDialog;
import splitstree6.dialog.exporting.ExportTaxonDisplayLabels;
import splitstree6.dialog.exporting.ExportTaxonTraits;
import splitstree6.dialog.importing.ImportMultipleTrees;
import splitstree6.dialog.importing.ImportTaxonDisplayLabels;
import splitstree6.dialog.importing.ImportTaxonTraits;
import splitstree6.io.FileLoader;
import splitstree6.io.readers.ImportManager;
import splitstree6.main.CheckForUpdate;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.view.alignment.AlignmentView;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

public class MainWindowPresenter {
	private final MainWindow mainWindow;
	private final MainWindowController controller;
	private final ObjectProperty<IDisplayTab> focusedDisplayTab = new SimpleObjectProperty<>();

	private final ObservableMap<WorkflowNode, Tab> workFlowTabs = FXCollections.observableHashMap();

	private final SplitPanePresenter splitPanePresenter;

	private final EventHandler<KeyEvent> keyEventEventHandler;

	public MainWindowPresenter(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		controller = mainWindow.getController();

		var workflowTreeView = mainWindow.getWorkflowTreeView();

		controller.getTreeViewAnchorPane().getChildren().add(workflowTreeView);
		AnchorPane.setRightAnchor(workflowTreeView, 0.0);
		AnchorPane.setLeftAnchor(workflowTreeView, 0.0);
		AnchorPane.setTopAnchor(workflowTreeView, 0.0);
		AnchorPane.setBottomAnchor(workflowTreeView, 0.0);

		keyEventEventHandler = e -> {
			var ch = e.getCharacter();
			if ((ch.equals("+") || ch.equals("=") && e.isShiftDown()) && e.isShortcutDown()
				&& controller.getIncreaseFontSizeMenuItem().getOnAction() != null && !controller.getIncreaseFontSizeMenuItem().isDisable()) {
				controller.getIncreaseFontSizeMenuItem().getOnAction().handle(null);
				e.consume();
			} else if (ch.equals("-") && !e.isShiftDown() && e.isShortcutDown() && controller.getDecreaseFontSizeMenuItem().getOnAction() != null && !controller.getDecreaseFontSizeMenuItem().isDisable()) {
				controller.getDecreaseFontSizeMenuItem().getOnAction().handle(null);
				e.consume();
			}
		};

		mainWindow.getStage().getScene().focusOwnerProperty().addListener((v, o, n) -> {
			try {
				if (o != null)
					o.removeEventHandler(KeyEvent.KEY_TYPED, keyEventEventHandler);
				var displayTab = getContainingDisplayTab(n);
				if (displayTab != null) {
					n.addEventHandler(KeyEvent.KEY_TYPED, keyEventEventHandler);

					focusedDisplayTab.set(displayTab);
					disableAllMenuItems(controller);
					setupCommonMenuItems(mainWindow, controller, focusedDisplayTab);
					if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getPresenter() != null)
						focusedDisplayTab.get().getPresenter().setupMenuItems();
					enableAllMenuItemsWithDefinedAction(controller);
				}
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});

		mainWindow.getStage().focusedProperty().addListener((v, o, n) -> {
			if (!n && mainWindow.getTaxonSelectionModel().getSelectedItems().size() > 0) {
				MainWindowManager.getPreviousSelection().clear();
				mainWindow.getTaxonSelectionModel().getSelectedItems().stream().map(Taxon::getName).filter(s -> !s.isBlank()).forEach(s -> MainWindowManager.getPreviousSelection().add(s));
			}
		});

		controller.getMainTabPane().getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			try {
				disableAllMenuItems(controller);
				if (n instanceof IDisplayTab displayTab) {
					focusedDisplayTab.set(displayTab);
				} else
					focusedDisplayTab.set(null);
				setupCommonMenuItems(mainWindow, controller, focusedDisplayTab);
				if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getPresenter() != null) {
					focusedDisplayTab.get().getPresenter().setupMenuItems();
				}
				enableAllMenuItemsWithDefinedAction(controller);
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});

		controller.getAlgorithmTabPane().getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			try {
				disableAllMenuItems(controller);
				setupCommonMenuItems(mainWindow, controller, focusedDisplayTab);
				if (n instanceof IDisplayTab displayTab) {
					displayTab.getPresenter().setupMenuItems();
					focusedDisplayTab.set(displayTab);
				} else
					focusedDisplayTab.set(null);
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

		BasicFX.applyToAllMenus(controller.getMenuBar(),
				m -> !List.of("File", "Edit", "Import", "Window", "Open Recent", "Help").contains(m.getText()),
				m -> m.disableProperty().bind(mainWindow.getWorkflow().runningProperty().or(mainWindow.emptyProperty())));
		BasicFX.applyToAllMenus(controller.getMenuBar(),
				m -> m.getText().equals("Edit"),
				m -> m.disableProperty().bind(mainWindow.getWorkflow().runningProperty()));
	}

	private void setupCommonMenuItems(MainWindow mainWindow, MainWindowController controller, ObjectProperty<IDisplayTab> focusedDisplayTab) {
		var workflow = mainWindow.getWorkflow();

		controller.getNewMenuItem().setOnAction(e -> MainWindowManager.getInstance().createAndShowWindow(false));

		var loadingFile = new SimpleBooleanProperty(this, "loadingFile", false);

		controller.getOpenButton().setOnAction(e -> {
			var previousDir = new File(ProgramProperties.get("InputDir", ""));
			var fileChooser = new FileChooser();
			if (previousDir.isDirectory())
				fileChooser.setInitialDirectory(previousDir);
			fileChooser.setTitle("Open input file");
			fileChooser.getExtensionFilters().addAll(ImportManager.getInstance().getExtensionFilters());
			var selectedFile = fileChooser.showOpenDialog(mainWindow.getStage());
			if (selectedFile != null) {
				if (!loadingFile.get()) {
					try {
						loadingFile.set(true);
						FileLoader.apply(false, mainWindow, selectedFile.getPath(), ex -> NotificationManager.showError("Open file failed: " + ex));
					} finally {
						loadingFile.set(false);
					}
				}
			}
		});
		controller.getOpenButton().disableProperty().bind(workflow.runningProperty().or(loadingFile));

		controller.getOpenMenuItem().setOnAction(controller.getOpenButton().getOnAction());
		controller.getOpenMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getImportTaxonDisplayMenuItem().setOnAction(e -> ImportTaxonDisplayLabels.apply(mainWindow));
		controller.getImportTaxonDisplayMenuItem().disableProperty().bind(workflow.runningProperty().or(mainWindow.emptyProperty()));

		controller.getImportTaxonTraitsMenuItem().setOnAction(e -> ImportTaxonTraits.apply(mainWindow));
		controller.getImportTaxonTraitsMenuItem().disableProperty().bind(workflow.runningProperty().or(mainWindow.emptyProperty()));

		controller.getImportMultipleTreeFilesMenuItem().setOnAction(e -> ImportMultipleTrees.apply(mainWindow));
		controller.getImportMultipleTreeFilesMenuItem().disableProperty().bind(mainWindow.emptyProperty().not());

		controller.getReplaceDataMenuItem().setOnAction(e -> System.err.println("Not implemented"));
		controller.getReplaceDataMenuItem().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()));

		controller.getInputEditorMenuItem().setOnAction(e -> showInputEditor());
		controller.getInputEditorMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getAnalyzeGenomesMenuItem().setOnAction(e -> {
		});
		controller.getAnalyzeGenomesMenuItem().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()));

		controller.getSaveButton().setOnAction(e -> {
			if (mainWindow.isHasSplitsTree6File())
				SaveDialog.save(mainWindow, false, new File(mainWindow.getFileName()));
			else
				controller.getSaveAsMenuItem().getOnAction().handle(e);
		});
		controller.getSaveButton().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()).or(mainWindow.dirtyProperty().not()));
		controller.getSaveMenuItem().setOnAction(controller.getSaveButton().getOnAction());
		controller.getSaveMenuItem().disableProperty().bind(controller.getSaveButton().disableProperty());

		controller.getSaveAsMenuItem().setOnAction(e -> SaveDialog.showSaveDialog(mainWindow, false));
		controller.getSaveAsMenuItem().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()));

		controller.getExportTaxonDisplayLabelsMenuItem().setOnAction(e -> ExportTaxonDisplayLabels.apply(mainWindow));
		controller.getExportTaxonDisplayLabelsMenuItem().disableProperty().bind(workflow.runningProperty().or(Bindings.createBooleanBinding(() -> workflow.getWorkingTaxaBlock() == null, workflow.validProperty())));

		controller.getExportTaxonTraitsMenuItem().setOnAction(e -> ExportTaxonTraits.apply(mainWindow));
		controller.getExportTaxonTraitsMenuItem().disableProperty().bind(workflow.runningProperty().or(Bindings.createBooleanBinding(() -> workflow.getWorkingTaxaBlock() == null || workflow.getWorkingTaxaBlock().getTraitsBlock() == null, workflow.validProperty())));

		controller.getExportWorkflowMenuItem().setOnAction(e -> SaveDialog.showSaveDialog(mainWindow, true));
		controller.getExportWorkflowMenuItem().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()));

		controller.getPageSetupMenuItem().setOnAction(e -> Print.showPageLayout(mainWindow.getStage()));
		controller.getPageSetupMenuItem().disableProperty().bind(workflow.runningProperty());

		if (focusedDisplayTab.get() != null) {
			controller.getPrintButton().setOnAction(e -> Print.print(mainWindow.getStage(), focusedDisplayTab.get().getImageNode()));
			controller.getPrintButton().disableProperty().bind(mainWindow.emptyProperty().or(workflow.runningProperty()).or(focusedDisplayTab.isNull()));
		}
		controller.getPrintMenuItem().setOnAction(controller.getPrintButton().getOnAction());
		controller.getPrintMenuItem().disableProperty().bind(controller.getPrintButton().disableProperty());

		controller.getGroupIdenticalHaplotypesFilesMenuItem().setOnAction(null);

		controller.getQuitMenuItem().setOnAction(e -> {
			while (MainWindowManager.getInstance().size() > 0) {
				final MainWindow aWindow = (MainWindow) MainWindowManager.getInstance().getMainWindow(MainWindowManager.getInstance().size() - 1);
				if (SaveBeforeClosingDialog.apply(aWindow) == SaveBeforeClosingDialog.Result.cancel || !MainWindowManager.getInstance().closeMainWindow(aWindow))
					break;
			}
		});

		mainWindow.getStage().setOnCloseRequest(e -> {
			controller.getCloseMenuItem().getOnAction().handle(null);
			e.consume();
		});

		controller.getCloseMenuItem().setOnAction(e -> {
			if (SaveBeforeClosingDialog.apply(mainWindow) != SaveBeforeClosingDialog.Result.cancel) {
				if (MainWindowManager.getInstance().closeMainWindow(mainWindow))
					mainWindow.getWorkflow().cancel();
			}
		});

		updateUndoRedo();

		controller.getCutMenuItem().setDisable(false);
		controller.getCopyMenuItem().setDisable(false);

		if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getImageNode() != null) {
			controller.getCopyImageMenuItem().setOnAction(e -> {
				final Image snapshot = focusedDisplayTab.get().getImageNode().snapshot(null, null);
				final ClipboardContent clipboardContent = new ClipboardContent();
				clipboardContent.putImage(snapshot);
				Clipboard.getSystemClipboard().setContent(clipboardContent);
			});
			controller.getCopyImageMenuItem().disableProperty().bind(focusedDisplayTab.isNull());
		}

		controller.getCopyNewickMenuItem().setDisable(true);

		controller.getPasteMenuItem().setDisable(true);

		// controller.getDuplicateMenuItem().setOnAction(null);
		// controller.getDeleteMenuItem().setOnAction(null);

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		controller.getReplaceMenuItem().setOnAction(null);

		// controller.getGotoLineMenuItem().setOnAction(null);

		controller.getPreferencesMenuItem().setOnAction(null);

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

		PresentationMode.setupPresentationModeMenuItem(mainWindow, controller.getPresentationModeMenuItem());

		BasicFX.setupFullScreenMenuSupport(mainWindow.getStage(), controller.getUseFullScreenMenuItem());
		controller.getPresentationModeMenuItem().setDisable(false);

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

		controller.getFilterSplitsMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new SplitsFilter()));
		controller.getFilterSplitsMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new SplitsFilter()));

		controller.getSplitsSliderMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new WeightsSlider()));
		controller.getSplitsSliderMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new WeightsSlider()));

		controller.getTraitsMenuItem().setOnAction(null);

		controller.getUncorrectedPMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new Uncorrected_P()));
		controller.getUncorrectedPMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new Uncorrected_P()));

		controller.getLogDetMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new LogDet()));
		controller.getLogDetMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new LogDet()));

		controller.getHky85MenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new HKY85()));
		controller.getHky85MenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new HKY85()));

		controller.getJukesCantorMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new JukesCantor()));
		controller.getJukesCantorMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new JukesCantor()));

		controller.getK2pMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new K2P()));
		controller.getK2pMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new K2P()));

		controller.getK3stMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new K3ST()));
		controller.getK3stMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new K3ST()));

		controller.getF81MenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new F81()));
		controller.getF81MenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new F81()));

		controller.getF84MenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new F84()));
		controller.getF84MenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new F84()));

		controller.getProteinMLDistanceMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ProteinMLdist()));
		controller.getProteinMLDistanceMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ProteinMLdist()));

		controller.getGeneContentDistanceMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new GeneContentDistance()));
		controller.getGeneContentDistanceMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new GeneContentDistance()));

		controller.getNjMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new NeighborJoining()));
		controller.getNjMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new NeighborJoining()));

		controller.getBioNJMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BioNJ()));
		controller.getBioNJMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BioNJ()));

		controller.getUpgmaMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new UPGMA()));
		controller.getUpgmaMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new UPGMA()));

		controller.getBunemanTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BunemanTree()));
		controller.getBunemanTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BunemanTree()));

		controller.getSelectTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new TreeSelector(), a -> ((TreeSelector) a).setOptionWhich(1)));
		controller.getSelectTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new TreeSelector()));

		controller.getConsensusTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ConsensusTree(), a -> ((ConsensusTree) a).setOptionConsensus(ConsensusTreeSplits.Consensus.Majority)));
		controller.getConsensusTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ConsensusTree()));

		controller.getMinSpanningTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new MinSpanningTree()));
		controller.getMinSpanningTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new MinSpanningTree()));

		controller.getRerootOrReorderTreesMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new RerootOrReorderTrees()));
		controller.getRerootOrReorderTreesMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new RerootOrReorderTrees()));

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

		controller.getNeighborNetMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new NeighborNet()));
		controller.getNeighborNetMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new NeighborNet()));

		controller.getSplitDecompositionMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new SplitDecomposition()));
		controller.getSplitDecompositionMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new SplitDecomposition()));

		controller.getParsimonySplitsMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ParsimonySplits()));
		controller.getParsimonySplitsMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ParsimonySplits()));

		controller.getConsensusNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ConsensusNetwork()));
		controller.getConsensusNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ConsensusNetwork()));

		controller.getFilteredSuperNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new FilteredSuperNetwork()));
		controller.getFilteredSuperNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new FilteredSuperNetwork()));

		controller.getMedianJoiningMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new MedianJoining()));
		controller.getMedianJoiningMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new MedianJoining()));

		controller.getMinSpanningNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new MinSpanningNetwork()));
		controller.getMinSpanningNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new MinSpanningNetwork()));

		controller.getHybridizationNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new AutumnAlgorithm(), null, a -> ((ShowTrees) a).setOptionView(ShowTrees.ViewType.TreePages)));
		controller.getHybridizationNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new AutumnAlgorithm()));

		controller.getSplitsNetworkViewMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowSplits(), a -> ((ShowSplits) a).setOptionView(ShowSplits.ViewType.SplitsNetwork)));
		controller.getSplitsNetworkViewMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new ShowSplits()));

		controller.getHaplotypeNetworkViewMenuItem().setOnAction(null);

		controller.getPcoaMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new PCoA()));
		controller.getPcoaMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new PCoA()));

		controller.getTsneMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new TSne()));
		controller.getTsneMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new TSne()));

		controller.getBootStrapTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BootstrapTree()));
		controller.getBootStrapTreeMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BootstrapTree(), () -> workflow.getWorkingDataBlock() instanceof CharactersBlock));

		controller.getBootstrapTreeAsNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BootstrapTreeSplits()));
		controller.getBootstrapTreeAsNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BootstrapTreeSplits(), () -> workflow.getWorkingDataBlock() instanceof CharactersBlock));

		controller.getBootStrapNetworkMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new BootstrapSplits()));
		controller.getBootStrapNetworkMenuItem().disableProperty().bind(AttachAlgorithm.createDisableProperty(mainWindow, new BootstrapSplits(), () -> workflow.getWorkingDataBlock() instanceof CharactersBlock));

		controller.getEstimateInvariableSitesMenuItem().setOnAction(null);
		controller.getComputePhylogeneticDiversityMenuItem().setOnAction(null);
		controller.getComputeDeltaScoreMenuItem().setOnAction(null);

		controller.getShowWorkflowMenuItem().setOnAction(e -> controller.getMainTabPane().getSelectionModel().select(mainWindow.getTabByClass(WorkflowTab.class)));

		controller.getShowMessageWindowMenuItem().setOnAction(e -> MessageWindow.getInstance().setVisible(true));

		controller.getSetWindowSizeMenuItem().setOnAction(e -> {
			var result = SetParameterDialog.apply(mainWindow.getStage(), "Enter size (width x height)",
					"%.0f x %.0f".formatted(mainWindow.getStage().getWidth(), mainWindow.getStage().getHeight()));

			if (result != null) {
				var tokens = StringUtils.split(result, 'x');
				if (tokens.length == 2 && NumberUtils.isInteger(tokens[0]) && NumberUtils.isInteger(tokens[1])) {
					var width = Math.max(50, NumberUtils.parseDouble(tokens[0]));
					var height = Math.max(50, NumberUtils.parseDouble(tokens[1]));
					mainWindow.getStage().setWidth(width);
					mainWindow.getStage().setHeight(height);
				}
			}
		});

		controller.getAboutMenuItem().setOnAction((e) -> SplashScreen.showSplash(Duration.ofMinutes(1)));

		controller.getCheckForUpdatesMenuItem().setOnAction(e -> CheckForUpdate.apply());
		controller.getCheckForUpdatesMenuItem().disableProperty().bind(mainWindow.emptyProperty().not().or(MainWindowManager.getInstance().sizeProperty().greaterThan(1)));
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
		while (stack.size() > 0) {
			var item = stack.pop();
			if (item instanceof Menu menu) {
				if (menu != controller.getOpenRecentMenu() && menu != controller.getWindowMenu() && menu != controller.getHelpMenu())
					stack.addAll(menu.getItems());
			} else if (!(item instanceof SeparatorMenuItem)) {
				item.setOnAction(null);
				item.disableProperty().unbind();
				item.setDisable(true);
			}
		}
	}

	private void enableAllMenuItemsWithDefinedAction(MainWindowController controller) {
		var stack = new Stack<MenuItem>();
		stack.addAll(controller.getMenuBar().getMenus());
		while (stack.size() > 0) {
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
		if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getUndoManager() != null) {
			var undoManager = focusedDisplayTab.get().getUndoManager();
			controller.getUndoMenuItem().textProperty().bind(undoManager.undoNameProperty());
			controller.getUndoMenuItem().setOnAction(e -> undoManager.undo());
			controller.getUndoMenuItem().disableProperty().bind(undoManager.undoableProperty().not());
			controller.getRedoMenuItem().textProperty().bind(undoManager.redoNameProperty());
			controller.getRedoMenuItem().setOnAction(e -> undoManager.redo());
			controller.getRedoMenuItem().disableProperty().bind(undoManager.redoableProperty().not());
		} else {
			controller.getUndoMenuItem().textProperty().unbind();
			controller.getUndoMenuItem().setText("Undo");
			controller.getUndoMenuItem().disableProperty().unbind();
			controller.getUndoMenuItem().setDisable(true);

			controller.getRedoMenuItem().textProperty().unbind();
			controller.getRedoMenuItem().setText("Redo");
			controller.getRedoMenuItem().disableProperty().unbind();
			controller.getRedoMenuItem().setDisable(true);
		}
	}
}
