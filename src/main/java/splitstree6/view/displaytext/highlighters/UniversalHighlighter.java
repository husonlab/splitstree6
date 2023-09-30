/*
 * UniversalHighlighter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.displaytext.highlighters;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Universal highlighter
 * Daria Evseeva, 2019
 */
public class UniversalHighlighter implements Highlighter.IHighlighter {
	private static final String PAREN_PATTERN = "[()<>|]";
	private static final String BRACE_PATTERN = "[{}]";
	private static final String COMMENT_PATTERN = "(^|\\n)\\s*#[^\n]*";
	private static final String HEADING_PATTERN = "(^|\\n)[\\pL\\s]*:(\n|$)";
	private static final String FASTA_COMMENT_PATTERN = "(^|\\n);[^\n]*";
	private static final String FASTA_PATTERN = "(^|\\n)>[^\n]*";
	private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
	private static final String NUMBER_PATTERN = "\\b-?\\d+(\\.\\d+)?(E-?\\d+)?(E\\+?\\d+)?(E?\\d+)?(e-?\\d+)?(e\\+?\\d+)?(e?\\d+)?|%";
	private static final String URL_PATTERN = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]\\b";

	private static final Pattern PATTERN = Pattern.compile(
			"(?<PAREN>" + PAREN_PATTERN + ")"
			+ "|(?<BRACE>" + BRACE_PATTERN + ")"
			+ "|(?<STRING>" + STRING_PATTERN + ")"
			+ "|(?<FASTACOMMENT>" + FASTA_COMMENT_PATTERN + ")"
			+ "|(?<FASTA>" + FASTA_PATTERN + ")"
			+ "|(?<COMMENT>" + COMMENT_PATTERN + ")"
			+ "|(?<NUMBER>" + NUMBER_PATTERN + ")"
			+ "|(?<URL>" + URL_PATTERN + ")"
			+ "|(?<HEADING>" + HEADING_PATTERN + ")"

	);

    @Override
    public StyleSpans<Collection<String>> computeHighlighting(String text) {
        var matcher = PATTERN.matcher(text);
        var lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder
                = new StyleSpansBuilder<>();

        while (matcher.find()) {
			var styleClass =
					matcher.group("URL") != null ? "url" :
							matcher.group("NUMBER") != null ? "number" :
									matcher.group("PAREN") != null ? "paren" :
											matcher.group("BRACE") != null ? "brace" :
													matcher.group("HEADING") != null ? "heading" :
															matcher.group("STRING") != null ? "string" :
																	matcher.group("COMMENT") != null ? "comment" :
																			matcher.group("FASTACOMMENT") != null ? "fasta-comment" :
																					matcher.group("FASTA") != null ? "fasta" :
																							null; /* never happens */
			assert styleClass != null;
			spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
