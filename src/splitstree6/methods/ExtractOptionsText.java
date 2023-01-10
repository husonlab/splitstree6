/*
 * ExtractOptionsText.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.methods;

import javafx.beans.property.Property;
import jloda.util.Basic;
import jloda.util.StringUtils;
import splitstree6.workflow.Algorithm;

import java.util.ArrayList;

/**
 * report options used by algorithm
 * Daniel Huson, 10.2021
 */
public class ExtractOptionsText {
	public static String apply(Algorithm algorithm) {
		var list = new ArrayList<String>();
		var hasDefaultOption = false;
		try {
			var clazz = algorithm.getClass();
			var algorithmWithDefaultOptions = clazz.getConstructor().newInstance();

			for (var method : clazz.getMethods()) {
				var methodName = method.getName();
				if (methodName.startsWith("option") && methodName.endsWith("Property") && method.getParameterCount() == 0) {
					var optionName = methodName.replaceAll("^option", "").replaceAll("Property$", "");
					var property = (Property) method.invoke(algorithm);
					var defaultProperty = (Property) method.invoke(algorithmWithDefaultOptions);
					if (property.getValue() == null || property.getValue().equals(defaultProperty.getValue())) {
						hasDefaultOption = true;
					} else {
						list.add(optionName + "=" + property.getValue());
					}
				}
			}
		} catch (Exception e) {
			Basic.caught(e);
		}
		if (hasDefaultOption) {
			if (list.size() == 0)
				return "default options";
			else return "default options, except " + StringUtils.toString(list, ", ");
		} else {
			if (list.size() == 0)
				return "";
			else
				return StringUtils.toString(list, ", ");
		}
	}
}
