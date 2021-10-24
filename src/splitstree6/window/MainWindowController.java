/*
 *  MainWindowController.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jloda.fx.control.SplittableTabPane;
import jloda.fx.window.IMainWindow;
import jloda.fx.window.MainWindowManager;
import jloda.util.ProgramProperties;

import java.util.ArrayList;

public class MainWindowController {
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
	private MenuItem importMenuItem;

	@FXML
	private MenuItem replaceDataMenuItem;

	@FXML
	private MenuItem inputEditorMenuItem;

	@FXML
	private MenuItem analyzeGenomesMenuItem;

	@FXML
	private MenuItem saveMenuItem;

	@FXML
	private MenuItem saveAsMenuItem;

	@FXML
	private MenuItem exportMenuItem;

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
	private MenuItem pasteMenuItem;

	@FXML
	private MenuItem duplicateMenuItem;

	@FXML
	private MenuItem deleteMenuItem;

	@FXML
	private MenuItem findMenuItem;

	@FXML
	private MenuItem findAgainMenuItem;

	@FXML
	private MenuItem replaceMenuItem;

	@FXML
	private MenuItem gotoLineMenuItem;

	@FXML
	private MenuItem preferencesMenuItem;

	@FXML
	private MenuItem selectAllMenuItem;

	@FXML
	private MenuItem selectNoneMenuItem;

	@FXML
	private MenuItem selectAllNodesMenuItem;

	@FXML
	private MenuItem selectAllLabeledNodesMenuItem;

	@FXML
	private MenuItem selectAllBelowMenuItem;

	@FXML
	private MenuItem selectBracketsMenuItem;

	@FXML
	private MenuItem invertNodeSelectionMenuItem;

	@FXML
	private MenuItem deselectAllNodesMenuItem;

	@FXML
	private MenuItem selectAllEdgesMenuItem;

	@FXML
	private MenuItem selectAllLabeledEdgesMenuItem;

	@FXML
	private MenuItem selectAllEdgesBelowMenuItem;

	@FXML
	private MenuItem invertEdgeSelectionMenuItem;

	@FXML
	private MenuItem deselectEdgesMenuItem;

	@FXML
	private MenuItem selectFromPreviousMenuItem;

	@FXML
	private MenuItem increaseFontSizeMenuItem;

	@FXML
	private MenuItem decreaseFontSizeMenuItem;

	@FXML
	private MenuItem zoomInMenuItem;

	@FXML
	private MenuItem zoomOutMenuItem;

	@FXML
	private MenuItem resetMenuItem;

	@FXML
	private MenuItem rotateLeftMenuItem;

	@FXML
	private MenuItem rotateRightMenuItem;

	@FXML
	private MenuItem flipMenuItem;

	@FXML
	private MenuItem formatNodesMenuItem;

	@FXML
	private MenuItem layoutLabelsMenuItem;

	@FXML
	private CheckMenuItem sparseLabelsCheckMenuItem;

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
	private MenuItem traitsMenuItem;

	@FXML
	private MenuItem uncorrectedPMenuItem;

	@FXML
	private MenuItem logDetMenuItem;

	@FXML
	private MenuItem hky85MenuItem;

	@FXML
	private MenuItem jukesCantorMenuItem;

	@FXML
	private MenuItem k2pMenuItem;

	@FXML
	private MenuItem k3stMenuItem;

	@FXML
	private MenuItem f81MenuItem;

	@FXML
	private MenuItem f84MenuItem;

	@FXML
	private MenuItem proteinMLDistanceMenuItem;

	@FXML
	private MenuItem geneContentDistanceMenuItem;

	@FXML
	private MenuItem njMenuItem;

	@FXML
	private MenuItem bioNJMenuItem;

	@FXML
	private MenuItem upgmaMenuItem;

	@FXML
	private MenuItem bunemanTreeMenuItem;

	@FXML
	private MenuItem selectTreeMenuItem;

	@FXML
	private MenuItem consensusTreeMenuItem;

	@FXML
	private MenuItem rootByOutgroupMenuItem;

	@FXML
	private MenuItem rootByMidpointMenuItem;

	@FXML
	private MenuItem treeViewMenuItem;

	@FXML
	private MenuItem treeGridMenuItem;

	@FXML
	private MenuItem tanglegramMenuItem;

	@FXML
	private MenuItem neighborNetMenuItem;

	@FXML
	private MenuItem splitDecompositionMenuItem;

	@FXML
	private MenuItem parsimonySplitsMenuItem;

	@FXML
	private MenuItem consensusNetworkMenuItem;

	@FXML
	private MenuItem filteredSuperNetworkMenuItem;

	@FXML
	private MenuItem medianNetworkMenuItem;

	@FXML
	private MenuItem medianJoiningMenuItem;

	@FXML
	private MenuItem minSpanningNetworkMenuItem;

	@FXML
	private MenuItem consensusClusterNetworkMenuItem;

	@FXML
	private MenuItem hybridizationNetworkMenuItem;

	@FXML
	private MenuItem splitsNetworkViewMenuItem;

	@FXML
	private MenuItem haplotypeNetworkViewMenuItem;

	@FXML
	private MenuItem show3DViewerMenuItem;

	@FXML
	private MenuItem relaxMenuItem;

	@FXML
	private MenuItem pcoaMenuItem;

	@FXML
	private MenuItem brayCurtisMenuItem;

	@FXML
	private MenuItem jsdMenuItem;

	@FXML
	private MenuItem bootstrappingMenuItem;

	@FXML
	private MenuItem showBootStrapTreeMenuItem;

	@FXML
	private MenuItem showBootStrapNetworkMenuItem;

	@FXML
	private MenuItem estimateInvariableSitesMenuItem;

	@FXML
	private MenuItem computePhylogeneticDiversityMenuItem;

	@FXML
	private MenuItem computeDeltaScoreMenuItem;

	@FXML
	private MenuItem showWorkflowMenuItem;

	@FXML
	private Menu windowMenu;

	@FXML
	private MenuItem showMessageWindowMenuItem;

	@FXML
	private MenuItem checkForUpdatesMenuItem;

	@FXML
	private MenuItem aboutMenuItem;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Button openCloseLeftButton;

	@FXML
	private SplitPane mainSplitPane;

	@FXML
	private SplitPane leftSplitPane;
	@FXML
	private AnchorPane leftAnchorPane;

	@FXML
	private AnchorPane treeViewAnchorPane;

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

	//@FXML
	//private TabPane algorithmTabPane;

	private final SplittableTabPane algorithmTabPane = new SplittableTabPane();

	//@FXML
	//private TabPane mainTabPane;

	private final SplittableTabPane mainTabPane = new SplittableTabPane();

	@FXML
	void initialize() {
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

	public MenuItem getImportMenuItem() {
		return importMenuItem;
	}

	public MenuItem getReplaceDataMenuItem() {
		return replaceDataMenuItem;
	}

	public MenuItem getInputEditorMenuItem() {
		return inputEditorMenuItem;
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

	public MenuItem getExportMenuItem() {
		return exportMenuItem;
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

	public MenuItem getImportMultipleTreeFilesMenuItem() {
		return importMultipleTreeFilesMenuItem;
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

	public MenuItem getPasteMenuItem() {
		return pasteMenuItem;
	}

	public MenuItem getDuplicateMenuItem() {
		return duplicateMenuItem;
	}

	public MenuItem getDeleteMenuItem() {
		return deleteMenuItem;
	}

	public MenuItem getFindMenuItem() {
		return findMenuItem;
	}

	public MenuItem getFindAgainMenuItem() {
		return findAgainMenuItem;
	}

	public MenuItem getReplaceMenuItem() {
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

	public MenuItem getSelectAllNodesMenuItem() {
		return selectAllNodesMenuItem;
	}

	public MenuItem getSelectAllLabeledNodesMenuItem() {
		return selectAllLabeledNodesMenuItem;
	}

	public MenuItem getSelectAllBelowMenuItem() {
		return selectAllBelowMenuItem;
	}

	public MenuItem getSelectBracketsMenuItem() {
		return selectBracketsMenuItem;
	}

	public MenuItem getInvertNodeSelectionMenuItem() {
		return invertNodeSelectionMenuItem;
	}

	public MenuItem getDeselectAllNodesMenuItem() {
		return deselectAllNodesMenuItem;
	}

	public MenuItem getSelectAllEdgesMenuItem() {
		return selectAllEdgesMenuItem;
	}

	public MenuItem getSelectAllLabeledEdgesMenuItem() {
		return selectAllLabeledEdgesMenuItem;
	}

	public MenuItem getSelectAllEdgesBelowMenuItem() {
		return selectAllEdgesBelowMenuItem;
	}

	public MenuItem getInvertEdgeSelectionMenuItem() {
		return invertEdgeSelectionMenuItem;
	}

	public MenuItem getDeselectEdgesMenuItem() {
		return deselectEdgesMenuItem;
	}

	public MenuItem getSelectFromPreviousMenuItem() {
		return selectFromPreviousMenuItem;
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

	public MenuItem getFormatNodesMenuItem() {
		return formatNodesMenuItem;
	}

	public MenuItem getLayoutLabelsMenuItem() {
		return layoutLabelsMenuItem;
	}

	public CheckMenuItem getSparseLabelsCheckMenuItem() {
		return sparseLabelsCheckMenuItem;
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

	public MenuItem getTraitsMenuItem() {
		return traitsMenuItem;
	}

	public MenuItem getUncorrectedPMenuItem() {
		return uncorrectedPMenuItem;
	}

	public MenuItem getLogDetMenuItem() {
		return logDetMenuItem;
	}

	public MenuItem getHky85MenuItem() {
		return hky85MenuItem;
	}

	public MenuItem getJukesCantorMenuItem() {
		return jukesCantorMenuItem;
	}

	public MenuItem getK2pMenuItem() {
		return k2pMenuItem;
	}

	public MenuItem getK3stMenuItem() {
		return k3stMenuItem;
	}

	public MenuItem getF81MenuItem() {
		return f81MenuItem;
	}

	public MenuItem getF84MenuItem() {
		return f84MenuItem;
	}

	public MenuItem getProteinMLDistanceMenuItem() {
		return proteinMLDistanceMenuItem;
	}

	public MenuItem getGeneContentDistanceMenuItem() {
		return geneContentDistanceMenuItem;
	}

	public MenuItem getNjMenuItem() {
		return njMenuItem;
	}

	public MenuItem getBioNJMenuItem() {
		return bioNJMenuItem;
	}

	public MenuItem getUpgmaMenuItem() {
		return upgmaMenuItem;
	}

	public MenuItem getBunemanTreeMenuItem() {
		return bunemanTreeMenuItem;
	}

	public MenuItem getSelectTreeMenuItem() {
		return selectTreeMenuItem;
	}

	public MenuItem getConsensusTreeMenuItem() {
		return consensusTreeMenuItem;
	}

	public MenuItem getRootByOutgroupMenuItem() {
		return rootByOutgroupMenuItem;
	}

	public MenuItem getRootByMidpointMenuItem() {
		return rootByMidpointMenuItem;
	}

	public MenuItem getTreeViewMenuItem() {
		return treeViewMenuItem;
	}

	public MenuItem getTreeGridMenuItem() {
		return treeGridMenuItem;
	}

	public MenuItem getTanglegramMenuItem() {
		return tanglegramMenuItem;
	}

	public MenuItem getNeighborNetMenuItem() {
		return neighborNetMenuItem;
	}

	public MenuItem getSplitDecompositionMenuItem() {
		return splitDecompositionMenuItem;
	}

	public MenuItem getParsimonySplitsMenuItem() {
		return parsimonySplitsMenuItem;
	}

	public MenuItem getConsensusNetworkMenuItem() {
		return consensusNetworkMenuItem;
	}

	public MenuItem getFilteredSuperNetworkMenuItem() {
		return filteredSuperNetworkMenuItem;
	}

	public MenuItem getMedianNetworkMenuItem() {
		return medianNetworkMenuItem;
	}

	public MenuItem getMedianJoiningMenuItem() {
		return medianJoiningMenuItem;
	}

	public MenuItem getMinSpanningNetworkMenuItem() {
		return minSpanningNetworkMenuItem;
	}

	public MenuItem getConsensusClusterNetworkMenuItem() {
		return consensusClusterNetworkMenuItem;
	}

	public MenuItem getHybridizationNetworkMenuItem() {
		return hybridizationNetworkMenuItem;
	}

	public MenuItem getSplitsNetworkViewMenuItem() {
		return splitsNetworkViewMenuItem;
	}

	public MenuItem getHaplotypeNetworkViewMenuItem() {
		return haplotypeNetworkViewMenuItem;
	}

	public MenuItem getShow3DViewerMenuItem() {
		return show3DViewerMenuItem;
	}

	public MenuItem getRelaxMenuItem() {
		return relaxMenuItem;
	}

	public MenuItem getPcoaMenuItem() {
		return pcoaMenuItem;
	}

	public MenuItem getBrayCurtisMenuItem() {
		return brayCurtisMenuItem;
	}

	public MenuItem getJsdMenuItem() {
		return jsdMenuItem;
	}

	public MenuItem getBootstrappingMenuItem() {
		return bootstrappingMenuItem;
	}

	public MenuItem getShowBootStrapTreeMenuItem() {
		return showBootStrapTreeMenuItem;
	}

	public MenuItem getShowBootStrapNetworkMenuItem() {
		return showBootStrapNetworkMenuItem;
	}

	public MenuItem getEstimateInvariableSitesMenuItem() {
		return estimateInvariableSitesMenuItem;
	}

	public MenuItem getComputePhylogeneticDiversityMenuItem() {
		return computePhylogeneticDiversityMenuItem;
	}

	public MenuItem getComputeDeltaScoreMenuItem() {
		return computeDeltaScoreMenuItem;
	}

	public MenuItem getShowWorkflowMenuItem() {
		return showWorkflowMenuItem;
	}

	public Menu getWindowMenu() {
		return windowMenu;
	}

	public MenuItem getShowMessageWindowMenuItem() {
		return showMessageWindowMenuItem;
	}

	public MenuItem getCheckForUpdatesMenuItem() {
		return checkForUpdatesMenuItem;
	}

	public MenuItem getAboutMenuItem() {
		return aboutMenuItem;
	}

	public ToolBar getToolBar() {
		return toolBar;
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

	public AnchorPane getTreeViewAnchorPane() {
		return treeViewAnchorPane;
	}

	public BorderPane getAlgorithmsBorderPane() {
		return algorithmsBorderPane;
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
}
