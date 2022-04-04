/*
 * TraitsPiePresenter.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.view.format.traits;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckMenuItem;
import jloda.fx.undo.UndoManager;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.window.MainWindow;

public class TraitsPiePresenter {
	private final InvalidationListener traitsBlockListener;
	private boolean inUpdatingDefaults = false;

	private final ObservableList<CheckMenuItem> traitMenuItems = FXCollections.observableArrayList();

	public TraitsPiePresenter(MainWindow mainWindow, TraitsPie traitsPie, TraitsPieController controller, UndoManager undoManager) {
		controller.getMaxSizeField().setText(String.valueOf(traitsPie.getOptionTraitSize()));


		traitsPie.optionTraitLegendProperty().bindBidirectional(controller.getLegendCBox().selectedProperty());
		traitsPie.optionTraitSizeProperty().addListener((v, o, n) -> controller.getMaxSizeField().setText(String.valueOf(n.intValue())));
		controller.getMaxSizeField().textProperty().addListener((v, o, n) -> traitsPie.setOptionTraitSize(NumberUtils.parseInt(n)));

		traitsPie.getLegend().visibleProperty().bindBidirectional(traitsPie.optionTraitLegendProperty());

		traitsPie.optionActiveTraitsProperty().addListener(e ->
		{
			traitMenuItems.forEach(m -> m.setSelected(traitsPie.isTraitActive(m.getText())));
			traitsPie.updateNodes();
		});

		traitsBlockListener = e -> {
			var traitsBlock = traitsPie.getTraitsBlock();
			if (traitsBlock != null) {
				if (traitsBlock.getNTraits() == 0) {
					controller.getvBox().setDisable(true);
					controller.getShowMenuButton().getItems().removeAll(traitMenuItems);
					traitMenuItems.clear();
				} else {
					controller.getvBox().setDisable(false);
					for (var trait = 1; trait <= traitsBlock.getNTraits(); trait++) {
						var label = traitsBlock.getTraitLabel(trait);
						var menuItem = new CheckMenuItem(label);
						menuItem.setSelected(true);
						menuItem.selectedProperty().addListener((b, o, n) -> {
							var oldState = traitsPie.getOptionActiveTraits().clone();
							var newState = StringUtils.addOrRemove(oldState, label, menuItem.isSelected());
							undoManager.doAndAdd("activate trait", () -> traitsPie.setOptionActiveTraits(oldState), () -> traitsPie.setOptionActiveTraits(newState));
						});
						traitMenuItems.add(menuItem);
					}
					controller.getShowMenuButton().getItems().addAll(traitMenuItems);
				}
			}
		};
		controller.getShowAllMenuItem().setOnAction(e -> {
			var oldState = traitsPie.getOptionActiveTraits().clone();
			var newState = traitsPie.getTraitsBlock().getTraitLabels().toArray(new String[0]);
			undoManager.doAndAdd("activate all traits", () -> traitsPie.setOptionActiveTraits(oldState), () -> traitsPie.setOptionActiveTraits(newState));
		});
		controller.getShowNoneMenuItem().setOnAction(e -> {
			var oldState = traitsPie.getOptionActiveTraits().clone();
			var newState = new String[0];
			undoManager.doAndAdd("deactivate all traits", () -> traitsPie.setOptionActiveTraits(oldState), () -> traitsPie.setOptionActiveTraits(newState));
		});

		traitsPie.traitsBlockProperty().addListener(new WeakInvalidationListener(traitsBlockListener));
		traitsBlockListener.invalidated(null);

		traitsPie.optionActiveTraitsProperty().addListener(e -> {
			if (traitsPie.isAllTraitsActive())
				controller.getShowMenuButton().setText("All");
			else if (traitsPie.isNoneTraitsActive())
				controller.getShowMenuButton().setText("None");
			else
				controller.getShowMenuButton().setText("Some");
		});

		traitsPie.optionTraitSizeProperty().addListener((v, o, n) -> undoManager.add("traits node size", traitsPie.optionTraitSizeProperty(), o, n));
		traitsPie.optionTraitLegendProperty().addListener((v, o, n) -> undoManager.add("show legend", traitsPie.optionTraitLegendProperty(), o, n));

		traitsPie.optionTraitSizeProperty().addListener(e -> traitsPie.updateNodes());
		traitsPie.optionTraitLegendProperty().addListener(e -> traitsPie.updateNodes());
	}
}
