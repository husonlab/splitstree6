/*
 *  AlgorithmTabPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.algorithms;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import jloda.util.StringUtils;
import splitstree6.options.Option;
import splitstree6.options.OptionControlCreator;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.workflow.Algorithm;

import java.util.ArrayList;
import java.util.List;


public class AlgorithmTabPresenter implements IDisplayTabPresenter {
	private final List<ChangeListener> changeListeners = new ArrayList<>();

	public AlgorithmTabPresenter(AlgorithmTab algorithmTab) {
		var controller = algorithmTab.getController();
		var algorithmNode = algorithmTab.getAlgorithmNode();

		controller.getApplyButton().setOnAction(e -> algorithmTab.getAlgorithmNode().restart());
		controller.getApplyButton().disableProperty().bind(algorithmNode.getService().runningProperty().or(algorithmNode.allParentsValidProperty().not()));

		var label = new Label();
		label.textProperty().bind(algorithmTab.getAlgorithmNode().titleProperty());
		algorithmTab.setGraphic(label);

		controller.getAlgorithmCBox().valueProperty().addListener((v, o, n) -> {
			var algorithm = (Algorithm) n;
			algorithmTab.getAlgorithmNode().setAlgorithm(algorithm);
			var tooltip = (algorithm == null ? null
					: new Tooltip(algorithm.getName() + (algorithm.getCitation() == null ? "" : "\n" + StringUtils.fold(algorithm.getCitation().replaceAll(".*;", ""), 80))));
			controller.getAlgorithmCBox().setTooltip(tooltip);
		});
		controller.getAlgorithmCBox().disableProperty().bind(Bindings.size(controller.getAlgorithmCBox().getItems()).lessThanOrEqualTo(1));

		if (algorithmTab.getAlgorithmNode().getAlgorithm() != null)
			setupOptionControls(controller, algorithmTab.getAlgorithmNode().getAlgorithm());

		algorithmTab.getAlgorithmNode().algorithmProperty().addListener((v, o, n) -> {
			controller.getAlgorithmCBox().setValue(n);
			setupOptionControls(controller, (Algorithm) n);
		});
	}

	public void setupOptionControls(AlgorithmTabController controller, Algorithm algorithm) {
		controller.getMainPane().getChildren().clear();
		changeListeners.clear();

		for (var option : Option.getAllOptions(algorithm)) {
			var control = OptionControlCreator.apply(option, changeListeners);
			if (control != null) {
				var label = new Label(StringUtils.fromCamelCase(option.getName()));
				label.setMinWidth(120);
				var hbox = new HBox(label, control);
				hbox.prefWidthProperty().bind(controller.getMainPane().widthProperty());
				controller.getMainPane().getChildren().add(hbox);
				var toolTip = new Tooltip(option.getToolTipText());
				label.setTooltip(toolTip);
				control.setTooltip(toolTip);
			}
		}
	}

	@Override
	public void setupMenuItems() {
	}

}
