/*
 * AlgorithmTab.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tabs.algorithms;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.algorithms.AlgorithmList;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.interfaces.DoNotLoadThisAlgorithm;

/**
 * algorithm tab, Daniel Huson, 2021
 */
public class AlgorithmTab extends Tab implements IDisplayTab {
	private final MainWindow mainWindow;
	private final AlgorithmTabController controller;
	private final AlgorithmTabPresenter presenter;
	private final UndoManager undoManager = new UndoManager();

	private final AlgorithmNode algorithmNode;
	private final ObjectProperty<Algorithm> algorithm = new SimpleObjectProperty<>(this, "algorithm");

	public AlgorithmTab(MainWindow mainWindow, AlgorithmNode algorithmNode) {
		this.mainWindow = mainWindow;
		this.algorithmNode = algorithmNode;

		this.algorithm.set(algorithmNode.getAlgorithm());

		var loader = new ExtendedFXMLLoader<AlgorithmTabController>(AlgorithmTab.class);
		controller = loader.getController();
		setContent(loader.getRoot());
		setClosable(true);

		presenter = new AlgorithmTabPresenter(mainWindow, this);

		for (var algorithm : AlgorithmList.list()) {
			if (!(algorithm instanceof DoNotLoadThisAlgorithm)) {
				if (algorithm.getFromClass() == getAlgorithm().getFromClass()
					&& algorithm.getToClass() == getAlgorithm().getToClass() && !(algorithm instanceof DataTaxaFilter))
					controller.getAlgorithmCBox().getItems().add(algorithm);
			}
		}
		controller.getAlgorithmCBox().setValue(getAlgorithm());
	}

	public AlgorithmNode getAlgorithmNode() {
		return algorithmNode;
	}

	public Algorithm getAlgorithm() {
		return algorithm.get();
	}

	public void setAlgorithm(Algorithm algorithm) {
		this.algorithm.set(algorithm);
	}

	public ObjectProperty<Algorithm> algorithmProperty() {
		return algorithm;
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return null;
	}

	@Override
	public Node getMainNode() {
		return controller.getMainPane();
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}

	public AlgorithmTabController getController() {
		return controller;
	}
}
