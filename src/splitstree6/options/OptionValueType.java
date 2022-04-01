/*
 * OptionValueType.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.fx.util.BasicFX;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;

import java.io.StringReader;

/**
 * possible value types of options
 * Daniel Huson, 2.2019
 */
public enum OptionValueType {
	Integer, Float, Double, String, Boolean, stringArray, doubleArray, doubleSquareMatrix, Enum, Color;

	/**
	 * get the type of a value
	 *
	 * @return type
	 */
	public static OptionValueType getValueType(Object value) {
		if (value instanceof Integer)
			return Integer;
		else if (value instanceof Float)
			return Float;
		else if (value instanceof Double)
			return Double;
		else if (value instanceof Boolean)
			return Boolean;
		else if (value instanceof String)
			return String;
		else if (value instanceof String[])
			return stringArray;
		else if (value instanceof double[])
			return doubleArray;
		else if (value instanceof double[][])
			return doubleSquareMatrix;
		else if (value instanceof Enum)
			return Enum;
		else if (value instanceof javafx.scene.paint.Color)
			return Color;
		else
			return null;
	}

	/**
	 * determines whether the given text represents an object of the given type
	 *
	 * @return true, if text is of given type
	 */
	public static boolean isType(OptionValueType type, String text) {
        return switch (type) {
            case Integer -> NumberUtils.isInteger(text);
            case Float -> NumberUtils.isFloat(text);
            case Double, doubleArray, doubleSquareMatrix -> NumberUtils.isDouble(text);
            case Boolean -> NumberUtils.isBoolean(text);
            case String -> text.length() > 0;
			case stringArray -> text.length() > 0;
			case Color -> BasicFX.isColor(text);
			default -> false;
        };
    }

	/**
	 * parses the text and returns an object of the given type
	 *
	 * @return object
	 */
	public static Object parseType(OptionValueType type, String text) {
		switch (type) {
			case Integer:
				return NumberUtils.parseInt(text);
			case Float:
				return NumberUtils.parseFloat(text);
			case Double:
				return NumberUtils.parseDouble(text);
			case doubleArray: {
				final String[] tokens = text.split("\\s+");
				final double[] array = new double[tokens.length];
				for (int i = 0; i < tokens.length; i++)
					array[i] = NumberUtils.parseDouble(tokens[i]);
				return array;
			}
			case doubleSquareMatrix: {
				final String[] tokens = text.split("\\s+");
				final int length = (int) Math.round(Math.sqrt(tokens.length));
				if (length * length != tokens.length)
					throw new RuntimeException("doubleSquareMatrix: wrong number of tokens: " + tokens.length);
				final double[][] matrix = new double[length][length];
				int count = 0;
				for (int i = 0; i < length; i++) {
					for (int j = 0; j < length; j++) {
						matrix[i][j] = NumberUtils.parseDouble(tokens[count++]);
					}
				}
				return matrix;
			}
			case Boolean:
				return NumberUtils.parseBoolean(text);
			case String:
				return text;
			case stringArray: {
				try {
					return (new NexusStreamParser(new StringReader(text))).getTokensRespectCase(null, null).stream().map(s -> s.replaceAll(",$", "")).toArray(java.lang.String[]::new);
				} catch (IOExceptionWithLineNumber ioExceptionWithLineNumber) {
					return new String[0];
				}
			}
			case Color:
				return javafx.scene.paint.Color.web(text);
		}
		return false;
	}

	/**
	 * converts an object of the specified type to a string
	 *
	 * @return string
	 */
	public static String toStringType(OptionValueType type, Object object) {
		switch (type) {
			case Integer:
				return java.lang.String.format("%d", (Integer) object);
			case Float:
				return StringUtils.removeTrailingZerosAfterDot(java.lang.String.format("%.6f", (Float) object));
			case Double:
				return StringUtils.removeTrailingZerosAfterDot(java.lang.String.format("%.8f", (Double) object));
			case doubleArray: {
				StringBuilder buf = new StringBuilder();
				final double[] array = (double[]) object;
				for (double value : array) {
					if (buf.length() > 0)
						buf.append(" ");
					buf.append(StringUtils.removeTrailingZerosAfterDot(java.lang.String.format("%.4f", value)));
				}
				return buf.toString();
			}
			case doubleSquareMatrix: {
				StringBuilder buf = new StringBuilder();
				final double[][] matrix = (double[][]) object;
				for (double[] row : matrix) {
					for (int j = 0; j < matrix.length; j++) {
						if (j > 0)
							buf.append(" ");
						buf.append(StringUtils.removeTrailingZerosAfterDot(java.lang.String.format("%.4f", row[j])));
					}
					buf.append(" "); // could also put \n here
				}
				return buf.toString();
			}
			case stringArray: {
				final String[] array = (String[]) object;
				if (array.length == 0)
					return "";
				else
					return "'" + StringUtils.toString(array, "' '") + "'";
			}
			case Color:
				return object.toString();
			default:
				return "'" + object.toString() + "'";
		}
	}
}
