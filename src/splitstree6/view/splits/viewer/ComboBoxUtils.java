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

package splitstree6.view.splits.viewer;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.collections.ObservableSet;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import jloda.fx.util.ResourceManagerFX;

import java.util.function.Function;

/**
 * combo box utils
 * Daniel Huson, 12.2021
 */
public class ComboBoxUtils {
	public static <T> ListCell<T> createButtonCell(ObservableSet<T> disabledItems, Function<T, String> itemImageMap) {
		return createButtonCell(disabledItems, itemImageMap, false);
	}

	/**
	 * create list cell for diagram combo box
	 */
	public static <T> ListCell<T> createButtonCell(ObservableSet<T> disabledItems, Function<T, String> itemImageMap, boolean flip) {
		return new ListCell<>() {
			{
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}

			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);

				if (item == null || empty) {
					setGraphic(null);
				} else {
					if (itemImageMap != null && itemImageMap.apply(item) != null) {
						var imageView = ResourceManagerFX.getIconAsImageView(itemImageMap.apply(item), 16);
						if (flip)
							imageView.setScaleX(-imageView.getScaleX());
						setGraphic(imageView);
						if (disabledItems != null) {
							imageView.disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
							disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
							imageView.opacityProperty().bind(new When(imageView.disableProperty()).then(0.4).otherwise(1.0));
						}
					} else {
						var label = new Label(item.toString());
						setGraphic(label);
						if (disabledItems != null) {
							label.disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
							disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
						}
					}
				}
			}
		};
	}

	public static <T> Callback<ListView<T>, ListCell<T>> createCellFactory(ObservableSet<T> disabled, Function<T, String> itemImageMap) {
		return p -> createButtonCell(disabled, itemImageMap, false);
	}

	/**
	 * creates the callback method for diagram combo box
	 */
	public static <T> Callback<ListView<T>, ListCell<T>> createCellFactory(ObservableSet<T> disabled, Function<T, String> itemImageMap, boolean flip) {
		return p -> createButtonCell(disabled, itemImageMap, flip);
	}
}
