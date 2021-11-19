/*
 *  ConsoleView.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import jloda.fx.undo.UndoManager;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.textdisplay.TextDisplayTab;
import splitstree6.window.MainWindow;

/**
 * console view
 * Daniel Huson, 11.2021
 */
public class ConsoleView implements IView {
	private final TextDisplayTab textDisplayTab;
	private final Node root;

	public ConsoleView(MainWindow mainWindow, String name) {
		textDisplayTab = new TextDisplayTab(mainWindow, name, false, false);
		root = textDisplayTab.getContent();
		textDisplayTab.setContent(null);
	}

	@Override
	public String getName() {
		return "ConsoleView";
	}

	@Override
	public Node getRoot() {
		return root;
	}

	@Override
	public void setupMenuItems() {
		textDisplayTab.getPresenter().setupMenuItems();
	}

	@Override
	public int size() {
		return textDisplayTab.getController().getCodeArea().getLength();
	}

	public void setText(String text) {
		textDisplayTab.getController().getCodeArea().replaceText(text);
	}

	@Override
	public UndoManager getUndoManager() {
		return textDisplayTab.getUndoManager();
	}

	@Override
	public ObservableValue<Boolean> emptyProperty() {
		return textDisplayTab.emptyProperty();
	}

	@Override
	public Node getImageNode() {
		return textDisplayTab.getImageNode();
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return null;
	}
}
