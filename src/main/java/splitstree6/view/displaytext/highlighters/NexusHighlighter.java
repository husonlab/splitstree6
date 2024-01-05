/*
 * NexusHighlighter.java Copyright (C) 2024 Daniel H. Huson
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
 * Nexus highlighter
 * Daria Evseeva, 2019
 */
public class NexusHighlighter implements Highlighter.IHighlighter {
	private static final boolean verbose = false;

	private static final String[] KEYWORDS = new String[]{
			"begin", "end", "endblock",
			"dimensions", "matrix",
			"format", "title", "matrix",
			"properties", "cycle", "draw", "options", "properties", "type", "tect"
	};

	private static final String[] INNER_KEYWORDS = new String[]{
			"translate", "vertices", "vlabels", "edges", "elabels",
			"displaylabels", "taxlabels", "taxset", "charset"
	};

	private static final String[] BLOCKS = new String[]{
			"data", "taxa", "characters", "distances", "trees", "sets",
			"splits", "network", "traits", "analysis", "viewer", "reportFairProportions",
			"splitstree6", "traits"
	};

	private static final String KEYWORD_PATTERN = "(?<KEYWORDSLINE>(?<KEYWORD>(?i)\\b(" + String.join("|", KEYWORDS) + ")\\b)" +
												  "(?<OPTION>(?i)(?!\\h+" + String.join("|\\h+", BLOCKS) + ")[^;]*;(?!\\h*\\R*end))?)";
	// everything between keyword and semicolon not ending with "end" and not blocks word

	private static final String BLOCK_PATTERN = "(?i)\\b(" + String.join("|", BLOCKS) + ")\\b";
	private static final String NETWORK_KEYWORDS_PATTERN = "(?i)\\b(" + String.join("|", INNER_KEYWORDS) + ")\\b";
	private static final String PAREN_PATTERN = "[()]";
	private static final String COMMENT_PATTERN = "\\[(.|\\R)*?]";
	private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";

	private static final Pattern PATTERN = Pattern.compile(
			"(" + KEYWORD_PATTERN + ")"
			+ "|(?<PAREN>" + PAREN_PATTERN + ")"
			+ "|(?<STRING>" + STRING_PATTERN + ")"
			+ "|(?<COMMENT>" + COMMENT_PATTERN + ")"
			+ "|(?<BLOCK>" + BLOCK_PATTERN + ")"
			+ "|(?<NK>" + NETWORK_KEYWORDS_PATTERN + ")"
	);

	@Override
	public StyleSpans<Collection<String>> computeHighlighting(String text) {
		//System.err.println("collapsingActive ->"+collapsingActive);

		var matcher = PATTERN.matcher(text);
		int lastKwEnd = 0;
		StyleSpansBuilder<Collection<String>> spansBuilder
				= new StyleSpansBuilder<>();

		while (matcher.find()) {

			//System.err.println(matcher.group(0));
			if (matcher.group(0).equalsIgnoreCase("begin")) {
				if (verbose)
					System.err.println(matcher.group(0) + matcher.start(0));

			}
			if (matcher.group(0).equalsIgnoreCase("end;")) { //todo add endblock
				if (verbose)
					System.err.println(matcher.group(0) + matcher.end(0));
			}

			var styleClass = matcher.group("KEYWORDSLINE") != null ? "keyword" :
					matcher.group("BLOCK") != null ? "block" :
							matcher.group("NK") != null ? "keyword" :
									matcher.group("PAREN") != null ? "paren" :
											matcher.group("COMMENT") != null ? "comment" :
													matcher.group("STRING") != null ? "string" :
															null;

			spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);

			if (styleClass != null && styleClass.equals("keyword")) {
				spansBuilder.add(Collections.singleton("keyword"),
						matcher.end("NK") - matcher.start("NK"));
				spansBuilder.add(Collections.singleton("keyword"),
						matcher.end("KEYWORD") - matcher.start("KEYWORD"));
				if (matcher.group("OPTION") != null && matcher.group("BLOCK") == null)
					spansBuilder.add(Collections.singleton("option"),
							matcher.end("OPTION") - matcher.start("OPTION"));
			} else
				spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());

			lastKwEnd = matcher.end();
		}
		spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
		return spansBuilder.create();
	}
}
