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

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.FloatStringConverter;
import javafx.util.converter.IntegerStringConverter;
import jloda.util.Basic;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.options.Option;
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

		controller.getReset().setOnAction(e -> {
			try {
				algorithmNode.setAlgorithm(algorithmNode.getAlgorithm().getClass().getConstructor().newInstance());
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});
		controller.getReset().disableProperty().bind(Bindings.isEmpty(controller.getMainPane().getChildren()));

		var label = new Label();
		label.textProperty().bind(algorithmTab.getAlgorithmNode().titleProperty());
		algorithmTab.setGraphic(label);

		controller.getAlgorithmCBox().valueProperty().addListener((v, o, n) -> {
			algorithmTab.getAlgorithmNode().setAlgorithm((Algorithm) n);
		});
		controller.getAlgorithmCBox().disableProperty().bind(Bindings.size(controller.getAlgorithmCBox().getItems()).lessThanOrEqualTo(1));

		if (algorithmTab.getAlgorithmNode().getAlgorithm() != null)
			setupOptionControls(controller, algorithmTab.getAlgorithmNode().getAlgorithm());

		algorithmTab.getAlgorithmNode().algorithmProperty().addListener((v, o, n) -> setupOptionControls(controller, (Algorithm) n));
	}

	public void setupOptionControls(AlgorithmTabController controller, Algorithm algorithm) {
		controller.getMainPane().getChildren().clear();

		for (var option : Option.getAllOptions(algorithm)) {
			var control = createControl(option);
			if (control != null) {
				var label = new Label(StringUtils.fromCamelCase(option.getName()));
				label.setPrefWidth(120);
				var hbox = new HBox(label, control);
				hbox.setPrefWidth(HBox.USE_COMPUTED_SIZE);
				controller.getMainPane().getChildren().add(hbox);
			}
		}
	}

	@Override
	public void setupMenuItems() {
	}

	/**
	 * creates the control for an algorithm option
	 *
	 * @param option the option
	 * @return the corresponding control
	 */
	private Control createControl(Option option) {
		switch (option.getOptionValueType()) {
			case Integer -> {
				var control = new TextField();
				control.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
				control.setText(option.getProperty().getValue().toString());
				control.setOnAction(e -> {
					if (NumberUtils.isInteger(control.getText()))
						option.getProperty().setValue(NumberUtils.parseInt(control.getText()));
					else
						Platform.runLater(() -> control.setText(String.valueOf(option.getProperty().getValue())));
				});
				ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "0" : n.toString());
				changeListeners.add(changeListener);
				option.getProperty().addListener(new WeakChangeListener(changeListener));
				return control;
			}
			case Float -> {
				var control = new TextField();
				control.setTextFormatter(new TextFormatter<>(new FloatStringConverter()));
				control.setText(option.getProperty().getValue().toString());
				control.setOnAction(e -> {
					if (NumberUtils.isFloat(control.getText()))
						option.getProperty().setValue(NumberUtils.parseFloat(control.getText()));
					else
						Platform.runLater(() -> control.setText(String.valueOf(option.getProperty().getValue())));
				});
				ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "0" : n.toString());
				changeListeners.add(changeListener);
				option.getProperty().addListener(new WeakChangeListener(changeListener));
				return control;
			}
			case Double -> {
				var control = new TextField();
				control.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
				control.setText(option.getProperty().getValue().toString());
				control.setOnAction(e -> {
					if (NumberUtils.isDouble(control.getText()))
						option.getProperty().setValue(NumberUtils.parseDouble(control.getText()));
					else
						Platform.runLater(() -> control.setText(String.valueOf(option.getProperty().getValue())));
				});
				ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "0" : n.toString());
				changeListeners.add(changeListener);
				option.getProperty().addListener(new WeakChangeListener(changeListener));
				return control;
			}
			case String -> {
				var control = new TextField(option.getProperty().getValue().toString());
				control.setOnAction(e -> option.getProperty().setValue(control.getText()));
				ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "" : n.toString());
				changeListeners.add(changeListener);
				option.getProperty().addListener(new WeakChangeListener(changeListener));
				return control;
			}
			case Boolean -> {
				var control = new CheckBox();
				control.setSelected((Boolean) option.getProperty().getValue());
				ChangeListener changeListener = (v, o, n) -> control.setSelected(n != null && (Boolean) n);
				changeListeners.add(changeListener);
				option.getProperty().addListener(new WeakChangeListener(changeListener));
				return control;
			}
			case stringArray -> {
			}
			case doubleArray -> {
			}
			case doubleSquareMatrix -> {
			}
			case Enum -> {
				var control = new ChoiceBox<String>();
				control.getItems().addAll(option.getLegalValues());
				control.setValue(option.getProperty().getValue().toString());
				control.valueProperty().addListener((v, o, n) -> option.getProperty().setValue(option.getEnumValueForName(n)));
				ChangeListener changeListener = (v, o, n) -> control.setValue(n.toString());
				changeListeners.add(changeListener);
				option.getProperty().addListener(new WeakChangeListener(changeListener));
				return control;
			}
		}
		return null;
	}
}