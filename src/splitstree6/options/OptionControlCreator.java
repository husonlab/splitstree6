/*
 *  OptionControlCreator.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.options;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.*;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.FloatStringConverter;
import javafx.util.converter.IntegerStringConverter;
import jloda.util.NumberUtils;

import java.util.Collection;

/**
 * creates controls for options
 * Daniel Huson, 11.21
 */
public class OptionControlCreator {
	/**
	 * creates the control for an  option
	 *
	 * @param option          the option
	 * @param changeListeners need to keep a list of the change listeners, as the are only weakly linked to the options
	 * @return the corresponding control
	 */
	public static Control apply(Option option, Collection<ChangeListener> changeListeners) {
		switch (option.getOptionValueType()) {
			case Integer -> {
				var control = new TextField();
				control.setPrefColumnCount(6);
				control.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
				control.setText(option.getProperty().getValue().toString());
				control.setOnAction(e -> {
					if (NumberUtils.isInteger(control.getText()))
						option.getProperty().setValue(NumberUtils.parseInt(control.getText()));
					else
						Platform.runLater(() -> control.setText(String.valueOf(option.getProperty().getValue())));
				});
				control.focusedProperty().addListener((v, o, n) -> {
					if (!n)
						control.getOnAction().handle(null);
				});
				if (changeListeners != null) {
					ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "0" : n.toString());
					changeListeners.add(changeListener);
					option.getProperty().addListener(new WeakChangeListener(changeListener));
				}
				return control;
			}
			case Float -> {
				var control = new TextField();
				control.setPrefColumnCount(6);
				control.setTextFormatter(new TextFormatter<>(new FloatStringConverter()));
				control.setText(option.getProperty().getValue().toString());
				control.setOnAction(e -> {
					if (NumberUtils.isFloat(control.getText()))
						option.getProperty().setValue(NumberUtils.parseFloat(control.getText()));
					else
						Platform.runLater(() -> control.setText(String.valueOf(option.getProperty().getValue())));
				});
				control.focusedProperty().addListener((v, o, n) -> {
					if (!n)
						control.getOnAction().handle(null);
				});
				if (changeListeners != null) {
					ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "0" : n.toString());
					changeListeners.add(changeListener);
					option.getProperty().addListener(new WeakChangeListener(changeListener));
				}
				return control;
			}
			case Double -> {
				var control = new TextField();
				control.setPrefColumnCount(6);
				control.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
				control.setText(option.getProperty().getValue().toString());
				control.setOnAction(e -> {
					if (NumberUtils.isDouble(control.getText()))
						option.getProperty().setValue(NumberUtils.parseDouble(control.getText()));
					else
						Platform.runLater(() -> control.setText(String.valueOf(option.getProperty().getValue())));
				});
				control.focusedProperty().addListener((v, o, n) -> {
					if (!n)
						control.getOnAction().handle(null);
				});
				if (changeListeners != null) {
					ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "0" : n.toString());
					changeListeners.add(changeListener);
					option.getProperty().addListener(new WeakChangeListener(changeListener));
				}
				return control;
			}
			case String -> {
				var control = new TextField(option.getProperty().getValue().toString());
				control.setPrefColumnCount(12);
				control.setOnAction(e -> option.getProperty().setValue(control.getText()));
				if (changeListeners != null) {
					ChangeListener changeListener = (v, o, n) -> control.setText(n == null ? "" : n.toString());
					changeListeners.add(changeListener);
					option.getProperty().addListener(new WeakChangeListener(changeListener));
				}
				control.focusedProperty().addListener((v, o, n) -> {
					if (!n)
						control.getOnAction().handle(null);
				});
				return control;
			}
			case Boolean -> {
				var control = new CheckBox();
				control.setSelected((Boolean) option.getProperty().getValue());
				control.setOnAction(e -> option.getProperty().setValue(control.isSelected()));
				if (changeListeners != null) {
					ChangeListener changeListener = (v, o, n) -> control.setSelected(n != null && (Boolean) n);
					changeListeners.add(changeListener);
					option.getProperty().addListener(new WeakChangeListener(changeListener));
				}
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
				if (changeListeners != null) {
					ChangeListener changeListener = (v, o, n) -> control.setValue(n.toString());
					changeListeners.add(changeListener);
					option.getProperty().addListener(new WeakChangeListener(changeListener));
				}
				return control;
			}
		}
		return null;
	}
}
