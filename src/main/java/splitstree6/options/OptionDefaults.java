/*
 *  OptionDefaults.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.util.ProgramProperties;
import jloda.util.parse.NexusStreamParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * the option values that a user has chosen as their own defaults for an algorithm, and the built-in ones.
 * <p>
 * An option starts with its built-in default, always. The user can store the current values of the options of an
 * algorithm as their defaults; these are then applied to an algorithm that is added to a workflow by hand in the
 * program window, and to nothing else: not when a file is opened, not when a workflow is run on other data, not in
 * the command-line tools. The stored values are kept in the preferences of the program, one entry per algorithm.
 * <p>
 * Options whose value is a list (of taxa, trees, sites or numbers) belong to the data and are not stored.
 * Daniel Huson, 11.2026
 */
public class OptionDefaults {
	private static final String KEY_PREFIX = "UserDefaults.";

	private static String key(IOptionsCarrier optionsCarrier) {
		return KEY_PREFIX + optionsCarrier.getClass().getName();
	}

	/**
	 * the options that can be stored
	 */
	public static List<Option<?>> storableOptions(IOptionsCarrier optionsCarrier) {
		var result = new ArrayList<Option<?>>();
		if (optionsCarrier != null) {
			for (Option<?> option : Option.getAllOptions(optionsCarrier)) {
				switch (option.getOptionValueType()) {
					case stringArray, intArray, doubleArray, doubleSquareMatrix -> {
					}
					default -> result.add(option);
				}
			}
		}
		return result;
	}

	/**
	 * the current values of the storable options, as they are written in a workflow file: name = value, name = value
	 */
	public static String valuesAsString(IOptionsCarrier optionsCarrier) {
		var buf = new StringBuilder();
		for (var option : storableOptions(optionsCarrier)) {
			var value = OptionValueType.toStringType(option.getOptionValueType(), option.getProperty().getValue());
			if (!value.isEmpty()) {
				if (!buf.isEmpty())
					buf.append(", ");
				buf.append(option.getName()).append(" = ").append(value);
			}
		}
		return buf.toString();
	}

	public static boolean hasUserDefaults(IOptionsCarrier optionsCarrier) {
		return optionsCarrier != null && !ProgramProperties.get(key(optionsCarrier), "").isBlank();
	}

	/**
	 * make the current values the user's defaults for this algorithm
	 */
	public static void storeAsUserDefaults(IOptionsCarrier optionsCarrier) {
		var values = valuesAsString(optionsCarrier);
		if (values.isBlank())
			ProgramProperties.remove(key(optionsCarrier));
		else
			ProgramProperties.put(key(optionsCarrier), values);
	}

	public static void forgetUserDefaults(IOptionsCarrier optionsCarrier) {
		ProgramProperties.remove(key(optionsCarrier));
	}

	/**
	 * set the options to the user's defaults, if there are any. A stored option or value that the algorithm no longer
	 * has is skipped
	 *
	 * @return true, if there were any
	 */
	public static boolean applyUserDefaults(IOptionsCarrier optionsCarrier) {
		if (!hasUserDefaults(optionsCarrier))
			return false;
		var storable = new java.util.HashSet<String>();
		for (var option : storableOptions(optionsCarrier))
			storable.add(option.getName());
		// keep only what this algorithm has and may store, then read it as an OPTIONS statement
		var kept = new ArrayList<String>();
		for (var assignment : splitAssignments(ProgramProperties.get(key(optionsCarrier), ""))) {
			var pos = assignment.indexOf('=');
			if (pos > 0 && storable.contains(assignment.substring(0, pos).trim()))
				kept.add(assignment.trim());
		}
		if (kept.isEmpty())
			return false;
		try (var np = new NexusStreamParser(new StringReader("OPTIONS " + String.join(", ", kept) + ";"))) {
			OptionIO.parseOptions(np, optionsCarrier);
			return true;
		} catch (IOException ex) {
			System.err.println("Stored defaults of " + optionsCarrier.getClass().getSimpleName() + " not applied: " + ex.getMessage());
			return false;
		}
	}

	/**
	 * split at the commas that are not inside quotes
	 */
	private static List<String> splitAssignments(String text) {
		var result = new ArrayList<String>();
		var current = new StringBuilder();
		var quote = (char) 0;
		for (var ch : text.toCharArray()) {
			if (quote != 0) {
				if (ch == quote)
					quote = 0;
				current.append(ch);
			} else if (ch == '\'' || ch == '"') {
				quote = ch;
				current.append(ch);
			} else if (ch == ',') {
				result.add(current.toString());
				current.setLength(0);
			} else
				current.append(ch);
		}
		if (!current.toString().isBlank())
			result.add(current.toString());
		return result;
	}

	/**
	 * set the storable options to the values that a new instance of the algorithm has
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void applyFactoryDefaults(IOptionsCarrier optionsCarrier) {
		try {
			var fresh = optionsCarrier.getClass().getConstructor().newInstance();
			var name2fresh = Option.getName2Options(fresh);
			for (Option option : storableOptions(optionsCarrier)) {
				var freshOption = name2fresh.get(option.getName());
				if (freshOption != null)
					option.getProperty().setValue(freshOption.getProperty().getValue());
			}
		} catch (ReflectiveOperationException ex) {
			System.err.println("Factory settings of " + optionsCarrier.getClass().getSimpleName() + " not available: " + ex.getMessage());
		}
	}

	/**
	 * do the current values differ from the built-in ones?
	 */
	public static boolean differsFromFactoryDefaults(IOptionsCarrier optionsCarrier) {
		try {
			return !valuesAsString(optionsCarrier).equals(valuesAsString(optionsCarrier.getClass().getConstructor().newInstance()));
		} catch (ReflectiveOperationException ex) {
			return false;
		}
	}
}
