/*
 *  MenusPresenter.java Copyright (C) 2021 Daniel H. Huson
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

import jloda.fx.window.MainWindowManager;
import jloda.fx.window.WindowGeometry;
import jloda.util.ProgramProperties;
import splitstree6.dialog.SaveBeforeClosingDialog;
import splitstree6.window.MainWindow;


/**
 * menus presenter
 * Daniel Huson, 10.2021
 */
public class MenusPresenter {
	public MenusPresenter(MainWindow mainWindow) {
		var controller = mainWindow.getController();

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

		controller.getNewMenuItem().setOnAction(e -> MainWindowManager.getInstance().createAndShowWindow(false));

	}
}
