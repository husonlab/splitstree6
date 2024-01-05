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

package splitstree6.view.findreplace;

import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.Region;
import jloda.fx.find.FindToolBar;
import jloda.fx.icons.MaterialIcons;
import splitstree6.main.SplitsTree6;

import java.util.ArrayList;

/**
 * find and replace utils
 * Daniel Huson, 12.2023
 */
public class FindReplaceUtils {
	private static final ArrayList<String> searchTerms = new ArrayList<>();
	private static final ArrayList<String> replaceTerms = new ArrayList<>();

	/**
	 * additional setup of the find toolbar
	 *
	 * @param findToolBar
	 */
	public static void additionalSetup(FindToolBar findToolBar) {
		if (SplitsTree6.isDesktop())
			for (var which = 0; which <= 1; which++) {
				var items = (which == 0 ? findToolBar.getController().getToolBar().getItems() : findToolBar.getController().getReplaceToolBar().getItems());
				for (var i = 0; i < items.size(); i++) {
					var item = items.get(i);
					if (item instanceof Separator) {
						var region = new Region();
						region.setPrefWidth(1);
						region.setMinWidth(Region.USE_PREF_SIZE);
						region.setMaxWidth(Region.USE_PREF_SIZE);
						region.setStyle("-fx-background-color: transparent;");
						items.set(i, region);
					} else if (item instanceof Button) {
						((Button) item).getStylesheets().add(MaterialIcons.getInstance().getStyleSheet());
					}
				}
			}
		var findFromFileButton = findToolBar.getController().getFindFromFileButton();
		if (SplitsTree6.isDesktop())
			MaterialIcons.setIcon(findFromFileButton, "upload", "-fx-font-size: 12;", true);
		else
			findToolBar.getController().getToolBar().getItems().remove(findFromFileButton);
		findToolBar.getController().getAnchorPane().getChildren().remove(findToolBar.getController().getCloseButton());

		findToolBar.getController().getSearchComboBox().getItems().addAll(searchTerms);
		findToolBar.getController().getSearchComboBox().itemsProperty().addListener(e -> {
			searchTerms.clear();
			searchTerms.addAll(findToolBar.getController().getSearchComboBox().getItems());
		});

		findToolBar.getController().getReplaceComboBox().getItems().addAll(replaceTerms);
		findToolBar.getController().getReplaceComboBox().itemsProperty().addListener(e -> {
			replaceTerms.clear();
			replaceTerms.addAll(findToolBar.getController().getReplaceComboBox().getItems());
		});
	}
}
