/*
 * AlgorithmTabPresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tabs.algorithms;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import jloda.fx.control.sliderhistogram.SliderHistogramView;
import jloda.util.StringUtils;
import splitstree6.algorithms.splits.splits2splits.WeightsSlider;
import splitstree6.data.SplitsBlock;
import splitstree6.splits.ASplit;
import splitstree6.options.Option;
import splitstree6.options.OptionControlCreator;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class AlgorithmTabPresenter implements IDisplayTabPresenter {
	private final List<ChangeListener> changeListeners = new ArrayList<>();

	public AlgorithmTabPresenter(MainWindow mainWindow, AlgorithmTab algorithmTab) {
		var controller = algorithmTab.getController();

		var runningProperty = mainWindow.getWorkflow().runningProperty();

		controller.getApplyButton().setOnAction(e -> {
			algorithmTab.getAlgorithmNode().setAlgorithm(algorithmTab.getAlgorithm());
			algorithmTab.getAlgorithmNode().restart();
			algorithmTab.getAlgorithmNode().setTitle(algorithmTab.getAlgorithm().getName());
		});
		controller.getApplyButton().disableProperty().bind(runningProperty.or(algorithmTab.getAlgorithmNode().allParentsValidProperty().not()));

		var label = new Label(algorithmTab.getAlgorithm().getName());
		algorithmTab.setGraphic(label);

		controller.getMainPane().disableProperty().bind(runningProperty);
		controller.getMenuButton().disableProperty().bind(runningProperty);

		//AutoCompleteComboBox.install(controller.getAlgorithmCBox());

		controller.getAlgorithmCBox().valueProperty().addListener((v, o, n) -> {
			var algorithm = (Algorithm) n;
			algorithmTab.setAlgorithm(algorithm);
			var tooltip = (algorithm == null ? null
					: new Tooltip(algorithm.getName() + (algorithm.getCitation() == null ? "" : "\n" + StringUtils.fold(algorithm.getCitation().replaceAll(".*;", ""), 80))));
			controller.getAlgorithmCBox().setTooltip(tooltip);
		});
		controller.getAlgorithmCBox().disableProperty().bind(runningProperty.or(Bindings.size(controller.getAlgorithmCBox().getItems()).lessThanOrEqualTo(1)));

		if (algorithmTab.getAlgorithm() != null) {
			setupOptionControls(algorithmTab, controller, algorithmTab.getAlgorithm());
		}

		algorithmTab.getAlgorithmNode().algorithmProperty().addListener((c, o, n) -> algorithmTab.setAlgorithm((Algorithm) n));

		algorithmTab.algorithmProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getAlgorithmCBox().setValue(n);
				setupOptionControls(algorithmTab, controller, (Algorithm) n);
				label.setText(n.getName());
			}
		});
		controller.getMenuButton().disableProperty().bind(runningProperty);
	}

	public void setupOptionControls(AlgorithmTab algorithmTab, AlgorithmTabController controller, Algorithm algorithm) {
		controller.getMainPane().getChildren().clear();
		changeListeners.clear();

		if (algorithm instanceof WeightsSlider weightsSlider) {
			setupSplitsSlider(algorithmTab.getAlgorithmNode(), weightsSlider, controller.getMainPane());
		} else {
			for (var option : Option.getAllOptions(algorithm)) {
				var control = OptionControlCreator.apply(option, changeListeners);
				if (control != null) {
					var label = new Label(StringUtils.fromCamelCase(option.getName()));
					label.setMinWidth(120);
					var hbox = new HBox(label, control, new Label("  "));
					hbox.setAlignment(Pos.CENTER_LEFT);
					hbox.setSpacing(3);
					hbox.prefWidthProperty().bind(controller.getMainPane().widthProperty());
					var toolTip = new Tooltip(option.getToolTipText());
					label.setTooltip(toolTip);
					control.setTooltip(toolTip);
					controller.getMainPane().getChildren().add(hbox);
				}
			}
		}
	}

	private void setupSplitsSlider(AlgorithmNode node, WeightsSlider weightsSlider, Pane pane) {
		ObservableList<Double> values = FXCollections.observableArrayList();
		var max = new SimpleDoubleProperty(0);
		InvalidationListener invalidationListener = e -> {
			if (node.isValid() && node.getPreferredParent() != null) {
				var splits = (SplitsBlock) node.getPreferredParent().getDataBlock();
				values.setAll(splits.getSplits().stream().map(ASplit::getWeight).collect(Collectors.toList()));
				max.set(values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0));
			}
		};
		node.validProperty().addListener(invalidationListener);
		node.getParents().addListener(invalidationListener);
		invalidationListener.invalidated(null);

		var slider = new SliderHistogramView(values, weightsSlider.optionWeightThresholdProperty(), new SimpleDoubleProperty(0), max);
		slider.getController().getRootPane().setPrefHeight(Pane.USE_COMPUTED_SIZE);
		pane.getChildren().add(slider.getRoot());
	}

	@Override
	public void setupMenuItems() {
	}
}
