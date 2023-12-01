/*
 * MainWindowController.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import jloda.fx.control.SplittableTabPane;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.IMainWindow;
import jloda.fx.window.MainWindowManager;
import splitstree6.utils.Platform;

import java.util.ArrayList;

public class MainWindowController {
	@FXML
	private Tooltip fileTooltip;

	@FXML
	private VBox topVBox;

	@FXML
	private MenuBar menuBar;

	@FXML
	private Menu fileMenu;

	@FXML
	private MenuItem newMenuItem;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private Menu openRecentMenu;

	@FXML
	private MenuItem importTaxonTraitsMenuItem;

	@FXML
	private MenuItem importDialogMenuItem;
	@FXML
	private MenuItem importTaxonDisplayMenuItem;

	@FXML
	private MenuItem replaceDataMenuItem;

	@FXML
	private MenuItem editInputMenuItem;

	@FXML
	private MenuItem analyzeGenomesMenuItem;

	@FXML
	private MenuItem saveMenuItem;

	@FXML
	private MenuItem saveAsMenuItem;

	@FXML
	private MenuItem exportImageMenuItem;

	@FXML
	private MenuItem exportTaxonDisplayLabelsMenuItem;

	@FXML
	private MenuItem exportTaxonTraitsMenuItem;

	@FXML
	private MenuItem exportWorkflowMenuItem;

	@FXML
	private MenuItem pageSetupMenuItem;

	@FXML
	private MenuItem printMenuItem;

	@FXML
	private Menu toolsMenu;

	@FXML
	private MenuItem importMultipleTreeFilesMenuItem;

	@FXML
	private MenuItem importTreeNamesMenuItem;

	@FXML
	private MenuItem GroupIdenticalHaplotypesFilesMenuItem;

	@FXML
	private MenuItem closeMenuItem;

	@FXML
	private MenuItem quitMenuItem;

	@FXML
	private Menu editMenu;

	@FXML
	private MenuItem undoMenuItem;

	@FXML
	private MenuItem redoMenuItem;

	@FXML
	private MenuItem cutMenuItem;

	@FXML
	private MenuItem copyMenuItem;

	@FXML
	private MenuItem copyImageMenuItem;

	@FXML
	private MenuItem copyNewickMenuItem;

	@FXML
	private MenuItem pasteMenuItem;

	@FXML
	private MenuItem duplicateMenuItem;

	@FXML
	private MenuItem deleteMenuItem;

	@FXML
	private CheckMenuItem findMenuItem;

	@FXML
	private MenuItem findAgainMenuItem;

	@FXML
	private CheckMenuItem replaceMenuItem;

	@FXML
	private MenuItem gotoLineMenuItem;

	@FXML
	private MenuItem preferencesMenuItem;

	@FXML
	private MenuItem selectAllMenuItem;

	@FXML
	private MenuItem selectNoneMenuItem;

	@FXML
	private MenuItem selectInverseMenuItem;

	@FXML
	private MenuItem selectFromPreviousMenuItem;

	@FXML
	private MenuItem selectBracketsMenuItem;

	@FXML
	private MenuItem selectCompatibleSitesMenuItem;

	@FXML
	public Menu selectSetsMenu;

	@FXML
	private MenuItem increaseFontSizeMenuItem;

	@FXML
	private MenuItem decreaseFontSizeMenuItem;

	@FXML
	private MenuItem zoomInMenuItem;

	@FXML
	private MenuItem zoomOutMenuItem;

	@FXML
	private MenuItem zoomInHorizontalMenuItem;

	@FXML
	private MenuItem zoomOutHorizontalMenuItem;

	@FXML
	private MenuItem resetMenuItem;

	@FXML
	private MenuItem rotateLeftMenuItem;

	@FXML
	private MenuItem rotateRightMenuItem;

	@FXML
	private MenuItem flipMenuItem;

	@FXML
	private MenuItem layoutLabelsMenuItem;

	@FXML
	private CheckMenuItem showScaleBarMenuItem;

	@FXML
	private CheckMenuItem useDarkThemeMenuItem;

	@FXML
	private MenuItem useFullScreenMenuItem;

	@FXML
	private MenuItem filterTaxaMenuItem;

	@FXML
	private MenuItem filterCharactersMenuItem;

	@FXML
	private MenuItem filterTreesMenuItem;

	@FXML
	private MenuItem filterSplitsMenuItem;

	@FXML
	private MenuItem splitsSliderMenuItem;

	@FXML
	private MenuItem traitsMenuItem;

	@FXML
	private CheckMenuItem uncorrectedPMenuItem;

	@FXML
	private CheckMenuItem logDetMenuItem;

	@FXML
	private CheckMenuItem hky85MenuItem;

	@FXML
	private CheckMenuItem jukesCantorMenuItem;

	@FXML
	private CheckMenuItem k2pMenuItem;

	@FXML
	private CheckMenuItem k3stMenuItem;

	@FXML
	private CheckMenuItem f81MenuItem;

	@FXML
	private CheckMenuItem f84MenuItem;

	@FXML
	private CheckMenuItem proteinMLDistanceMenuItem;

	@FXML
	private CheckMenuItem geneContentDistanceMenuItem;

	@FXML
	private CheckMenuItem njMenuItem;

	@FXML
	private CheckMenuItem bioNJMenuItem;

	@FXML
	private CheckMenuItem upgmaMenuItem;

	@FXML
	private CheckMenuItem bunemanTreeMenuItem;

	@FXML
	private CheckMenuItem consensusTreeMenuItem;

	@FXML
	private CheckMenuItem minSpanningTreeMenuItem;

	@FXML
	private CheckMenuItem rerootOrReorderTreesMenuItem;

	@FXML
	private CheckMenuItem viewTreePagesMenuItem;

	@FXML
	private CheckMenuItem viewSingleTreeMenuItem;

	@FXML
	private CheckMenuItem viewTanglegramMenuItem;

	@FXML
	private CheckMenuItem viewDensiTreeMenuItem;

	@FXML
	private CheckMenuItem neighborNetMenuItem;

	@FXML
	private CheckMenuItem splitDecompositionMenuItem;

	@FXML
	private CheckMenuItem parsimonySplitsMenuItem;

	@FXML
	private CheckMenuItem consensusNetworkMenuItem;

	@FXML
	private CheckMenuItem consensusOutlineMenuItem;

	@FXML
	private CheckMenuItem consensusSplitsMenuItem;

	@FXML
	private CheckMenuItem superNetworkMenuItem;

	@FXML
	private CheckMenuItem medianJoiningMenuItem;

	@FXML
	private CheckMenuItem minSpanningNetworkMenuItem;

	@FXML
	private CheckMenuItem hybridizationNetworkMenuItem;

	@FXML
	private CheckMenuItem clusterNetworkMenuItem;

	@FXML
	private CheckMenuItem pcoaMenuItem;

	@FXML
	private CheckMenuItem bootStrapTreeMenuItem;

	@FXML
	private CheckMenuItem bootstrapTreeAsNetworkMenuItem;

	@FXML
	private CheckMenuItem bootStrapNetworkMenuItem;

	@FXML
	private MenuItem estimateInvariableSitesMenuItem;

	@FXML
	private MenuItem computeDeltaScoreMenuItem;

	@FXML
	private MenuItem phiTestMenuItem;

	@FXML
	private MenuItem computeRootedTreeFairProportionMenuItem;

	@FXML
	private MenuItem computeRootedTreeEqualSplitsMenuItem;

	@FXML
	private MenuItem computeTreePhylogeneticDiversityMenuItem;

	@FXML
	private MenuItem computeSplitsPhylogeneticDiversityMenuItem;

	@FXML
	private MenuItem computeSplitsShapleyValuesMenuItem;

	@FXML
	private MenuItem computeUnrootedTreeShapleyMenuItem;

	@FXML
	private MenuItem showWorkflowMenuItem;

	@FXML
	private Menu windowMenu;

	@FXML
	private Menu helpMenu;

	@FXML
	private CheckMenuItem showMessageWindowMenuItem;

	@FXML
	private MenuItem setWindowSizeMenuItem;

	@FXML
	private MenuItem checkForUpdatesMenuItem;

	@FXML
	private MenuItem aboutMenuItem;

	@FXML
	private Button openCloseLeftButton;

	@FXML
	private SplitPane mainSplitPane;

	@FXML
	private SplitPane leftSplitPane;
	@FXML
	private AnchorPane leftAnchorPane;

	@FXML
	private BorderPane workflowBorderPane;

	@FXML
	private BorderPane algorithmsBorderPane;

	@FXML
	private ToolBar algorithmsTabToolBar;

	@FXML
	private AnchorPane rightAnchorPane;

	@FXML
	private BorderPane mainBorderPane;

	@FXML
	private Button openCloseRightButton;

	@FXML
	private FlowPane bottomFlowPane;

	@FXML
	private Label memoryLabel;

	@FXML
	private MenuButton fileMenuButton;

	@FXML
	private TextField fileNameTextField;

	@FXML
	private Button undoButton;

	@FXML
	private Button redoButton;
	@FXML
	private Button findButton;

	@FXML
	private MenuButton exportButton;

	@FXML
	private MenuButton fileNameMenuButton;

	private final SplittableTabPane algorithmTabPane = new SplittableTabPane();

	//@FXML
	//private TabPane mainTabPane;

	private final SplittableTabPane mainTabPane = new SplittableTabPane();

	@FXML
	void initialize() {
		if (!Platform.isDesktop()) {
			((Pane) menuBar.getParent()).getChildren().remove(menuBar);
			topVBox.setPadding(new Insets(30, 0, 0, 0));
		}

		MaterialIcons.setIcon(fileMenuButton, "file_open");

		MaterialIcons.setIcon(undoButton, "undo");
		MaterialIcons.setIcon(redoButton, "redo");
		MaterialIcons.setIcon(findButton, "search");
		MaterialIcons.setIcon(exportButton, "ios_share");


		algorithmsBorderPane.setCenter(algorithmTabPane);
		mainBorderPane.setCenter(mainTabPane);

		if (ProgramProperties.isMacOS()) {
			getMenuBar().setUseSystemMenuBar(true);
			getFileMenu().getItems().remove(getQuitMenuItem());
			// windowMenu.getItems().remove(getAboutMenuItem());
			//editMenu.getItems().remove(getPreferencesMenuItem());
		}

		final ArrayList<MenuItem> originalWindowMenuItems = new ArrayList<>(windowMenu.getItems());

		final InvalidationListener invalidationListener = observable -> {
			windowMenu.getItems().setAll(originalWindowMenuItems);
			int count = 0;
			for (IMainWindow mainWindow : MainWindowManager.getInstance().getMainWindows()) {
				if (mainWindow.getStage() != null) {
					final String title = mainWindow.getStage().getTitle();
					if (title != null) {
						final MenuItem menuItem = new MenuItem(title.replaceAll("- " + ProgramProperties.getProgramName(), ""));
						menuItem.setOnAction((e) -> mainWindow.getStage().toFront());
						menuItem.setAccelerator(new KeyCharacterCombination("" + (++count), KeyCombination.SHORTCUT_DOWN));
						windowMenu.getItems().add(menuItem);
					}
				}
				if (MainWindowManager.getInstance().getAuxiliaryWindows(mainWindow) != null) {
					for (Stage auxStage : MainWindowManager.getInstance().getAuxiliaryWindows(mainWindow)) {
						final String title = auxStage.getTitle();
						if (title != null) {
							final MenuItem menuItem = new MenuItem(title.replaceAll("- " + ProgramProperties.getProgramName(), ""));
							menuItem.setOnAction((e) -> auxStage.toFront());
							windowMenu.getItems().add(menuItem);
						}
					}
				}
			}
		};
		MainWindowManager.getInstance().changedProperty().addListener(invalidationListener);
		invalidationListener.invalidated(null);
	}

	public VBox getTopVBox() {
		return topVBox;
	}

	public MenuBar getMenuBar() {
		return menuBar;
	}

	public Menu getFileMenu() {
		return fileMenu;
	}

	public MenuItem getNewMenuItem() {
		return newMenuItem;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public Menu getOpenRecentMenu() {
		return openRecentMenu;
	}

	public MenuItem getImportTaxonTraitsMenuItem() {
		return importTaxonTraitsMenuItem;
	}

	public MenuItem getImportDialogMenuItem() {
		return importDialogMenuItem;
	}

	public MenuItem getImportTaxonDisplayMenuItem() {
		return importTaxonDisplayMenuItem;
	}

	public MenuItem getImportMultipleTreeFilesMenuItem() {
		return importMultipleTreeFilesMenuItem;
	}

	public MenuItem getImportTreeNamesMenuItem() {
		return importTreeNamesMenuItem;
	}

	public MenuItem getReplaceDataMenuItem() {
		return replaceDataMenuItem;
	}

	public MenuItem getEditInputMenuItem() {
		return editInputMenuItem;
	}

	public MenuItem getAnalyzeGenomesMenuItem() {
		return analyzeGenomesMenuItem;
	}

	public MenuItem getSaveMenuItem() {
		return saveMenuItem;
	}

	public MenuItem getSaveAsMenuItem() {
		return saveAsMenuItem;
	}

	public MenuItem getExportImageMenuItem() {
		return exportImageMenuItem;
	}

	public MenuItem getExportTaxonDisplayLabelsMenuItem() {
		return exportTaxonDisplayLabelsMenuItem;
	}

	public MenuItem getExportTaxonTraitsMenuItem() {
		return exportTaxonTraitsMenuItem;
	}

	public MenuItem getExportWorkflowMenuItem() {
		return exportWorkflowMenuItem;
	}

	public MenuItem getPageSetupMenuItem() {
		return pageSetupMenuItem;
	}

	public MenuItem getPrintMenuItem() {
		return printMenuItem;
	}

	public Menu getToolsMenu() {
		return toolsMenu;
	}


	public MenuItem getGroupIdenticalHaplotypesFilesMenuItem() {
		return GroupIdenticalHaplotypesFilesMenuItem;
	}

	public MenuItem getCloseMenuItem() {
		return closeMenuItem;
	}

	public MenuItem getQuitMenuItem() {
		return quitMenuItem;
	}

	public Menu getEditMenu() {
		return editMenu;
	}

	public MenuItem getUndoMenuItem() {
		return undoMenuItem;
	}

	public MenuItem getRedoMenuItem() {
		return redoMenuItem;
	}

	public MenuItem getCutMenuItem() {
		return cutMenuItem;
	}

	public MenuItem getCopyMenuItem() {
		return copyMenuItem;
	}

	public MenuItem getCopyImageMenuItem() {
		return copyImageMenuItem;
	}

	public MenuItem getCopyNewickMenuItem() {
		return copyNewickMenuItem;
	}

	public MenuItem getPasteMenuItem() {
		return pasteMenuItem;
	}

	public MenuItem getDuplicateMenuItem() {
		return duplicateMenuItem;
	}

	public MenuItem getDeleteMenuItem() {
		return deleteMenuItem;
	}

	public CheckMenuItem getFindMenuItem() {
		return findMenuItem;
	}

	public MenuItem getFindAgainMenuItem() {
		return findAgainMenuItem;
	}

	public CheckMenuItem getReplaceMenuItem() {
		return replaceMenuItem;
	}

	public MenuItem getGotoLineMenuItem() {
		return gotoLineMenuItem;
	}

	public MenuItem getPreferencesMenuItem() {
		return preferencesMenuItem;
	}

	public MenuItem getSelectAllMenuItem() {
		return selectAllMenuItem;
	}

	public MenuItem getSelectNoneMenuItem() {
		return selectNoneMenuItem;
	}

	public MenuItem getSelectInverseMenuItem() {
		return selectInverseMenuItem;
	}

	public MenuItem getSelectFromPreviousMenuItem() {
		return selectFromPreviousMenuItem;
	}

	public MenuItem getSelectBracketsMenuItem() {
		return selectBracketsMenuItem;
	}

	public MenuItem getSelectCompatibleSitesMenuItem() {
		return selectCompatibleSitesMenuItem;
	}

	public Menu getSelectSetsMenu() {
		return selectSetsMenu;
	}

	public MenuItem getIncreaseFontSizeMenuItem() {
		return increaseFontSizeMenuItem;
	}

	public MenuItem getDecreaseFontSizeMenuItem() {
		return decreaseFontSizeMenuItem;
	}

	public MenuItem getZoomInMenuItem() {
		return zoomInMenuItem;
	}

	public MenuItem getZoomOutMenuItem() {
		return zoomOutMenuItem;
	}

	public MenuItem getZoomInHorizontalMenuItem() {
		return zoomInHorizontalMenuItem;
	}

	public MenuItem getZoomOutHorizontalMenuItem() {
		return zoomOutHorizontalMenuItem;
	}

	public MenuItem getResetMenuItem() {
		return resetMenuItem;
	}

	public MenuItem getRotateLeftMenuItem() {
		return rotateLeftMenuItem;
	}

	public MenuItem getRotateRightMenuItem() {
		return rotateRightMenuItem;
	}

	public MenuItem getFlipMenuItem() {
		return flipMenuItem;
	}

	public MenuItem getLayoutLabelsMenuItem() {
		return layoutLabelsMenuItem;
	}

	public CheckMenuItem getShowScaleBarMenuItem() {
		return showScaleBarMenuItem;
	}

	public CheckMenuItem getUseDarkThemeMenuItem() {
		return useDarkThemeMenuItem;
	}

	public MenuItem getUseFullScreenMenuItem() {
		return useFullScreenMenuItem;
	}

	public MenuItem getFilterTaxaMenuItem() {
		return filterTaxaMenuItem;
	}

	public MenuItem getFilterCharactersMenuItem() {
		return filterCharactersMenuItem;
	}

	public MenuItem getFilterTreesMenuItem() {
		return filterTreesMenuItem;
	}

	public MenuItem getFilterSplitsMenuItem() {
		return filterSplitsMenuItem;
	}

	public MenuItem getSplitsSliderMenuItem() {
		return splitsSliderMenuItem;
	}

	public MenuItem getTraitsMenuItem() {
		return traitsMenuItem;
	}

	public CheckMenuItem getUncorrectedPMenuItem() {
		return uncorrectedPMenuItem;
	}

	public CheckMenuItem getLogDetMenuItem() {
		return logDetMenuItem;
	}

	public CheckMenuItem getHky85MenuItem() {
		return hky85MenuItem;
	}

	public CheckMenuItem getJukesCantorMenuItem() {
		return jukesCantorMenuItem;
	}

	public CheckMenuItem getK2pMenuItem() {
		return k2pMenuItem;
	}

	public CheckMenuItem getK3stMenuItem() {
		return k3stMenuItem;
	}

	public CheckMenuItem getF81MenuItem() {
		return f81MenuItem;
	}

	public CheckMenuItem getF84MenuItem() {
		return f84MenuItem;
	}

	public CheckMenuItem getProteinMLDistanceMenuItem() {
		return proteinMLDistanceMenuItem;
	}

	public CheckMenuItem getGeneContentDistanceMenuItem() {
		return geneContentDistanceMenuItem;
	}

	public CheckMenuItem getNjMenuItem() {
		return njMenuItem;
	}

	public CheckMenuItem getBioNJMenuItem() {
		return bioNJMenuItem;
	}

	public CheckMenuItem getUpgmaMenuItem() {
		return upgmaMenuItem;
	}

	public CheckMenuItem getBunemanTreeMenuItem() {
		return bunemanTreeMenuItem;
	}

	public CheckMenuItem getConsensusTreeMenuItem() {
		return consensusTreeMenuItem;
	}

	public CheckMenuItem getMinSpanningTreeMenuItem() {
		return minSpanningTreeMenuItem;
	}

	public CheckMenuItem getRerootOrReorderTreesMenuItem() {
		return rerootOrReorderTreesMenuItem;
	}

	public CheckMenuItem getViewTreePagesMenuItem() {
		return viewTreePagesMenuItem;
	}

	public CheckMenuItem getViewSingleTreeMenuItem() {
		return viewSingleTreeMenuItem;
	}

	public CheckMenuItem getViewTanglegramMenuItem() {
		return viewTanglegramMenuItem;
	}

	public CheckMenuItem getViewDensiTreeMenuItem() {
		return viewDensiTreeMenuItem;
	}

	public CheckMenuItem getNeighborNetMenuItem() {
		return neighborNetMenuItem;
	}

	public CheckMenuItem getSplitDecompositionMenuItem() {
		return splitDecompositionMenuItem;
	}

	public CheckMenuItem getParsimonySplitsMenuItem() {
		return parsimonySplitsMenuItem;
	}

	public CheckMenuItem getConsensusNetworkMenuItem() {
		return consensusNetworkMenuItem;
	}

	public CheckMenuItem getConsensusOutlineMenuItem() {
		return consensusOutlineMenuItem;
	}

	public CheckMenuItem getConsensusSplitsMenuItem() {
		return consensusSplitsMenuItem;
	}

	public CheckMenuItem getSuperNetworkMenuItem() {
		return superNetworkMenuItem;
	}

	public CheckMenuItem getMedianJoiningMenuItem() {
		return medianJoiningMenuItem;
	}

	public CheckMenuItem getMinSpanningNetworkMenuItem() {
		return minSpanningNetworkMenuItem;
	}

	public CheckMenuItem getHybridizationNetworkMenuItem() {
		return hybridizationNetworkMenuItem;
	}

	public CheckMenuItem getClusterNetworkMenuItem() {
		return clusterNetworkMenuItem;
	}

	public CheckMenuItem getPcoaMenuItem() {
		return pcoaMenuItem;
	}

	public CheckMenuItem getBootStrapTreeMenuItem() {
		return bootStrapTreeMenuItem;
	}

	public CheckMenuItem getBootstrapTreeAsNetworkMenuItem() {
		return bootstrapTreeAsNetworkMenuItem;
	}

	public CheckMenuItem getBootStrapNetworkMenuItem() {
		return bootStrapNetworkMenuItem;
	}

	public MenuItem getEstimateInvariableSitesMenuItem() {
		return estimateInvariableSitesMenuItem;
	}

	public MenuItem getComputeDeltaScoreMenuItem() {
		return computeDeltaScoreMenuItem;
	}

	public MenuItem getPhiTestMenuItem() {
		return phiTestMenuItem;
	}

	public MenuItem getComputeRootedTreeFairProportionMenuItem() {
		return computeRootedTreeFairProportionMenuItem;
	}

	public MenuItem getComputeRootedTreeEqualSplitsMenuItem() {
		return computeRootedTreeEqualSplitsMenuItem;
	}

	public MenuItem getComputeTreePhylogeneticDiversityMenuItem() {
		return computeTreePhylogeneticDiversityMenuItem;
	}

	public MenuItem getComputeSplitsPhylogeneticDiversityMenuItem() {
		return computeSplitsPhylogeneticDiversityMenuItem;
	}

	public MenuItem getComputeSplitsShapleyValuesMenuItem() {
		return computeSplitsShapleyValuesMenuItem;
	}

	public MenuItem getComputeUnrootedTreeShapleyMenuItem() {
		return computeUnrootedTreeShapleyMenuItem;
	}

	public MenuItem getShowWorkflowMenuItem() {
		return showWorkflowMenuItem;
	}

	public Menu getWindowMenu() {
		return windowMenu;
	}

	public Menu getHelpMenu() {
		return helpMenu;
	}

	public CheckMenuItem getShowMessageWindowMenuItem() {
		return showMessageWindowMenuItem;
	}

	public MenuItem getSetWindowSizeMenuItem() {
		return setWindowSizeMenuItem;
	}

	public MenuItem getCheckForUpdatesMenuItem() {
		return checkForUpdatesMenuItem;
	}

	public MenuItem getAboutMenuItem() {
		return aboutMenuItem;
	}

	public Button getOpenCloseLeftButton() {
		return openCloseLeftButton;
	}

	public SplitPane getMainSplitPane() {
		return mainSplitPane;
	}

	public SplitPane getLeftSplitPane() {
		return leftSplitPane;
	}

	public AnchorPane getLeftAnchorPane() {
		return leftAnchorPane;
	}

	public BorderPane getAlgorithmsBorderPane() {
		return algorithmsBorderPane;
	}

	public BorderPane getWorkflowBorderPane() {
		return workflowBorderPane;
	}

	public ToolBar getAlgorithmsTabToolBar() {
		return algorithmsTabToolBar;
	}

	public AnchorPane getRightAnchorPane() {
		return rightAnchorPane;
	}

	public BorderPane getMainBorderPane() {
		return mainBorderPane;
	}

	public Button getOpenCloseRightButton() {
		return openCloseRightButton;
	}

	public FlowPane getBottomFlowPane() {
		return bottomFlowPane;
	}

	public Label getMemoryLabel() {
		return memoryLabel;
	}

	public SplittableTabPane getAlgorithmTabPane() {
		return algorithmTabPane;
	}

	public SplittableTabPane getMainTabPane() {
		return mainTabPane;
	}

	public TextField getFileNameTextField() {
		return fileNameTextField;
	}

	public MenuButton getFileMenuButton() {
		return fileMenuButton;
	}

	public Tooltip getFileTooltip() {
		return fileTooltip;
	}

	public Button getUndoButton() {
		return undoButton;
	}

	public Button getRedoButton() {
		return redoButton;
	}

	public Button getFindButton() {
		return findButton;
	}

	public MenuButton getExportButton() {
		return exportButton;
	}

	public MenuButton getFileNameMenuButton() {
		return fileNameMenuButton;
	}
}
