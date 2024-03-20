/*
 *  XMLHighlighter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.displaytext.highlighters;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XML highlighter
 * Daria Evseeva, 2019
 */
public class XMLHighlighter implements Highlighter.IHighlighter {

	private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)(\\w+[:])?(\\w+)([^<>]*)(\\h*/?>))"
														   + "|(?<COMMENT><!--[^<>]+-->)");

	private static final Pattern ATTRIBUTES = Pattern.compile(
			"((?!xmlns:)\\w+[:])?" + //namespace prefix not equals xmlns:
			"(\\w+\\h*|xmlns:\\w+)" + // attribute name
			"(=)(\\h*\"[^\"]+\")"); // attribute value

	private static final int GROUP_OPEN_BRACKET = 2;
	private static final int GROUP_ELEMENT_NAMESPACE = 3;
	private static final int GROUP_ELEMENT_NAME = 4;
	private static final int GROUP_ATTRIBUTES_SECTION = 5;
	private static final int GROUP_CLOSE_BRACKET = 6;

	// Attributes
	private static final int GROUP_ATTRIBUTE_NAMESPACE = 1;
	private static final int GROUP_ATTRIBUTE_NAME = 2;
	private static final int GROUP_EQUAL_SYMBOL = 3;
	private static final int GROUP_ATTRIBUTE_VALUE = 4;


	@Override
	public StyleSpans<Collection<String>> computeHighlighting(String text) {
		Matcher matcher = XML_TAG.matcher(text);
		int lastKwEnd = 0;
		StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
		while (matcher.find()) {

			spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
			if (matcher.group("COMMENT") != null) {
				spansBuilder.add(Collections.singleton("xml-comment"), matcher.end() - matcher.start());
			} else {
				if (matcher.group("ELEMENT") != null) {
					String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);

					spansBuilder.add(Collections.singleton("xml-tagmark"),
							matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));

					if (matcher.group(GROUP_ELEMENT_NAMESPACE) != null) {
						spansBuilder.add(Collections.singleton("xml-namespace"),
								matcher.end(GROUP_ELEMENT_NAMESPACE) - matcher.end(GROUP_OPEN_BRACKET));
						spansBuilder.add(Collections.singleton("xml-anytag"),
								matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_ELEMENT_NAMESPACE));
					} else {
						spansBuilder.add(Collections.singleton("xml-anytag"),
								matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));
					}

					if (!attributesText.isEmpty()) {

						lastKwEnd = 0;

						Matcher amatcher = ATTRIBUTES.matcher(attributesText);
						while (amatcher.find()) {
							spansBuilder.add(Collections.emptyList(),
									amatcher.start() - lastKwEnd);

							// no namespace found
							if (amatcher.group(GROUP_ATTRIBUTE_NAMESPACE) == null)
								if (amatcher.group(GROUP_ATTRIBUTE_NAME).startsWith("xmlns:"))
									spansBuilder.add(Collections.singleton("xml-namespace"),
											amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
								else
									spansBuilder.add(Collections.singleton("xml-attribute"),
											amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
								// name contains namespace
							else {
								spansBuilder.add(Collections.singleton("xml-namespace"),
										amatcher.end(GROUP_ATTRIBUTE_NAMESPACE) - amatcher.start(GROUP_ATTRIBUTE_NAMESPACE));
								spansBuilder.add(Collections.singleton("xml-attribute"),
										amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.end(GROUP_ATTRIBUTE_NAMESPACE));
							}

							// values
							spansBuilder.add(Collections.singleton("xml-tagmark"),
									amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
							spansBuilder.add(Collections.singleton("xml-avalue"),
									amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
							lastKwEnd = amatcher.end();
						}
						if (attributesText.length() > lastKwEnd)
							spansBuilder.add(Collections.emptyList(), attributesText.length() - lastKwEnd);
					}

					lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);

					spansBuilder.add(Collections.singleton("xml-tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
				}
			}
			lastKwEnd = matcher.end();
		}
		spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
		return spansBuilder.create();
	}
}
