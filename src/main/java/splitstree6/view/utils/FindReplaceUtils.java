/*
 *  FindReplaceUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.utils;

import javafx.application.Platform;
import javafx.scene.control.ToggleButton;
import jloda.fx.find.FindToolBar;
import jloda.fx.icons.MaterialIcons;

/**
 * utils for find/replace toggle button
 * Daniel Huson, 1.2023
 */
public class FindReplaceUtils {
	public static void setup(FindToolBar findToolBar, ToggleButton showFindToggleButton, boolean editiable) {
		if (editiable) {
			showFindToggleButton.setOnAction(e -> {
				if (!findToolBar.isShowFindToolBar()) {
					findToolBar.setShowFindToolBar(true);
					showFindToggleButton.setSelected(true);
				} else if (!findToolBar.isShowReplaceToolBar()) {
					findToolBar.setShowReplaceToolBar(true);
					showFindToggleButton.setSelected(true);
				} else {
					findToolBar.setShowFindToolBar(false);
					findToolBar.setShowReplaceToolBar(false);
					showFindToggleButton.setSelected(false);
				}
			});
		} else {
			findToolBar.showFindToolBarProperty().addListener(e -> {
				if (findToolBar.isShowFindToolBar() && !findToolBar.isShowReplaceToolBar())
					MaterialIcons.setIcon(showFindToggleButton, "find_replace");
				else
					MaterialIcons.setIcon(showFindToggleButton, "search");
			});
			showFindToggleButton.setOnAction(e -> {
				if (!findToolBar.isShowFindToolBar()) {
					findToolBar.setShowFindToolBar(true);
				} else {
					findToolBar.setShowFindToolBar(false);
					findToolBar.setShowReplaceToolBar(false);
				}
			});
		}
		findToolBar.showFindToolBarProperty().addListener((v, o, n) -> {
			if (n && editiable)
				MaterialIcons.setIcon(showFindToggleButton, "find_replace");
			else
				MaterialIcons.setIcon(showFindToggleButton, "search");
			Platform.runLater(() -> showFindToggleButton.setSelected(findToolBar.isShowFindToolBar() || findToolBar.isShowReplaceToolBar()));
		});
		findToolBar.showReplaceToolBarProperty().addListener((v, o, n) -> {
			MaterialIcons.setIcon(showFindToggleButton, "search");
			Platform.runLater(() -> showFindToggleButton.setSelected(findToolBar.isShowFindToolBar() || findToolBar.isShowReplaceToolBar()));
		});
	}
}
