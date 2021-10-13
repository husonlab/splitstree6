/*
 *  MainWindowPresenter.java Copyright (C) 2021 Daniel H. Huson
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


import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.window.MainWindow;

public class MainWindowPresenter {
	private final MainWindow mainWindow;
	private final WorkflowTabPresenter workflowTabPresenter;

	public MainWindowPresenter(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		var controller = mainWindow.getController();

		this.workflowTabPresenter = new WorkflowTabPresenter(mainWindow);

		controller.getMainTabPane().getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			disableAllMenuItems();
			CommonMenuPresenter.apply(mainWindow);
			if (n instanceof WorkflowTab workflowTab) {
				workflowTabPresenter.apply(workflowTab);
			}
			enableAllMenuItemsWithDefinedAction();
		});
	}

	public void disableAllMenuItems() {
		for (var menu : mainWindow.getController().getMenuBar().getMenus()) {
			disableAllMenuItems(menu);
		}

	}

	public void disableAllMenuItems(Menu menu) {
		if (!menu.getText().equals("Open Recent") && !menu.getText().equals("Window") && !menu.getText().equals("Help"))
			for (var item : menu.getItems()) {
				if (item instanceof Menu other) {
					disableAllMenuItems(other);
				} else if (!(item instanceof SeparatorMenuItem)) {
					item.setOnAction(null);
					item.disableProperty().unbind();
					item.setDisable(true);
				}
			}
	}

	public void enableAllMenuItemsWithDefinedAction() {
		for (var menu : mainWindow.getController().getMenuBar().getMenus()) {
			enableAllMenuItemsWithDefinedAction(menu);
		}
	}

	public void enableAllMenuItemsWithDefinedAction(Menu menu) {
		if (!menu.getText().equals("Open Recent") && !menu.getText().equals("Window") && !menu.getText().equals("Help"))
			for (var item : menu.getItems()) {
				if (item instanceof Menu other) {
					enableAllMenuItemsWithDefinedAction(other);
				} else if (!(item instanceof SeparatorMenuItem)) {
					if (item.getOnAction() != null && !item.disableProperty().isBound()) {
						item.setDisable(false);
					}
				}
			}
	}
}
