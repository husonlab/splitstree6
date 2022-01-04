/*
 * Option.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.options;

import javafx.beans.property.Property;
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
	 *
	 * @param property
	 * @param name
	 * @param toolTipText
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
			for (Object value : ((Enum) property.getValue()).getClass().getEnumConstants()) {
				legalValues.add(value.toString());
			}
		} else
			legalValues = null;
	}

	public Object getEnumValueForName(String name) {
		for (Object value : ((Enum) property.getValue()).getClass().getEnumConstants()) {
			if (value.toString().equalsIgnoreCase(name))
				return value;
		}
		return null;
	}

	/**
	 * gets all options associated with an optionable.
	 * An option is given by a getOption/setOptionValue pair of methods
	 *
	 * @param optionable
	 * @return options
	 */
	public static ArrayList<Option> getAllOptions(Object optionable) {
		final Map<String, Option> name2AnOption = new HashMap<>();

		Method listOptionsMethod = null;

		final ArrayList<Option> options = new ArrayList<>();
		Method tooltipMethod = null;
		try {
			tooltipMethod = optionable.getClass().getMethod("getToolTip", String.class);
		} catch (Exception ignored) {
		}

		for (Method method : optionable.getClass().getMethods()) {
			final String methodName = method.getName();
			if (methodName.startsWith("option") && methodName.endsWith("Property") && method.getParameterCount() == 0) {
				final String optionName = methodName.replaceAll("^option", "").replaceAll("Property$", "");

				try {
					final Object toolTip = (tooltipMethod != null ? tooltipMethod.invoke(optionable, "option" + optionName) : null);
					final String toolTipText = (toolTip != null ? toolTip.toString() : null);
					final Option option = new Option((Property) method.invoke(optionable), optionName, toolTipText);
					options.add(option);
					name2AnOption.put(optionName, option);
				} catch (IllegalAccessException | InvocationTargetException e) {
					Basic.caught(e);
				}
			} else if (methodName.equals("listOptions") && method.getParameterCount() == 0)
				listOptionsMethod = method;
		}

		// determine the order in which to return options
		final Collection<String> order = new ArrayList<>(name2AnOption.size());

		List<String> list = null;
		if (listOptionsMethod != null) {
			try {
				var obj = listOptionsMethod.invoke(optionable);
				if (obj instanceof List<?> objectList) {
					list = objectList.stream().map(Object::toString).collect(Collectors.toList());
				}
			} catch (Exception ignored) {
			}
		}

		if (list != null) {
			final Set<String> set = new HashSet<>();
			for (Object a : list) {
				String optionName = a.toString();
				if (optionName.startsWith("option"))
					optionName = optionName.replaceAll("^option", "").replaceAll("Property$", "");
				order.add(optionName);
				set.add(optionName);
			}

			// add other options not mentioned in the order
			if (false)
				for (String name : name2AnOption.keySet()) {
					if (!set.contains(name))
						order.add(name);
				}
		} else {
			order.addAll(name2AnOption.keySet());
		}
		options.clear();
		for (String name : order) {
			Option option = name2AnOption.get(name);
			if (option != null)
				options.add(option);
		}
		return options;
	}

	/**
	 * gets a mapping of names to options
	 *
	 * @param optionable
	 * @return map
	 */
	public static Map<String, Option> getName2Options(Object optionable) {
		final Map<String, Option> name2options = new TreeMap<>();
		for (Option option : getAllOptions(optionable)) {
			name2options.put(option.getName(), option);
		}
		return name2options;
	}

	/**
	 * gets the type of this option
	 *
	 * @return option value type
	 */
	public OptionValueType getOptionValueType() {
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
}
