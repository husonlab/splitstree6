/*
 *  MainWindowPresenter.java Copyright (C) 2021 Daniel H. Huson
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
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
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
import jloda.fx.util.BasicFX;
import jloda.fx.util.Print;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.fx.window.WindowGeometry;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.Basic;
import jloda.util.ProgramProperties;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaEditor;
import splitstree6.algorithms.trees.trees2splits.ConsensusTreeSplits;
import splitstree6.algorithms.trees.trees2trees.ConsensusTree;
import splitstree6.algorithms.trees.trees2trees.RerootOrLadderizeTrees;
import splitstree6.algorithms.trees.trees2trees.TreeSelector;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.dialog.SaveBeforeClosingDialog;
import splitstree6.dialog.SaveDialog;
import splitstree6.io.FileLoader;
import splitstree6.io.readers.ImportManager;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.tabs.workflow.WorkflowTab;

import java.io.File;
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

		RecentFilesManager.getInstance().setFileOpener(fileName -> {
			FileLoader.apply(false, mainWindow, fileName, ex -> NotificationManager.showError("Open recent file failed: " + ex));
		});

		RecentFilesManager.getInstance().setupMenu(controller.getOpenRecentMenu());

		splitPanePresenter = new SplitPanePresenter(mainWindow.getController());
	}

	private void setupCommonMenuItems(MainWindow mainWindow, MainWindowController controller, ObjectProperty<IDisplayTab> focusedDisplayTab) {
		var workflow = mainWindow.getWorkflow();

		controller.getNewMenuItem().setOnAction(e -> MainWindowManager.getInstance().createAndShowWindow(false));

		controller.getOpenMenuItem().setOnAction(e -> {
			final File previousDir = new File(ProgramProperties.get("InputDir", ""));
			final FileChooser fileChooser = new FileChooser();
			if (previousDir.isDirectory())
				fileChooser.setInitialDirectory(previousDir);
			fileChooser.setTitle("Open input file");
			fileChooser.getExtensionFilters().addAll(ImportManager.getInstance().getExtensionFilters());
			final File selectedFile = fileChooser.showOpenDialog(mainWindow.getStage());
			if (selectedFile != null) {
				FileLoader.apply(false, mainWindow, selectedFile.getPath(), ex -> NotificationManager.showError("Open file failed: " + ex));
			}
		});

		controller.getImportMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
		});

		controller.getReplaceDataMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
		});

		controller.getInputEditorMenuItem().setOnAction(e -> showInputEditor());

		controller.getAnalyzeGenomesMenuItem().setOnAction(e -> {
		});

		controller.getSaveMenuItem().setOnAction(e -> {
			SaveDialog.save(mainWindow, false, new File(mainWindow.getFileName()));
		});
		controller.getSaveMenuItem().disableProperty().bind((mainWindow.dirtyProperty().and(mainWindow.fileNameProperty().isNotEmpty()).and(mainWindow.hasSplitsTree6FileProperty())).not());


		controller.getSaveAsMenuItem().setOnAction(e -> {
			SaveDialog.showSaveDialog(mainWindow, false);
		});
		controller.getSaveAsMenuItem().disableProperty().bind(mainWindow.emptyProperty());

		controller.getExportMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
		});
		controller.getExportMenuItem().disableProperty().bind(mainWindow.emptyProperty());

		controller.getExportWorkflowMenuItem().setOnAction(e -> {
			SaveDialog.showSaveDialog(mainWindow, true);
		});
		controller.getSaveAsMenuItem().disableProperty().bind(mainWindow.emptyProperty());


		controller.getPageSetupMenuItem().setOnAction(e -> Print.showPageLayout(mainWindow.getStage()));

		if (focusedDisplayTab.get() != null) {
			controller.getPrintMenuItem().setOnAction(e -> Print.print(mainWindow.getStage(), focusedDisplayTab.get().getImageNode()));
			controller.getPrintMenuItem().disableProperty().bind(focusedDisplayTab.isNull());
		}

		controller.getImportMultipleTreeFilesMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
		});
		controller.getImportMultipleTreeFilesMenuItem().disableProperty().bind(mainWindow.emptyProperty().not());

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
				ProgramProperties.put("WindowGeometry", (new WindowGeometry(mainWindow.getStage())).toString());
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

		controller.getPasteMenuItem().setDisable(false);

		// controller.getDuplicateMenuItem().setOnAction(null);
		// controller.getDeleteMenuItem().setOnAction(null);

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		controller.getReplaceMenuItem().setOnAction(null);

		// controller.getGotoLineMenuItem().setOnAction(null);

		controller.getPreferencesMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(null);
		controller.getSelectNoneMenuItem().setOnAction(null);

		/*
		controller.getSelectAllNodesMenuItem().setOnAction(null);
		controller.getSelectAllLabeledNodesMenuItem().setOnAction(null);
		controller.getSelectAllBelowMenuItem().setOnAction(null);
		controller.getSelectBracketsMenuItem().setOnAction(null);
		controller.getInvertNodeSelectionMenuItem().setOnAction(null);
		controller.getDeselectAllNodesMenuItem().setOnAction(null);
		controller.getSelectAllEdgesMenuItem().setOnAction(null);
		controller.getSelectAllLabeledEdgesMenuItem().setOnAction(null);
		controller.getSelectAllEdgesBelowMenuItem().setOnAction(null);
		controller.getInvertEdgeSelectionMenuItem().setOnAction(null);
		controller.getDeselectEdgesMenuItem().setOnAction(null);
		controller.getSelectFromPreviousMenuItem().setOnAction(null);
		 */

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

		BasicFX.setupFullScreenMenuSupport(mainWindow.getStage(), controller.getUseFullScreenMenuItem());

		controller.getEditTaxaMenuItem().setOnAction(e -> {
			var nodes = workflow.getNodes(TaxaEditor.class);
			if (nodes.size() == 1)
				mainWindow.getAlgorithmTabsManager().showTab(nodes.iterator().next(), true);
		});
		controller.getEditTaxaMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> workflow.getNodes(TaxaEditor.class).size() != 1, workflow.nodes()));

		controller.getEditCharactersMenuItem().setOnAction(null);
		controller.getEditTreesMenuItem().setOnAction(null);
		controller.getEditSplitsMenuItem().setOnAction(null);

		controller.getTraitsMenuItem().setOnAction(null);

		controller.getUncorrectedPMenuItem().setOnAction(null);

		controller.getLogDetMenuItem().setOnAction(null);

		controller.getHky85MenuItem().setOnAction(null);
		controller.getJukesCantorMenuItem().setOnAction(null);
		controller.getK2pMenuItem().setOnAction(null);
		controller.getK3stMenuItem().setOnAction(null);
		controller.getF81MenuItem().setOnAction(null);
		controller.getF84MenuItem().setOnAction(null);
		controller.getProteinMLDistanceMenuItem().setOnAction(null);
		controller.getGeneContentDistanceMenuItem().setOnAction(null);
		controller.getNjMenuItem().setOnAction(null);
		controller.getBioNJMenuItem().setOnAction(null);
		controller.getUpgmaMenuItem().setOnAction(null);
		controller.getBunemanTreeMenuItem().setOnAction(null);

		controller.getSelectTreeMenuItem().setOnAction(e -> InsertAlgorithm.apply(mainWindow, new TreeSelector(), a -> ((TreeSelector) a).setOptionWhich(1)));
		controller.getConsensusTreeMenuItem().setOnAction(e -> InsertAlgorithm.apply(mainWindow, new ConsensusTree(), a -> ((ConsensusTree) a).setOptionConsensus(ConsensusTreeSplits.Consensus.Majority)));

		controller.getRerootTreesMenuItem().setOnAction(e -> InsertAlgorithm.apply(mainWindow, new RerootOrLadderizeTrees(), null));

		controller.getViewSingleTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.SingleTree))));
		controller.getViewSingleTreeMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getViewTreePagesMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.TreePages))));
		controller.getViewTreePagesMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getViewTanglegramMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.Tanglegram))));
		controller.getViewTanglegramMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getViewDensiTreeMenuItem().setOnAction(e -> AttachAlgorithm.apply(mainWindow, new ShowTrees(),
				a -> ((ShowTrees) a).setOptionView((ShowTrees.ViewType.DensiTree))));
		controller.getViewDensiTreeMenuItem().disableProperty().bind(workflow.runningProperty());

		controller.getNeighborNetMenuItem().setOnAction(null);
		controller.getSplitDecompositionMenuItem().setOnAction(null);
		controller.getParsimonySplitsMenuItem().setOnAction(null);
		controller.getConsensusNetworkMenuItem().setOnAction(null);
		controller.getFilteredSuperNetworkMenuItem().setOnAction(null);
		controller.getMedianNetworkMenuItem().setOnAction(null);
		controller.getMedianJoiningMenuItem().setOnAction(null);
		controller.getMinSpanningNetworkMenuItem().setOnAction(null);
		controller.getConsensusClusterNetworkMenuItem().setOnAction(null);
		controller.getHybridizationNetworkMenuItem().setOnAction(null);
		controller.getSplitsNetworkViewMenuItem().setOnAction(null);
		controller.getHaplotypeNetworkViewMenuItem().setOnAction(null);
		controller.getShow3DViewerMenuItem().setOnAction(null);
		controller.getRelaxMenuItem().setOnAction(null);

		controller.getPcoaMenuItem().setOnAction(null);
		controller.getBrayCurtisMenuItem().setOnAction(null);
		controller.getJsdMenuItem().setOnAction(null);
		controller.getBootstrappingMenuItem().setOnAction(null);

		controller.getShowBootStrapTreeMenuItem().setOnAction(null);
		controller.getShowBootStrapNetworkMenuItem().setOnAction(null);

		controller.getEstimateInvariableSitesMenuItem().setOnAction(null);
		controller.getComputePhylogeneticDiversityMenuItem().setOnAction(null);
		controller.getComputeDeltaScoreMenuItem().setOnAction(null);

		controller.getShowWorkflowMenuItem().setOnAction(e -> controller.getMainTabPane().getSelectionModel().select(mainWindow.getTabByClass(WorkflowTab.class)));

		controller.getShowMessageWindowMenuItem().setOnAction(null);
		controller.getCheckForUpdatesMenuItem().setOnAction(null);
		controller.getAboutMenuItem().setOnAction(null);
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
			controller.getUndoMenuItem().setDisable(true);
			controller.getRedoMenuItem().textProperty().unbind();
			controller.getRedoMenuItem().setText("Redo");
			controller.getRedoMenuItem().setDisable(true);
		}
	}
}
