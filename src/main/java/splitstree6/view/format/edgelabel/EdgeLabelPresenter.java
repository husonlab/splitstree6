/*
 *  EdgeLabelPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.edgelabel;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import jloda.phylo.PhyloTree;

import java.util.function.Consumer;

/**
 * edge formatter presenter
 * Daniel Huson, 5.2022
 */
public class EdgeLabelPresenter {

	private final EdgeLabelController controller;

	private Consumer<LabelEdgesBy> updateLabelsConsumer;

	public EdgeLabelPresenter(EdgeLabelController controller, ObjectProperty<LabelEdgesBy> optionLabelEdgesBy) {
		this.controller = controller;

		controller.getLabelByNoneMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.None));
		controller.getLabelByWeightMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.Weight));
		controller.getLabelByConfidenceMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.Confidence));
		controller.getLabelByConfidenceX100MenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.ConfidenceX100));
		controller.getLabelByProbabilityMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.Probability));
		optionLabelEdgesBy.addListener((v, o, n) -> {
			if (n != null) {
				switch (n) {
					case None -> controller.getLabelByToggleGroup().selectToggle(controller.getLabelByNoneMenuItem());
					case Weight ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByWeightMenuItem());
					case Confidence ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByConfidenceMenuItem());
					case ConfidenceX100 ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByConfidenceX100MenuItem());
					case Probability ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByProbabilityMenuItem());
				}
			}
		});
	}

	public void setUpdateLabelsConsumer(Consumer<LabelEdgesBy> updateLabelsConsumer) {
		this.updateLabelsConsumer = updateLabelsConsumer;
	}

	public void updateMenus(PhyloTree tree) {
		controller.getLabelByWeightMenuItem().setDisable(tree == null || !tree.hasEdgeWeights());
		controller.getLabelByConfidenceMenuItem().setDisable(tree == null || !tree.hasEdgeConfidences());
		controller.getLabelByConfidenceX100MenuItem().setDisable(tree == null || !tree.hasEdgeConfidences());
		controller.getLabelByProbabilityMenuItem().setDisable(tree == null || !tree.hasEdgeProbabilities());

		if (controller.getLabelByWeightMenuItem().isSelected() && controller.getLabelByWeightMenuItem().isDisable()
			|| controller.getLabelByConfidenceMenuItem().isSelected() && controller.getLabelByConfidenceMenuItem().isDisable()
			|| controller.getLabelByConfidenceX100MenuItem().isSelected() && controller.getLabelByConfidenceMenuItem().isDisable()
			|| controller.getLabelByProbabilityMenuItem().isSelected() && controller.getLabelByProbabilityMenuItem().isDisable()) {
			Platform.runLater(() -> controller.getLabelByNoneMenuItem().setSelected(true));
		}
	}
}
