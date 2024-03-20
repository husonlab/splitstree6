/*
 *  ComboBoxUtils.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.collections.ObservableSet;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

import java.util.function.Function;

/**
 * combo box utils
 * Daniel Huson, 12.2021
 */
public class ComboBoxUtils {
	public static <T> ListCell<T> createButtonCell(ObservableSet<T> disabledItems, Function<T, Node> itemNodeFunction) {
		return createButtonCell(disabledItems, itemNodeFunction, false);
	}

	/**
	 * create list cell for diagram combo box
	 */
	public static <T> ListCell<T> createButtonCell(ObservableSet<T> disabledItems, Function<T, Node> itemNodeFunction, boolean flip) {
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
					if (itemNodeFunction != null && itemNodeFunction.apply(item) != null) {
						var node = itemNodeFunction.apply(item);
						if (flip)
							node.setScaleX(-node.getScaleX());
						setGraphic(node);
						if (disabledItems != null) {
							node.disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
							disableProperty().bind(Bindings.createBooleanBinding(() -> disabledItems.contains(item), disabledItems));
							node.opacityProperty().bind(new When(node.disableProperty()).then(0.4).otherwise(1.0));
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

	public static <T> Callback<ListView<T>, ListCell<T>> createCellFactory(ObservableSet<T> disabled, Function<T, Node> itemNodeFunction) {
		return p -> createButtonCell(disabled, itemNodeFunction, false);
	}

	/**
	 * creates the callback method for diagram combo box
	 */
	public static <T> Callback<ListView<T>, ListCell<T>> createCellFactory(ObservableSet<T> disabled, Function<T, Node> itemNodeFunction, boolean flip) {
		return p -> createButtonCell(disabled, itemNodeFunction, flip);
	}
}
