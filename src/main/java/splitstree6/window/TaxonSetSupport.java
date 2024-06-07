/*
 *  TaxonSetSupport.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.window;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import jloda.fx.dialog.SetParameterDialog;
import jloda.util.BitSetUtils;
import jloda.util.StringUtils;
import splitstree6.data.SetsBlock;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * support for taxon sets
 * Daniel Huson, 6.2024
 */
public class TaxonSetSupport {
	public static void setupMenuItems(MainWindow mainWindow) {
		var stage = mainWindow.getStage();
		var controller = mainWindow.getController();

		controller.getAddTaxonSetMenuItem().setOnAction(e -> {
			var name = "taxa";
			var inputTaxaBlock = mainWindow.getWorkflow().getInputTaxaBlock();
			var inputSetsBlock = inputTaxaBlock.getSetsBlock();
			if (inputSetsBlock != null) {
				var names = inputSetsBlock.getTaxSets().stream().map(SetsBlock.TaxSet::getName).collect(Collectors.toSet());
				var count = 0;
				while (names.contains(name)) {
					name = "taxa-" + (++count);
				}
			}
			name = SetParameterDialog.apply(stage, "Enter name for set of %d selected taxa".formatted(mainWindow.getTaxonSelectionModel().size()), name);
			if (name != null && !name.isBlank()) {
				name = StringUtils.toCleanName(name);
				if (inputSetsBlock == null) {
					inputSetsBlock = new SetsBlock();
					inputTaxaBlock.setSetsBlock(inputSetsBlock);
				}
				{
					var taxa = new BitSet();
					for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
						taxa.set(inputTaxaBlock.indexOf(taxon));
					}
					var taxaSet = new SetsBlock.TaxSet(name, taxa);
					inputSetsBlock.getTaxSets().add(taxaSet);
				}
				{
					var workingTaxaBlock = mainWindow.getWorkingTaxa();
					var workingSetsBlock = workingTaxaBlock.getSetsBlock();
					if (workingSetsBlock == null) {
						workingSetsBlock = new SetsBlock();
						workingTaxaBlock.setSetsBlock(workingSetsBlock);
					}

					var taxa = new BitSet();
					for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
						taxa.set(workingTaxaBlock.indexOf(taxon));
					}
					var taxaSet = new SetsBlock.TaxSet(name, taxa);
					workingSetsBlock.getTaxSets().add(taxaSet);
				}
				updateSetsMenu(mainWindow, controller.getSelectSetsMenu());
			}
		});
		controller.getAddTaxonSetMenuItem().disableProperty().bind(Bindings.isEmpty(mainWindow.getTaxonSelectionModel().getSelectedItems()));

		controller.getRemoveTaxonSetMenuItem().setOnAction(e -> {
			var inputTaxaBlock = mainWindow.getWorkflow().getInputTaxaBlock();
			var inputSetsBlock = inputTaxaBlock.getSetsBlock();
			if (inputSetsBlock != null) {
				var names = inputSetsBlock.getTaxSets().stream().map(SetsBlock.TaxSet::getName).toList();
				if (!names.isEmpty()) {
					var result = SetParameterDialog.apply(stage, "Select set to delete", names, names.get(0));
					if (result != null) {
						inputSetsBlock.getTaxSets().stream().filter(s -> s.getName().equals(result)).findAny().ifPresent(set -> inputSetsBlock.getTaxSets().remove(set));
						var workingTaxaBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
						var workingSetsBlock = workingTaxaBlock.getSetsBlock();
						if (workingSetsBlock != null) {
							workingSetsBlock.getTaxSets().stream().filter(s -> s.getName().equals(result)).findAny().ifPresent(set -> workingSetsBlock.getTaxSets().remove(set));
						}
						updateSetsMenu(mainWindow, controller.getSelectSetsMenu());
					}
				}
			}
		});
		controller.getRemoveTaxonSetMenuItem().disableProperty().bind(Bindings.createBooleanBinding(
				() -> mainWindow.getWorkingTaxa() == null || mainWindow.getWorkingTaxa().getSetsBlock() == null, mainWindow.getWorkflow().runningProperty()));
	}

	public static void updateSetsMenu(MainWindow mainWindow, Menu menu) {
		Consumer<String> selectTaxonByName = label -> {
			var t = mainWindow.getWorkingTaxa().indexOf(label);
			if (t > 0)
				mainWindow.getTaxonSelectionModel().select(mainWindow.getWorkingTaxa().get(t));
		};
		updateSetsMenu(mainWindow, menu, selectTaxonByName);
	}

	public static void updateSetsMenu(MainWindow mainWindow, Menu menu, Consumer<String> selectTaxonByName) {
		menu.getItems().removeAll(menu.getItems().stream().filter(m -> m.getId() != null && m.getId().equals("taxon-set-menu-item")).toList());

		var inputTaxaBlock = mainWindow.getWorkflow().getInputTaxaBlock();
		if (inputTaxaBlock != null && inputTaxaBlock.getSetsBlock() != null) {
			var list = new ArrayList<>(inputTaxaBlock.getSetsBlock().getTaxSets());
			list.sort(Comparator.comparing(SetsBlock.TaxSet::getName));
			for (var taxSet : list) {
				var menuItem = new MenuItem("Taxon Set '" + taxSet.getName() + "'");
				menuItem.setId("taxon-set-menu-item");
				menuItem.setOnAction(a -> {
					for (var t : BitSetUtils.members(taxSet)) {
						selectTaxonByName.accept(inputTaxaBlock.getLabel(t));
					}
				});
				menuItem.disableProperty().bind(mainWindow.getWorkflow().runningProperty());
				menu.getItems().add(menuItem);
			}
		}
		;
	}
}
