/*
 *  NetworkTabPresenter.java Copyright (C) 2021 Daniel H. Huson
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

import splitstree6.tabs.IDisplayTab;
import splitstree6.window.MainWindow;

public class NetworkTabPresenter {
	private final MainWindow mainWindow;

	public NetworkTabPresenter(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
	}

	public void apply(IDisplayTab networkTab) {
		var controller = mainWindow.getController();

		controller.getPrintMenuItem().setOnAction(e -> {
		});

		controller.getUndoMenuItem().setOnAction(e -> networkTab.getUndoManager().undo());
		controller.getUndoMenuItem().disableProperty().bind(networkTab.getUndoManager().undoableProperty().not());

		controller.getRedoMenuItem().setOnAction(e -> networkTab.getUndoManager().redo());
		controller.getRedoMenuItem().disableProperty().bind(networkTab.getUndoManager().redoableProperty().not());

		controller.getCutMenuItem().setDisable(false);
		controller.getCopyMenuItem().setDisable(false);

		controller.getCopyImageMenuItem().setDisable(false);

		controller.getPasteMenuItem().setDisable(false);


		controller.getFindMenuItem().setDisable(false);
		controller.getFindAgainMenuItem().setDisable(false);

		controller.getReplaceMenuItem().setDisable(false);


		controller.getSelectAllMenuItem().setDisable(false);
		controller.getSelectNoneMenuItem().setDisable(false);

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

		controller.getIncreaseFontSizeMenuItem().setDisable(false);
		controller.getDecreaseFontSizeMenuItem().setDisable(false);

		controller.getZoomInMenuItem().setDisable(false);
		controller.getZoomOutMenuItem().setDisable(false);


		controller.getResetMenuItem().setDisable(false);
		controller.getRotateLeftMenuItem().setDisable(false);
		controller.getRotateRightMenuItem().setDisable(false);
		controller.getFlipMenuItem().setDisable(false);
		controller.getWrapTextMenuItem().setDisable(false);
		controller.getFormatNodesMenuItem().setDisable(false);
		controller.getLayoutLabelsMenuItem().setDisable(false);
		controller.getSparseLabelsCheckMenuItem().setDisable(false);
		controller.getShowScaleBarMenuItem().setDisable(false);
	}
}
