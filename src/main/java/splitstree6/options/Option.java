/*
 * Option.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.options;

import javafx.beans.property.*;
import jloda.util.Basic;
import jloda.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A property-based option
 * Daniel Huson, 2.2019
 */
public class Option<T> {
	private final Property<T> property;
	private final String name;
	private final String toolTipText;
	private final ArrayList<String> legalValues;

	/**
	 * constructs an option
	 */
	Option(Property<T> property, String name, String toolTipText) {
		this.property = property;
		this.name = name;
		if (toolTipText != null && toolTipText.length() > 0)
			this.toolTipText = toolTipText;
		else
			this.toolTipText = StringUtils.fromCamelCase(name);

		if (property.getValue() != null && property.getValue().getClass().isEnum()) {
			legalValues = new ArrayList<>();
			for (Object value : ((Enum<?>) property.getValue()).getClass().getEnumConstants()) {
				legalValues.add(value.toString());
			}
		} else
			legalValues = null;
	}

	public Object getEnumValueForName(String name) {
		for (var value : ((Enum<?>) property.getValue()).getClass().getEnumConstants()) {
			if (value.toString().equalsIgnoreCase(name))
				return value;
		}
		return null;
	}

	/**
	 * gets the type of this option
	 *
	 * @return option value type
	 */
	public OptionValueType getOptionValueType() {
		if (property instanceof StringProperty)
			return OptionValueType.String;
		else if (property instanceof IntegerProperty)
			return OptionValueType.Integer;
		else if (property instanceof DoubleProperty)
			return OptionValueType.Double;
		else if (property instanceof BooleanProperty)
			return OptionValueType.Boolean;
		return OptionValueType.getValueType(property.getValue());
	}

	/**
	 * get the property corresponding to this option
	 *
	 * @return property
	 */
	public Property<T> getProperty() {
		return property;
	}

	public String getName() {
		return name;
	}

	public String getToolTipText() {
		return toolTipText;
	}

	public ArrayList<String> getLegalValues() {
		return legalValues;
	}

	/**
	 * gets all options associated with an optionable class
	 * An option is given by a getOption/setOptionValue pair of methods
	 *
	 * @return options
	 */
	public static ArrayList<Option> getAllOptions(IOptionsCarrier optionsCarrier) {
		final var name2AnOption = new HashMap<String, Option<?>>();

		Method listOptionsMethod = null;

		Method tooltipMethod = null;
		try {
			tooltipMethod = optionsCarrier.getClass().getMethod("getToolTip", String.class);
		} catch (Exception ignored) {
		}

		for (var method : optionsCarrier.getClass().getMethods()) {
			final var methodName = method.getName();
			if (methodName.startsWith("option") && methodName.endsWith("Property") && method.getParameterCount() == 0) {
				final var optionName = methodName.replaceAll("^option", "").replaceAll("Property$", "");

				try {
					final var toolTip = (tooltipMethod != null ? tooltipMethod.invoke(optionsCarrier, "option" + optionName) : null);
					final var toolTipText = (toolTip != null ? toolTip.toString() : null);
					final var option = new Option<>((Property<?>) method.invoke(optionsCarrier), optionName, toolTipText);
					name2AnOption.put(optionName, option);
				} catch (IllegalAccessException | InvocationTargetException e) {
					Basic.caught(e);
				}
			} else if (methodName.equals("listOptions") && method.getParameterCount() == 0)
				listOptionsMethod = method;
		}

		// collect the listed options:

		final var listedOptions = new ArrayList<String>();
		if (listOptionsMethod != null) {
			try {
				var result = listOptionsMethod.invoke(optionsCarrier);
				if (result instanceof List<?> namesList) {
					listedOptions.addAll(namesList.stream().map(Object::toString)
							.map(name -> name.replaceAll("^option", "").replaceAll("Property$", ""))
							.collect(Collectors.toList()));
				}
			} catch (Exception ignored) {
			}
		} else // list not provided, list all found:
			listedOptions.addAll(name2AnOption.keySet());

		return listedOptions.stream().map(name2AnOption::get).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * gets a mapping of names to options
	 *
	 * @return map
	 */
	public static Map<String, Option<?>> getName2Options(IOptionsCarrier optionsCarrier) {
		final var name2options = new TreeMap<String, Option<?>>();
		for (var option : getAllOptions(optionsCarrier)) {
			name2options.put(option.getName(), option);
		}
		return name2options;
	}
}
