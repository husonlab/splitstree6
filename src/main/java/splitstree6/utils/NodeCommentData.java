/*
 * NodeCommentData.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.utils;

import jloda.util.BitSetUtils;
import jloda.util.StringUtils;

import java.util.*;

public class NodeCommentData {
	private final Map<String, Value> values = new HashMap<>();

	public Map<String, Value> values() {
		return values;
	}

	public int parse(String comment) {
		if (comment == null)
			return 0;

		if (comment.startsWith("["))
			comment = comment.substring(1);
		if (comment.endsWith("]"))
			comment = comment.substring(0, comment.length() - 1);

		comment = comment.trim();

		var oldSize = values.size();

		if (!comment.startsWith("&"))
			return 0;

		// remove leading '&'
		comment = comment.substring(1).trim();

		int i = 0;
		while (i < comment.length()) {

			// ---- parse key ----
			int keyStart = i;

			char c = comment.charAt(i);
			if (!isKeyFirst(c))
				throw new IllegalArgumentException("Invalid key at position " + i);

			i++;
			while (i < comment.length() && isKeyRest(comment.charAt(i)))
				i++;

			String key = comment.substring(keyStart, i);

			// ---- skip whitespace ----
			while (i < comment.length() && Character.isWhitespace(comment.charAt(i)))
				i++;

			if (i >= comment.length() || comment.charAt(i) != '=')
				throw new IllegalArgumentException("Expected '=' after key: " + key);

			i++; // skip '='

			while (i < comment.length() && Character.isWhitespace(comment.charAt(i)))
				i++;

			// ---- parse value ----
			int valueStart = i;

			if (i >= comment.length())
				throw new IllegalArgumentException("Missing value for key: " + key);

			if (comment.charAt(i) == '\'') {
				// single quoted string
				i++; // skip opening quote
				while (i < comment.length()) {
					if (comment.charAt(i) == '\'') {
						if (i + 1 < comment.length() && comment.charAt(i + 1) == '\'') {
							i += 2; // escaped quote
						} else {
							i++; // closing quote
							break;
						}
					} else {
						i++;
					}
				}
			} else if (comment.charAt(i) == '{') {
				// brace expression
				int braceDepth = 1;
				i++;
				while (i < comment.length() && braceDepth > 0) {
					char ch = comment.charAt(i);
					if (ch == '{') braceDepth++;
					else if (ch == '}') braceDepth--;
					i++;
				}
				if (braceDepth != 0)
					throw new IllegalArgumentException("Unterminated brace value for key: " + key);
			} else {
				// number
				while (i < comment.length()) {
					char ch = comment.charAt(i);
					if (ch == ',' || Character.isWhitespace(ch))
						break;
					i++;
				}
			}

			String valueText = comment.substring(valueStart, i).trim();

			// store parsed value
			values.put(key, parseValue(valueText));

			// ---- skip whitespace ----
			while (i < comment.length() && Character.isWhitespace(comment.charAt(i)))
				i++;

			// ---- skip comma ----
			if (i < comment.length()) {
				if (comment.charAt(i) == ',') {
					i++; // move to next pair
					while (i < comment.length() && Character.isWhitespace(comment.charAt(i)))
						i++;
				} else {
					throw new IllegalArgumentException("Expected ',' at position " + i);
				}
			}
		}
		return values.size() - oldSize;
	}

	public String toCommentString() {
		var buf = new StringBuilder();
		buf.append("[&");
		var first = true;
		for (var entry : values.entrySet()) {
			if (first)
				first = false;
			else buf.append(",");
			buf.append(entry.getKey());
			buf.append("=");
			buf.append(entry.getValue());
		}
		return buf.append(']').toString();
	}

	private Value parseValue(String valueText) {
		{
			var value = new DoubleValue();
			if (value.valueOf(valueText)) {
				return value;
			}
		}
		{
			var value = new DoubleArrayValue();
			if (value.valueOf(valueText)) {
				return value;
			}
		}
		{
			var value = new IntValue();
			if (value.valueOf(valueText)) {
				return value;
			}
		}
		{
			var value = new IntSetValue();
			if (value.valueOf(valueText)) {
				return value;
			}
		}
		{
			var value = new StringValue();
			if (value.valueOf(valueText)) {
				return value;
			}
		}
		{
			var value = new StringSetValue();
			if (value.valueOf(valueText)) {
				return value;
			}
		}
		return null;
	}

	public sealed interface Value permits IntValue, IntSetValue, DoubleValue, DoubleArrayValue, StringValue, StringSetValue {
		boolean valueOf(String string);

		String toString();
	}


	public static final class DoubleValue implements Value {
		private double value;

		@Override
		public boolean valueOf(String string) {
			try {
				value = Double.parseDouble(string);
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}

		@Override
		public String toString() {
			return StringUtils.removeTrailingZerosAfterDot("%.12f", value);
		}

		public double getValue() {
			return value;
		}

		public void setValue(double value) {
			this.value = value;
		}
	}

	public static final class DoubleArrayValue implements Value {
		private double[] value;

		@Override
		public boolean valueOf(String string) {
			if (string.startsWith("{") && string.endsWith("}")) {
				try {
					string = string.substring(1, string.length() - 1).trim(); // remove braces
					var list = new ArrayList<Double>();
					for (var token : string.split(","))
						list.add(Double.parseDouble(token));
					value = list.stream().mapToDouble(Double::doubleValue).toArray();
					return true;
				} catch (NumberFormatException ignored) {
				}
			}
			return false;
		}

		@Override
		public String toString() {
			var buf = new StringBuilder();
			buf.append("{");
			for (int i = 0; i < value.length; i++) {
				if (i > 0)
					buf.append(",");
				buf.append(StringUtils.removeTrailingZerosAfterDot("%.12f", value[i]));
			}
			buf.append("}");
			return buf.toString();
		}

		public double[] getValue() {
			return value;
		}

		public void setValue(double[] value) {
			this.value = new double[value.length];
			System.arraycopy(value, 0, this.value, 0, value.length);
		}
	}

	public static final class IntValue implements Value {
		private int value;

		@Override
		public boolean valueOf(String string) {
			try {
				value = Integer.parseInt(string);
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}
	}

	public static final class IntSetValue implements Value {
		private final BitSet value = new BitSet();

		@Override
		public boolean valueOf(String string) {
			if (string.startsWith("{") && string.endsWith("}")) {
				string = string.substring(1, string.length() - 1).trim(); // remove braces
				try {
					var bits = BitSetUtils.valueOf(string);
					if (bits.cardinality() > 0) {
						value.or(bits);
						return true;
					}
				} catch (Exception ignored) {
				}
			}
			return false;

		}

		@Override
		public String toString() {
			return "{" + StringUtils.toString(value) + "}";
		}

		public BitSet getValue() {
			return value;
		}

		public void setValue(BitSet value) {
			this.value.clear();
			this.value.or(value);
		}
	}

	public static final class StringValue implements Value {
		private String value;

		@Override
		public boolean valueOf(String string) {
			if (string == null)
				return false;

			string = string.trim();

			if (string.isEmpty())
				return false;

			if (string.startsWith("{") && string.endsWith("}"))
				return false;

			if (string.length() >= 2 && string.startsWith("'") && string.endsWith("'")) {
				var inner = string.substring(1, string.length() - 1);
				// unescape doubled single quotes: '' → '
				inner = inner.replace("''", "'");
				value = inner;
				return true;
			} else {
				value = string;
				return true;
			}
		}

		@Override
		public String toString() {
			if (value == null)
				return "''";
			return "'" + value.replace("'", "''") + "'";
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	public static final class StringSetValue implements Value {
		final private Set<String> value = new TreeSet<>();

		@Override
		public boolean valueOf(String string) {
			if (!(string.startsWith("{") && string.endsWith("}")))
				return false;

			string = string.substring(1, string.length() - 1).trim(); // remove braces

			var tokens = new ArrayList<String>();

			var i = 0;
			var start = 0;
			var inQuotes = false;

			while (i < string.length()) {
				char c = string.charAt(i);

				if (c == '\'') {
					// handle escaped '' inside quoted string
					if (inQuotes && i + 1 < string.length() && string.charAt(i + 1) == '\'') {
						i += 2;
						continue;
					}
					inQuotes = !inQuotes;
					i++;
				} else if (c == ',' && !inQuotes) {
					tokens.add(string.substring(start, i).trim());
					i++;
					start = i;
				} else {
					i++;
				}
			}

			// add final token
			if (start <= string.length()) {
				tokens.add(string.substring(start).trim());
			}

			for (var token : tokens)
				value.add(unquote(token));
			return true;
		}

		@Override
		public String toString() {
			if (value.isEmpty())
				return "{}";
			else {
				var buf = new StringBuilder();
				buf.append("{");
				var first = true;
				for (var string : value) {
					if (first)
						first = false;
					else
						buf.append(",");
					buf.append("'");
					buf.append(string.replace("'", "''"));
					buf.append("'");
				}
				buf.append("}");
				return buf.toString();
			}
		}

		public Set<String> getValue() {
			return value;
		}

		public void setValue(Collection<String> value) {
			this.value.clear();
			this.value.addAll(value);
		}

		private String unquote(String s) {
			if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
				s = s.substring(1, s.length() - 1);
				return s.replace("''", "'");
			}
			return s;
		}
	}

	private static boolean isKeyFirst(char c) {
		return (c >= 'A' && c <= 'Z')
			   || (c >= 'a' && c <= 'z')
			   || c == '_' || c == '.' || c == '%';
	}

	private static boolean isKeyRest(char c) {
		return isKeyFirst(c) || (c >= '0' && c <= '9');
	}

	public static void main(String[] args) {
		var string = """
				[&height=0.008196721311605872,
				height_95%_HPD={0.008196721311605815,0.008196721311606037},
				height_median=0.008196721311605926,
				height_range={0.008196721311605815,0.008196721311606037},
				length=0.0365083832461953,
				length_95%_HPD={0.005587810482868272,0.06652752101802084},
				length_median=0.035246750639436775,
				length_range={7.11282670166069E-5,0.10735663564477493},
				type=OtherEuropean,
				type.prob=1.0,type.set={OtherEuropean},
				type.set.prob={1.0},
				IS={1,3-8,11},
				single='bla''s bla',
				QUOTED={'1','yes=?','no,no,no'}
				]""";

		System.err.println("Input:\n" + string);

		var nodeCommentData = new NodeCommentData();
		nodeCommentData.parse(string.replaceAll("\n", ""));
		System.err.println("nodeCommentData:\n");
		for (var entry : nodeCommentData.values().entrySet()) {
			System.err.println(entry.getKey() + ": " + entry.getValue());
		}

		System.err.println("String:\n" + nodeCommentData.toCommentString());

	}
}
