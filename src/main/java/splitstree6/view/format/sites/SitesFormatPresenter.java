/*
 *  SitesFormatPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.sites;

import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import jloda.fx.undo.UndoManager;

/**
 * the sites format presenter
 * Daniel Huson, 3.2022
 */
public class SitesFormatPresenter {

	public SitesFormatPresenter(SitesFormat sitesFormat, UndoManager undoManager, SitesFormatController controller) {
		sitesFormat.optionSitesStyleProperty().addListener(e -> sitesFormat.updateEdges());

		var styleToggleGroup = new ToggleGroup();
		for (var style : SitesStyle.values()) {
			if (style.name().equals("None"))
				controller.getShowMenuButton().getItems().add(new SeparatorMenuItem());
			var menuItem = new RadioMenuItem(style.name());
			menuItem.setUserData(style);
			controller.getShowMenuButton().getItems().add(menuItem);
			styleToggleGroup.getToggles().add(menuItem);

		}
		styleToggleGroup.selectedToggleProperty().addListener((v, o, n) -> {
			var style = (SitesStyle) n.getUserData();
			sitesFormat.setOptionSitesStyle(style);
			controller.getShowMenuButton().setText(style.name());
		});
		sitesFormat.optionSitesStyleProperty().addListener((v, o, n) -> {
			for (var toggle : styleToggleGroup.getToggles()) {
				if (toggle.getUserData() != null && toggle.getUserData().equals(n)) {
					toggle.setSelected(true);
					break;
				}
			}
		});
		var style = sitesFormat.getOptionSitesStyle();
		sitesFormat.setOptionSitesStyle(SitesStyle.None);
		sitesFormat.setOptionSitesStyle(style);

		sitesFormat.optionSitesStyleProperty().addListener((v, o, n) -> undoManager.add("sites format", () -> sitesFormat.setOptionSitesStyle(o), () -> sitesFormat.setOptionSitesStyle(n)));
	}
}
