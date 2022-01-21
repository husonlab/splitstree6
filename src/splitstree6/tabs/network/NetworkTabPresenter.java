/*
 * NetworkTabPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.network;

import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

public class NetworkTabPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final NetworkTab tab;

	public NetworkTabPresenter(MainWindow mainWindow, NetworkTab tab) {
		this.mainWindow = mainWindow;
		this.tab = tab;
	}

	public void setupMenuItems() {
		var controller = mainWindow.getController();

		controller.getCutMenuItem().setOnAction(null);
		controller.getCopyMenuItem().setOnAction(null);

		controller.getCopyImageMenuItem().setOnAction(null);

		controller.getPasteMenuItem().setOnAction(null);

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		controller.getReplaceMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(null);
		controller.getSelectNoneMenuItem().setOnAction(null);

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

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		controller.getZoomInMenuItem().setOnAction(null);
		controller.getZoomOutMenuItem().setOnAction(null);


		controller.getResetMenuItem().setOnAction(null);
		controller.getRotateLeftMenuItem().setOnAction(null);
		controller.getRotateRightMenuItem().setOnAction(null);
		controller.getFlipMenuItem().setOnAction(null);
		controller.getFormatNodesMenuItem().setOnAction(null);
		controller.getLayoutLabelsMenuItem().setOnAction(null);
		controller.getSparseLabelsCheckMenuItem().setOnAction(null);
		controller.getShowScaleBarMenuItem().setOnAction(null);
	}
}
