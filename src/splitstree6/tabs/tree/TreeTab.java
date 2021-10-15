/*
 *  TreeTab.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.tree;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

public class TreeTab extends Tab implements IDisplayTab {
	private final TreeTabController controller;
	private final TreeTabPresenter presenter;

	private final UndoManager undoManager = new UndoManager();
	private final MainWindow mainWindow;
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	/**
	 * constructor
	 */
	public TreeTab(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		presenter = new TreeTabPresenter(mainWindow, this);

		var extendedFXMLLoader = new ExtendedFXMLLoader<TreeTabController>(this.getClass());
		controller = extendedFXMLLoader.getController();

		//empty.bind();

		setText("Tree");
		setClosable(false);
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty isEmptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return null;
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	public TreeTabController getController() {
		return controller;
	}
}

