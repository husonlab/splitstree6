package splitstree6.view.displaytext.highlighters;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NexusHighlighter implements Highlighter.IHighlighter {
	private static final boolean verbose = false;

	// Option B: namespace Nexus styles
	private static final String S_KEYWORD = "nexus-keyword";
	private static final String S_INNER_KEYWORD = "nexus-inner-keyword";
	private static final String S_BLOCK = "nexus-block";
	private static final String S_OPTION = "nexus-option";
	private static final String S_PAREN = "nexus-paren";
	private static final String S_COMMENT = "nexus-comment";
	private static final String S_STRING = "nexus-string";

	private static final String[] KEYWORDS = new String[]{
			"begin", "end", "endblock",
			"dimensions", "matrix",
			"format", "title", "matrix",
			"properties", "cycle", "draw", "options", "properties", "type", "tect"
	};

	private static final String[] INNER_KEYWORDS = new String[]{
			"translate", "vertices", "vlabels", "edges", "elabels",
			"displaylabels", "taxlabels", "taxset", "charset", "charlabels", "charweights", "charstatelabels"
	};

	private static final String[] BLOCKS = new String[]{
			"data", "taxa", "characters", "distances", "trees", "sets",
			"splits", "network", "traits", "analysis", "viewer", "report",
			"splitstree6", "traits"
	};

	private static final String BLOCK_PATTERN = "(?i)\\b(" + String.join("|", BLOCKS) + ")\\b";
	private static final String KEYWORD_PATTERN = "(?i)\\b(" + String.join("|", KEYWORDS) + ")\\b";
	private static final String INNER_KEYWORDS_PATTERN = "(?i)\\b(" + String.join("|", INNER_KEYWORDS) + ")\\b";

	private static final String PAREN_PATTERN = "[()]";
	private static final String COMMENT_PATTERN = "\\[(.|\\R)*?]";

	// If you also want single-quoted Nexus labels highlighted, use this instead:
	// private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
	private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";

	/**
	 * Matches:
	 * - a top-level KEYWORD plus the rest up to semicolon as OPTION (optional)
	 * - or an INNER keyword
	 * - or blocks, parens, strings, comments
	 * <p>
	 * For KEYWORD lines, we capture KEYWORD and OPTION separately so we can style OPTION subparts (blocks).
	 */
	private static final String KEYWORD_LINE_PATTERN =
			"(?<KEYWORDSLINE>(?<KEYWORD>" + KEYWORD_PATTERN + ")" +
			"(?<OPTION>(?i)(?!\\h+" + String.join("|\\h+", BLOCKS) + ")[^;]*;(?!\\h*\\R*end))?)";

	private static final Pattern PATTERN = Pattern.compile(
			KEYWORD_LINE_PATTERN
			+ "|(?<NK>" + INNER_KEYWORDS_PATTERN + ")"
			+ "|(?<BLOCK>" + BLOCK_PATTERN + ")"
			+ "|(?<PAREN>" + PAREN_PATTERN + ")"
			+ "|(?<STRING>" + STRING_PATTERN + ")"
			+ "|(?<COMMENT>" + COMMENT_PATTERN + ")"
	);

	// Used to highlight BLOCK words *inside* the option region (e.g., " begin trees; " -> "trees" as block).
	private static final Pattern BLOCK_IN_OPTION = Pattern.compile(BLOCK_PATTERN);

	@Override
	public StyleSpans<Collection<String>> computeHighlighting(String text) {
		var matcher = PATTERN.matcher(text);
		int lastEnd = 0;

		var spansBuilder = new StyleSpansBuilder<Collection<String>>();

		while (matcher.find()) {

			if (verbose) {
				System.err.println("match: " + matcher.group());
			}

			// gap before this match
			spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);

			if (matcher.group("KEYWORDSLINE") != null) {
				// Style the KEYWORD part
				spansBuilder.add(Collections.singleton(S_KEYWORD),
						matcher.end("KEYWORD") - matcher.start("KEYWORD"));

				// Style OPTION part (if present) but split out BLOCK words inside it
				if (matcher.group("OPTION") != null) {
					int optStart = matcher.start("OPTION");
					int optEnd = matcher.end("OPTION");

					String optionText = text.substring(optStart, optEnd);
					Matcher b = BLOCK_IN_OPTION.matcher(optionText);

					int last = 0;
					while (b.find()) {
						spansBuilder.add(Collections.singleton(S_OPTION), b.start() - last);
						spansBuilder.add(Collections.singleton(S_BLOCK), b.end() - b.start());
						last = b.end();
					}
					spansBuilder.add(Collections.singleton(S_OPTION), optionText.length() - last);
				}

			} else if (matcher.group("NK") != null) {
				spansBuilder.add(Collections.singleton(S_INNER_KEYWORD),
						matcher.end() - matcher.start());

			} else if (matcher.group("BLOCK") != null) {
				spansBuilder.add(Collections.singleton(S_BLOCK),
						matcher.end() - matcher.start());

			} else if (matcher.group("PAREN") != null) {
				spansBuilder.add(Collections.singleton(S_PAREN),
						matcher.end() - matcher.start());

			} else if (matcher.group("COMMENT") != null) {
				spansBuilder.add(Collections.singleton(S_COMMENT),
						matcher.end() - matcher.start());

			} else if (matcher.group("STRING") != null) {
				spansBuilder.add(Collections.singleton(S_STRING),
						matcher.end() - matcher.start());

			} else {
				// Shouldn't happen, but keep it safe
				spansBuilder.add(Collections.emptyList(), matcher.end() - matcher.start());
			}

			lastEnd = matcher.end();
		}

		spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
		return spansBuilder.create();
	}
}