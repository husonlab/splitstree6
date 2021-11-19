/*
 *  AlgorithmTab.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.algorithms;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.util.PluginClassLoader;
import splitstree6.tabs.IDisplayTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;

public class AlgorithmTab extends Tab implements IDisplayTab {
	private final MainWindow mainWindow;
	private final AlgorithmTabController controller;
	private final AlgorithmTabPresenter presenter;

	private final AlgorithmNode algorithmNode;

	public AlgorithmTab(MainWindow mainWindow, AlgorithmNode algorithmNode) {
		this.mainWindow = mainWindow;
		this.algorithmNode = algorithmNode;

		var loader = new ExtendedFXMLLoader<AlgorithmTabController>(AlgorithmTab.class);
		controller = loader.getController();
		setContent(loader.getRoot());
		setClosable(true);

		presenter = new AlgorithmTabPresenter(this);

		for (var algorithm : PluginClassLoader.getInstances(Algorithm.class, "splitstree6.algorithms")) {
			if (algorithm.getFromClass() == algorithmNode.getAlgorithm().getFromClass()
				&& algorithm.getToClass() == algorithmNode.getAlgorithm().getToClass())
				controller.getAlgorithmCBox().getItems().add(algorithm);
		}

		controller.getAlgorithmCBox().setValue(algorithmNode.getAlgorithm());
	}


	public AlgorithmNode getAlgorithmNode() {
		return algorithmNode;
	}

	@Override
	public UndoManager getUndoManager() {
		return null;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return null;
	}

	@Override
	public Node getImageNode() {
		return controller.getMainPane();
	}

	@Override
	public AlgorithmTabPresenter getPresenter() {
		return presenter;
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}

	public AlgorithmTabController getController() {
		return controller;
	}
}
