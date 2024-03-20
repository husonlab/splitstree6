/*
 *  TreeFilterTab.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tabs.algorithms.treefilter;

import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.algorithms.trees.trees2trees.TreesFilter;
import splitstree6.data.TreesBlock;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.algorithms.AlgorithmTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

/**
 * display the trees filter tab
 * Daniel Huson, 2.2022
 */
public class TreeFilterTab extends AlgorithmTab implements IDisplayTab {
	private final TreeFilterTabController controller;
	private final TreeFilterTabPresenter presenter;

	/**
	 * constructor
	 */
	public TreeFilterTab(MainWindow mainWindow, AlgorithmNode<TreesBlock, TreesBlock> treeFilterNode) {
		super(mainWindow, treeFilterNode);

		var treesFilter = (TreesFilter) treeFilterNode.getAlgorithm();

		var loader = new ExtendedFXMLLoader<TreeFilterTabController>(TreeFilterTabController.class);
		controller = loader.getController();
		presenter = new TreeFilterTabPresenter(mainWindow, this, treeFilterNode, treesFilter);

		getController().getMainPane().getChildren().add(controller.getAnchorPane());
		controller.getAnchorPane().prefWidthProperty().bind(getController().getMainPane().widthProperty());
		controller.getAnchorPane().prefHeightProperty().bind(getController().getMainPane().heightProperty());
	}

	public TreeFilterTabController getTreeFilterTabController() {
		return controller;
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}


}
