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


import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import jloda.fx.util.BasicFX;
import jloda.fx.util.Print;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.WindowGeometry;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.Basic;
import jloda.util.ProgramProperties;
import splitstree6.dialog.SaveBeforeClosingDialog;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.treeview.WorkflowTreeView;

import java.util.Stack;

public class MainWindowPresenter {
	private final MainWindow mainWindow;
	private final ObjectProperty<IDisplayTab> focusedDisplayTab = new SimpleObjectProperty<>();

	private final ObservableMap<WorkflowNode, Tab> workFlowTabs = FXCollections.observableHashMap();

	private final SplitPanePresenter splitPanePresenter;

	public MainWindowPresenter(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		var controller = mainWindow.getController();

		var workflowTreeView = new WorkflowTreeView(mainWindow);

		controller.getTreeViewAnchorPane().getChildren().add(workflowTreeView);
		AnchorPane.setRightAnchor(workflowTreeView, 0.0);
		AnchorPane.setLeftAnchor(workflowTreeView, 0.0);
		AnchorPane.setTopAnchor(workflowTreeView, 0.0);
		AnchorPane.setBottomAnchor(workflowTreeView, 0.0);

		mainWindow.getStage().getScene().focusOwnerProperty().addListener((v, o, n) -> {
			try {
				var displayTab = getContainingDisplayTab(n);
				if (displayTab != null) {
					focusedDisplayTab.set(displayTab);
					disableAllMenuItems(controller);
					setupCommonMenuItems(mainWindow, controller, focusedDisplayTab);
					if (focusedDisplayTab.get() != null)
						focusedDisplayTab.get().getPresenter().setup();
					enableAllMenuItemsWithDefinedAction(controller);
				}
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});

		controller.getMainTabPane().getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			try {
				disableAllMenuItems(controller);
				setupCommonMenuItems(mainWindow, controller, focusedDisplayTab);
				if (n instanceof IDisplayTab displayTab) {
					displayTab.getPresenter().setup();
					focusedDisplayTab.set(displayTab);
				} else
					focusedDisplayTab.set(null);
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
					displayTab.getPresenter().setup();
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

		RecentFilesManager.getInstance().setupMenu(controller.getOpenRecentMenu());

		splitPanePresenter = new SplitPanePresenter(mainWindow.getController());
	}

	private void setupCommonMenuItems(MainWindow mainWindow, MainWindowController controller, ObjectProperty<IDisplayTab> focusedDisplayTab) {

		controller.getNewMenuItem().setOnAction(e -> MainWindowManager.getInstance().createAndShowWindow(false));

		controller.getOpenMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
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
			System.err.println("Not implemented");
		});

		controller.getSaveMenuItem().disableProperty().bind(mainWindow.dirtyProperty().not().or(mainWindow.emptyProperty()));
		controller.getSaveAsMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
		});
		controller.getSaveMenuItem().disableProperty().bind(mainWindow.emptyProperty());

		controller.getExportMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
		});
		controller.getExportMenuItem().disableProperty().bind(mainWindow.emptyProperty());

		controller.getExportWorkflowMenuItem().setOnAction(e -> {
			System.err.println("Not implemented");
		});
		controller.getExportWorkflowMenuItem().disableProperty().bind(mainWindow.emptyProperty());


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
				MainWindowManager.getInstance().closeMainWindow(mainWindow);
			}
		});

		controller.getCutMenuItem().setDisable(false);
		controller.getCopyMenuItem().setDisable(false);

		if (focusedDisplayTab.get() != null && focusedDisplayTab.get().getImageNode() != null) {
			controller.getCopyImageMenuItem().setOnAction(e -> {
				final Image snapshot = focusedDisplayTab.get().getImageNode().snapshot(null, null);
				final ClipboardContent clipboardContent = new ClipboardContent();
				clipboardContent.putImage(snapshot);
				Clipboard.getSystemClipboard().setContent(clipboardContent);
			});
			controller.getCopyImageMenuItem().disableProperty().bind(focusedDisplayTab.isNull().or(focusedDisplayTab.get().isEmptyProperty()));
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

		controller.getFilterTaxaMenuItem().setOnAction(null);
		controller.getFilterCharactersMenuItem().setOnAction(null);
		controller.getFilterTreesMenuItem().setOnAction(null);
		controller.getFilterSplitsMenuItem().setOnAction(null);

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

		controller.getSelectTreeMenuItem().setOnAction(null);
		controller.getConsensusTreeMenuItem().setOnAction(null);
		controller.getRootByOutgroupMenuItem().setOnAction(null);
		controller.getRootByMidpointMenuItem().setOnAction(null);
		controller.getTreeViewMenuItem().setOnAction(null);
		controller.getTreeGridMenuItem().setOnAction(null);
		controller.getTanglegramMenuItem().setOnAction(null);
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
		final MainWindow otherWindow;
		if (mainWindow.isEmpty())
			otherWindow = mainWindow;
		else {
			otherWindow = (MainWindow) MainWindowManager.getInstance().createAndShowWindow(false);
		}
		var otherController = otherWindow.getController();
		var tab = otherController.getMainTabPane().findTab(InputEditorTab.NAME);
		if (tab == null) {
			tab = new InputEditorTab(otherWindow);
			otherController.getMainTabPane().getTabs().add(tab);
		}
		mainWindow.getController().getMainTabPane().getSelectionModel().select(tab);
	}

	private void disableAllMenuItems(MainWindowController controller) {
		var stack = new Stack<MenuItem>();
		stack.addAll(controller.getMenuBar().getMenus());
		while (stack.size() > 0) {
			var item = stack.pop();
			if (item instanceof Menu menu) {
				if (!menu.getText().equals("Open Recent") && !menu.getText().equals("Window") && !menu.getText().equals("Help"))
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
				if (!menu.getText().equals("Open Recent") && !menu.getText().equals("Window") && !menu.getText().equals("Help"))
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

	public void displayInMainTabPaneUniquely(WorkflowNode workflowNode, Tab tab) {
		if (!workFlowTabs.containsKey(workflowNode)) {
			mainWindow.getController().getMainTabPane().getTabs().add(tab);
			workFlowTabs.put(workflowNode, tab);
		}
		mainWindow.getController().getMainTabPane().getSelectionModel().select(tab);
	}

	public SplitPanePresenter getSplitPanePresenter() {
		return splitPanePresenter;
	}
}
