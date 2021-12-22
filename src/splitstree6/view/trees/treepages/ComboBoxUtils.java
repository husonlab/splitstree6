/*
 *  ComboBoxUtils.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.collections.ObservableSet;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.util.Callback;
import jloda.fx.util.ResourceManagerFX;
import jloda.util.StringUtils;
import splitstree6.view.trees.layout.ComputeTreeLayout;


public class ComboBoxUtils {
	/**
	 * create list cell for diagram combo box
	 */
	public static ListCell<ComputeTreeLayout.Diagram> createDiagramComboBoxListCell(boolean horizontalFlip, ObservableSet<ComputeTreeLayout.Diagram> disabledItems) {
		return new ListCell<>() {
			{
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}

			@Override
			protected void updateItem(ComputeTreeLayout.Diagram item, boolean empty) {
				super.updateItem(item, empty);

				if (item == null || empty) {
					setGraphic(null);
				} else {
					var imageView = ResourceManagerFX.getIconAsImageView(item.name() + "16.gif", 16);
					if (horizontalFlip)
						imageView.setScaleX(-imageView.getScaleX());
					setGraphic(imageView);
					imageView.disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
					disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
					imageView.opacityProperty().bind(new When(imageView.disableProperty()).then(0.4).otherwise(1.0));
				}
			}
		};
	}

	/**
	 * creates the callback method for diagram combo box
	 */
	public static Callback<ListView<ComputeTreeLayout.Diagram>, ListCell<ComputeTreeLayout.Diagram>> createDiagramComboxBoxCallback(boolean horizontalFlip, ObservableSet<ComputeTreeLayout.Diagram> disabled) {
		return p -> createDiagramComboBoxListCell(horizontalFlip, disabled);
	}

	/**
	 * create list cell for root side combo box
	 */
	public static ListCell<TreePane.Orientation> createOrientationComboBoxListCell() {
		return new ListCell<>() {
			{
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}

			@Override
			protected void updateItem(TreePane.Orientation item, boolean empty) {
				super.updateItem(item, empty);

				if (item == null || empty) {
					setGraphic(null);
				} else {
					var graphic = ResourceManagerFX.getIconAsImageView(item.name() + ".png", 16);
					setGraphic(graphic);
					Tooltip.install(graphic, new Tooltip(StringUtils.fromCamelCase(item.name())));
				}
			}
		};
	}

	/**
	 * creates the callback method for diagraom combo box
	 */
	public static Callback<ListView<TreePane.Orientation>, ListCell<TreePane.Orientation>> createOrientationComboBoxCallback() {
		return p -> createOrientationComboBoxListCell();
	}
}
