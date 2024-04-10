/*
 *  TraitsFormatPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.traits;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.FuzzyBoolean;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.window.MainWindow;

public class TraitsFormatPresenter {
	private final InvalidationListener traitsBlockListener;
	private boolean inUpdatingDefaults = false;

	private final ObservableList<CheckMenuItem> traitMenuItems = FXCollections.observableArrayList();

	public TraitsFormatPresenter(MainWindow mainWindow, TraitsFormat format, TraitsFormatController controller, UndoManager undoManager) {
		controller.getMaxSizeField().setText(String.valueOf(format.getOptionTraitSize()));

		format.optionTraitSizeProperty().addListener((v, o, n) -> controller.getMaxSizeField().setText(String.valueOf(n.intValue())));
		controller.getMaxSizeField().textProperty().addListener((v, o, n) -> format.setOptionTraitSize(NumberUtils.parseInt(n)));

		FuzzyBoolean.setupCheckBox(controller.getLegendCBox(), format.getLegend().showProperty());

		format.optionActiveTraitsProperty().addListener(e -> {
			traitMenuItems.forEach(m -> m.setSelected(format.isTraitActive(m.getText())));
			format.updateNodes();
		});

		traitsBlockListener = e -> {
			var traitsBlock = format.getTraitsBlock();
			if (traitsBlock != null) {
				if (traitsBlock.getNumberNumericalTraits() == 0) {
					controller.getTitledPane().setDisable(true);
					controller.getShowMenuButton().getItems().removeAll(traitMenuItems);
					traitMenuItems.clear();
				} else {
					traitMenuItems.clear();
					controller.getTitledPane().setDisable(false);
					for (var label : traitsBlock.getNumericalTraitLabels()) {
						var menuItem = new CheckMenuItem(label);
						menuItem.setSelected(true);
						menuItem.selectedProperty().addListener((b, o, n) -> {
							var oldState = format.getOptionActiveTraits().clone();
							var newState = StringUtils.addOrRemove(oldState, label, menuItem.isSelected());
							undoManager.doAndAdd("activate trait", () -> format.setOptionActiveTraits(oldState), () -> format.setOptionActiveTraits(newState));
						});
						traitMenuItems.add(menuItem);
					}
					controller.getShowMenuButton().getItems().setAll(controller.getShowAllMenuItem(), controller.getShowNoneMenuItem(), new SeparatorMenuItem());
					controller.getShowMenuButton().getItems().addAll(traitMenuItems);
				}
			}
		};
		controller.getShowAllMenuItem().setOnAction(e -> {
			var oldState = format.getOptionActiveTraits().clone();
			var newState = format.getTraitsBlock().getTraitLabels().toArray(new String[0]);
			undoManager.doAndAdd("activate all traits", () -> format.setOptionActiveTraits(oldState), () -> format.setOptionActiveTraits(newState));
		});
		controller.getShowNoneMenuItem().setOnAction(e -> {
			var oldState = format.getOptionActiveTraits().clone();
			var newState = new String[0];
			undoManager.doAndAdd("deactivate all traits", () -> format.setOptionActiveTraits(oldState), () -> format.setOptionActiveTraits(newState));
		});

		format.traitsBlockProperty().addListener(new WeakInvalidationListener(traitsBlockListener));
		traitsBlockListener.invalidated(null);

		format.optionActiveTraitsProperty().addListener(e -> {
			if (format.isAllTraitsActive())
				controller.getShowMenuButton().setText("All");
			else if (format.isNoneTraitsActive())
				controller.getShowMenuButton().setText("None");
			else
				controller.getShowMenuButton().setText("Some");
		});

		format.optionTraitSizeProperty().addListener((v, o, n) -> undoManager.add("traits node size", format.optionTraitSizeProperty(), o, n));
		format.optionTraitLegendProperty().addListener((v, o, n) -> undoManager.add("show legend", format.optionTraitLegendProperty(), o, n));

		format.optionTraitSizeProperty().addListener(e -> format.updateNodes());
		format.optionTraitLegendProperty().addListener(e -> format.updateNodes());
	}
}
