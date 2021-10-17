/*
 * OptionValueType.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.methods;

import jloda.util.Basic;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.parse.NexusStreamParser;

import java.io.StringReader;

/**
 * possible value types of options
 * Daniel Huson, 2.2019
 */
public enum OptionValueType {
    Integer, Float, Double, String, Boolean, stringArray, doubleArray, doubleSquareMatrix, Enum;

    /**
     * get the type of a value
     *
     * @param value
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
        else
            return null;
    }

    /**
     * determines whether the given text represents an object of the given type
     *
     * @param type
     * @param text
     * @return true, if text is of given type
     */
    public static boolean isType(OptionValueType type, String text) {
        switch (type) {
            case Integer:
                return Basic.isInteger(text);
            case Float:
                return Basic.isFloat(text);
            case Double:
            case doubleArray:
            case doubleSquareMatrix:
                return Basic.isDouble(text);
            case Boolean:
                return Basic.isBoolean(text);
            case String:
                return text.length() > 0;
            case stringArray:
                return text.length() > 0;
        }
        return false;
    }

    /**
     * parses the text and returns an object of the given type
     *
     * @param type
     * @param text
     * @return object
     */
    public static Object parseType(OptionValueType type, String text) {
        switch (type) {
            case Integer:
                return Basic.parseInt(text);
            case Float:
                return Basic.parseFloat(text);
            case Double:
                return Basic.parseDouble(text);
            case doubleArray: {
                final String[] tokens = text.split("\\s+");
                final double[] array = new double[tokens.length];
                for (int i = 0; i < tokens.length; i++)
                    array[i] = Basic.parseDouble(tokens[i]);
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
                        matrix[i][j] = Basic.parseDouble(tokens[count++]);
                    }
                }
                return matrix;
            }
            case Boolean:
                return Basic.parseBoolean(text);
            case String:
                return text;
            case stringArray: {
                try {
                    return (new NexusStreamParser(new StringReader(text))).getTokensRespectCase(null, null).stream().map(s -> s.replaceAll(",$", "")).toArray(java.lang.String[]::new);
                } catch (IOExceptionWithLineNumber ioExceptionWithLineNumber) {
                    return new String[0];
                }
            }
        }
        return false;
    }

    /**
     * converts an object of the specified type to a string
     *
     * @param type
     * @param object
     * @return string
     */
    public static String toStringType(OptionValueType type, Object object) {
        switch (type) {
            case Integer:
                return java.lang.String.format("%d", (Integer) object);
            case Float:
                return Basic.removeTrailingZerosAfterDot(java.lang.String.format("%.6f", (Float) object));
            case Double:
                return Basic.removeTrailingZerosAfterDot(java.lang.String.format("%.8f", (Double) object));
            case doubleArray: {
                StringBuilder buf = new StringBuilder();
                final double[] array = (double[]) object;
                for (double value : array) {
                    if (buf.length() > 0)
                        buf.append(" ");
                    buf.append(Basic.removeTrailingZerosAfterDot(java.lang.String.format("%.4f", value)));
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
                        buf.append(Basic.removeTrailingZerosAfterDot(java.lang.String.format("%.4f", row[j])));
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
                    return "'" + Basic.toString(array, "' '") + "'";
            }
            default:
                return object.toString();
        }
    }
}
