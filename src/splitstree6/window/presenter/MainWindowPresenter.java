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


import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import splitstree6.window.MainWindow;

public class MainWindowPresenter {
	private final MenusPresenter menusPresenter;
	private final SplitPanePresenter splitPanePresenter;

	public MainWindowPresenter(MainWindow mainWindow) {
		var controller = mainWindow.getController();

		this.menusPresenter = new MenusPresenter(mainWindow);
		this.splitPanePresenter = new SplitPanePresenter(controller);

		controller.getUseDarkThemeMenuItem().selectedProperty().bindBidirectional(MainWindowManager.useDarkThemeProperty());
		controller.getUseDarkThemeMenuItem().setSelected(MainWindowManager.isUseDarkTheme());

		BasicFX.setupFullScreenMenuSupport(mainWindow.getStage(), controller.getUseFullScreenMenuItem());
	}

	public MenusPresenter getMenusPresenter() {
		return menusPresenter;
	}

	public SplitPanePresenter getSplitPanePresenter() {
		return splitPanePresenter;
	}
}
