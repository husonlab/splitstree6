/*
 *  CommonMenuPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.window.presenter;

import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.WindowGeometry;
import jloda.util.ProgramProperties;
import splitstree6.dialog.SaveBeforeClosingDialog;
import splitstree6.window.MainWindow;

public class CommonMenuPresenter {
	public static void apply(MainWindow mainWindow) {
		var controller = mainWindow.getController();


		controller.getNewMenuItem().setOnAction(e -> MainWindowManager.getInstance().createAndShowWindow(false));


		controller.getOpenMenuItem().setOnAction(e -> {
		});
		controller.getImportMenuItem().setOnAction(e -> {
		});
		controller.getReplaceDataMenuItem().setOnAction(e -> {
		});
		controller.getInputEditorMenuItem().setOnAction(e -> {
		});
		controller.getAnalyzeGenomesMenuItem().setOnAction(e -> {
		});

		controller.getSaveMenuItem().setOnAction(e -> {
		});
		controller.getSaveAsMenuItem().setOnAction(e -> {
		});

		controller.getExportMenuItem().setOnAction(e -> {
		});
		controller.getExportWorkflowMenuItem().setOnAction(e -> {
		});

		controller.getPageSetupMenuItem().setOnAction(e -> {
		});
		controller.getPrintMenuItem().setOnAction(e -> {
		});

		controller.getImportMultipleTreeFilesMenuItem().setOnAction(e -> {
		});
		controller.getGroupIdenticalHaplotypesFilesMenuItem().setOnAction(e -> {
		});

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

		// controller.getUndoMenuItem().setDisable(false);
		// controller.getRedoMenuItem().setDisable(false);

		controller.getCutMenuItem().setDisable(false);
		controller.getCopyMenuItem().setDisable(false);

		// controller.getCopyImageMenuItem().setDisable(false);

		controller.getPasteMenuItem().setDisable(false);

		// controller.getDuplicateMenuItem().setDisable(false);
		// controller.getDeleteMenuItem().setDisable(false);

		controller.getFindMenuItem().setDisable(false);
		controller.getFindAgainMenuItem().setDisable(false);

		controller.getReplaceMenuItem().setDisable(false);

		// controller.getGotoLineMenuItem().setDisable(false);

		controller.getPreferencesMenuItem().setDisable(false);

		controller.getSelectAllMenuItem().setDisable(false);
		controller.getSelectNoneMenuItem().setDisable(false);
			
		/*
		controller.getSelectAllNodesMenuItem().setDisable(false);
		controller.getSelectAllLabeledNodesMenuItem().setDisable(false);
		controller.getSelectAllBelowMenuItem().setDisable(false);
		controller.getSelectBracketsMenuItem().setDisable(false);
		controller.getInvertNodeSelectionMenuItem().setDisable(false);
		controller.getDeselectAllNodesMenuItem().setDisable(false);
		controller.getSelectAllEdgesMenuItem().setDisable(false);
		controller.getSelectAllLabeledEdgesMenuItem().setDisable(false);
		controller.getSelectAllEdgesBelowMenuItem().setDisable(false);
		controller.getInvertEdgeSelectionMenuItem().setDisable(false);
		controller.getDeselectEdgesMenuItem().setDisable(false);
		controller.getSelectFromPreviousMenuItem().setDisable(false);
		 */

		controller.getIncreaseFontSizeMenuItem().setDisable(false);
		controller.getDecreaseFontSizeMenuItem().setDisable(false);

		/*
		controller.getZoomInMenuItem().setDisable(false);
		controller.getZoomOutMenuItem().setDisable(false);
		*/


		// controller.getResetMenuItem().setDisable(false);
		// controller.getRotateLeftMenuItem().setDisable(false);
		// controller.getRotateRightMenuItem().setDisable(false);
		//controller.getFlipMenuItem().setDisable(false);
		// controller.getWrapTextMenuItem().setDisable(false);
		// controller.getFormatNodesMenuItem().setDisable(false);
		// controller.getLayoutLabelsMenuItem().setDisable(false);
		// controller.getSparseLabelsCheckMenuItem().setDisable(false);
		// controller.getShowScaleBarMenuItem().setDisable(false);

		controller.getUseDarkThemeMenuItem().selectedProperty().bindBidirectional(MainWindowManager.useDarkThemeProperty());
		controller.getUseDarkThemeMenuItem().setSelected(MainWindowManager.isUseDarkTheme());
		controller.getUseDarkThemeMenuItem().setDisable(false);

		BasicFX.setupFullScreenMenuSupport(mainWindow.getStage(), controller.getUseFullScreenMenuItem());

		controller.getFilterTaxaMenuItem().setDisable(false);
		controller.getFilterCharactersMenuItem().setDisable(false);
		controller.getFilterTreesMenuItem().setDisable(false);
		controller.getFilterSplitsMenuItem().setDisable(false);

		controller.getTraitsMenuItem().setDisable(false);

		controller.getUncorrectedPMenuItem().setDisable(false);

		controller.getLogDetMenuItem().setDisable(false);

		controller.getHky85MenuItem().setDisable(false);
		controller.getJukesCantorMenuItem().setDisable(false);
		controller.getK2pMenuItem().setDisable(false);
		controller.getK3stMenuItem().setDisable(false);
		controller.getF81MenuItem().setDisable(false);
		controller.getF84MenuItem().setDisable(false);
		controller.getProteinMLDistanceMenuItem().setDisable(false);
		controller.getGeneContentDistanceMenuItem().setDisable(false);
		controller.getNjMenuItem().setDisable(false);
		controller.getBioNJMenuItem().setDisable(false);
		controller.getUpgmaMenuItem().setDisable(false);
		controller.getBunemanTreeMenuItem().setDisable(false);

		controller.getSelectTreeMenuItem().setDisable(false);
		controller.getConsensusTreeMenuItem().setDisable(false);
		controller.getRootByOutgroupMenuItem().setDisable(false);
		controller.getRootByMidpointMenuItem().setDisable(false);
		controller.getTreeViewMenuItem().setDisable(false);
		controller.getTreeGridMenuItem().setDisable(false);
		controller.getTanglegramMenuItem().setDisable(false);
		controller.getNeighborNetMenuItem().setDisable(false);
		controller.getSplitDecompositionMenuItem().setDisable(false);
		controller.getParsimonySplitsMenuItem().setDisable(false);
		controller.getConsensusNetworkMenuItem().setDisable(false);
		controller.getFilteredSuperNetworkMenuItem().setDisable(false);
		controller.getMedianNetworkMenuItem().setDisable(false);
		controller.getMedianJoiningMenuItem().setDisable(false);
		controller.getMinSpanningNetworkMenuItem().setDisable(false);
		controller.getConsensusClusterNetworkMenuItem().setDisable(false);
		controller.getHybridizationNetworkMenuItem().setDisable(false);
		controller.getSplitsNetworkViewMenuItem().setDisable(false);
		controller.getHaplotypeNetworkViewMenuItem().setDisable(false);
		controller.getShow3DViewerMenuItem().setDisable(false);
		controller.getRelaxMenuItem().setDisable(false);

		controller.getPcoaMenuItem().setDisable(false);
		controller.getBrayCurtisMenuItem().setDisable(false);
		controller.getJsdMenuItem().setDisable(false);
		controller.getBootstrappingMenuItem().setDisable(false);

		controller.getShowBootStrapTreeMenuItem().setDisable(false);
		controller.getShowBootStrapNetworkMenuItem().setDisable(false);

		controller.getEstimateInvariableSitesMenuItem().setDisable(false);
		controller.getComputePhylogeneticDiversityMenuItem().setDisable(false);
		controller.getComputeDeltaScoreMenuItem().setDisable(false);


		controller.getShowWorkflowMenuItem().setDisable(false);

		controller.getShowMessageWindowMenuItem().setDisable(false);
		controller.getCheckForUpdatesMenuItem().setDisable(false);
		controller.getAboutMenuItem().setDisable(false);
	}
}
