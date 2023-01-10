/*
 *  SelectTraitsController.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.format.selecttraits;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import jloda.fx.selection.SelectionModel;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.data.parts.Taxon;

import java.util.TreeSet;

public class SelectTraitsPresenter {
	private final InvalidationListener traitsBlockListener;
	private final InvalidationListener selectionListener;

	private final ObservableList<RadioMenuItem> traitMenuItems = FXCollections.observableArrayList();
	private final ToggleGroup traitMenuToggleGroup = new ToggleGroup();


	public SelectTraitsPresenter(SelectTraits selectTraits) {
		var controller = selectTraits.getController();

		traitMenuItems.addListener((ListChangeListener<? super RadioMenuItem>) e -> {
			while (e.next()) {
				traitMenuToggleGroup.getToggles().addAll(e.getAddedSubList());
				traitMenuToggleGroup.getToggles().removeAll(e.getRemoved());
			}
		});

		traitsBlockListener = e -> {
			var traitsBlock = selectTraits.getTraitsBlock();
			controller.getTraitMenuButton().setText("Trait");
			if (traitsBlock != null) {
				if (traitsBlock.getNTraits() == 0) {
					controller.getTitledPane().setDisable(true);
					controller.getTraitMenuButton().getItems().removeAll(traitMenuItems);
					traitMenuItems.clear();
				} else {
					traitMenuItems.clear();
					controller.getTitledPane().setDisable(false);
					for (var label : traitsBlock.getTraitLabels()) {
						var menuItem = new RadioMenuItem(label);

						menuItem.selectedProperty().addListener((b, o, n) -> {
							controller.getTraitMenuButton().setText(menuItem.getText());
							updateValuesMenu(selectTraits.getTaxonSelectionModel(), selectTraits.getWorkingTaxa(), traitsBlock, label, controller);
						});
						traitMenuItems.add(menuItem);
					}
					controller.getTraitMenuButton().getItems().addAll(traitMenuItems);
				}
			}
		};
		selectTraits.traitsBlockProperty().addListener(traitsBlockListener);

		controller.getAllValuesMenuItem().setOnAction(e -> selectTraits.getTaxonSelectionModel().selectAll(selectTraits.getWorkingTaxa().getTaxa()));
		controller.getNoneValueMenuItem().setOnAction(e -> selectTraits.getTaxonSelectionModel().clearSelection());

		selectionListener = e -> controller.getTraitValuesMenuButton().setText("Values");

		selectTraits.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));

		traitsBlockListener.invalidated(null);
	}

	private void updateValuesMenu(SelectionModel<Taxon> taxonSelectionModel, TaxaBlock taxaBlock, TraitsBlock traitsBlock, String traitLabel, SelectTraitsController controller) {
		controller.getTraitValuesMenuButton().getItems().setAll(controller.getAllValuesMenuItem(), controller.getNoneValueMenuItem(), new SeparatorMenuItem());

		controller.getTraitValuesMenuButton().setText("Values");

		var tr = traitsBlock.getTraitId(traitLabel);
		if (tr != -1) {
			if (traitsBlock.isNumerical(tr)) {
				var values = new TreeSet<Double>();
				for (var t = 1; t <= taxaBlock.getNtax(); t++) {
					values.add(traitsBlock.getTraitValue(t, tr));
				}
				for (var value : values) {
					var menuItem = new MenuItem(String.valueOf(value));
					menuItem.setOnAction(e -> {
						for (var t = 1; t <= taxaBlock.getNtax(); t++) {
							if (traitsBlock.getTraitValue(t, tr) == value)
								taxonSelectionModel.select(taxaBlock.get(t));
						}
						controller.getTraitValuesMenuButton().setText(String.valueOf(value));
					});
					controller.getTraitValuesMenuButton().getItems().add(menuItem);
				}
			} else {
				var values = new TreeSet<String>();
				for (var t = 1; t <= taxaBlock.getNtax(); t++) {
					values.add(traitsBlock.getTraitValueLabel(t, tr));
				}
				for (var value : values) {
					var menuItem = new MenuItem(String.valueOf(value));
					menuItem.setOnAction(e -> {
						for (var t = 1; t <= taxaBlock.getNtax(); t++) {
							if (traitsBlock.getTraitValueLabel(t, tr).equals(value))
								taxonSelectionModel.select(taxaBlock.get(t));
						}
						controller.getTraitValuesMenuButton().setText(value);
					});
					controller.getTraitValuesMenuButton().getItems().add(menuItem);
				}
			}
		}
	}
}
