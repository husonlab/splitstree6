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
import jloda.fx.undo.UndoManager;
import jloda.util.NumberUtils;
import splitstree6.window.MainWindow;

public class TraitsPiePresenter {
	private final InvalidationListener traitsBlockListener;
	private boolean inUpdatingDefaults = false;

	public TraitsPiePresenter(MainWindow mainWindow, TraitsPie traitsPie, TraitsPieController controller, UndoManager undoManager) {
		controller.getMaxSizeField().setText(String.valueOf(traitsPie.getOptionTraitSize()));

		traitsPie.optionShowTraitProperty().bindBidirectional(controller.getShowCBox().valueProperty());
		traitsPie.optionTraitLegendProperty().bindBidirectional(controller.getLegendCBox().selectedProperty());
		traitsPie.optionTraitSizeProperty().addListener((v, o, n) -> controller.getMaxSizeField().setText(String.valueOf(n.intValue())));
		controller.getMaxSizeField().textProperty().addListener((v, o, n) -> traitsPie.setOptionTraitSize(NumberUtils.parseInt(n)));

		traitsPie.getLegend().visibleProperty().bindBidirectional(traitsPie.optionTraitLegendProperty());

		traitsBlockListener = e -> {
			var traitsBlock = traitsPie.getTraitsBlock();
			if (traitsBlock != null) {
				if (traitsBlock.getNTraits() == 0) {
					controller.getvBox().setDisable(true);
					controller.getShowCBox().getItems().clear();
				} else {
					controller.getvBox().setDisable(false);
					controller.getShowCBox().getItems().setAll(TraitItem.All, TraitItem.None);
					for (var trait = 1; trait <= traitsBlock.getNTraits(); trait++) {
						controller.getShowCBox().getItems().add(traitsBlock.getTraitLabel(trait));
					}
					controller.getShowCBox().setValue(controller.getShowCBox().getItems().get(0));
				}
			}
		};

		traitsPie.traitsBlockProperty().addListener(new WeakInvalidationListener(traitsBlockListener));
		traitsBlockListener.invalidated(null);

		traitsPie.optionShowTraitProperty().addListener((v, o, n) -> undoManager.add("show traits", traitsPie.optionShowTraitProperty(), o, n));
		traitsPie.optionTraitSizeProperty().addListener((v, o, n) -> undoManager.add("traits node size", traitsPie.optionTraitSizeProperty(), o, n));
		traitsPie.optionTraitLegendProperty().addListener((v, o, n) -> undoManager.add("show legend", traitsPie.optionTraitLegendProperty(), o, n));

		traitsPie.optionShowTraitProperty().addListener(e -> traitsPie.updateNodes());
		traitsPie.optionTraitSizeProperty().addListener(e -> traitsPie.updateNodes());
		traitsPie.optionTraitLegendProperty().addListener(e -> traitsPie.updateNodes());
	}
}
