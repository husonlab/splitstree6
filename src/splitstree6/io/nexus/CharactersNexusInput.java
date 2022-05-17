/*
 * CharactersNexusInput.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.fx.window.NotificationManager;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.CharactersBlock;
import splitstree6.data.CharactersFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.*;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.*;

/**
 * nexus input parser
 * Daniel Huson, 2.2018
 */
public class CharactersNexusInput extends NexusIOBase implements INexusInput<CharactersBlock> {
	private boolean ignoreMatrix = false;
    private final boolean treatUnknownAsError = false;

	public static final String SYNTAX = """
			BEGIN CHARACTERS;
				[TITLE {title};]
				[LINK {type} = {title};]
				DIMENSIONS [NTAX=number-of-taxa] NCHAR=number-of-characters;
				[FORMAT
					[DATATYPE={STANDARD|DNA|RNA|PROTEIN|MICROSAT}]
					[RESPECTCASE]
					[MISSING=symbol]
					[GAP=symbol]
					[MatchChar=symbol]
					[SYMBOLS="symbol symbol ..."]
					[LABELS={NO|LEFT}]
					[TRANSPOSE={NO|YES}]
					[INTERLEAVE={NO|YES}]
					[TOKENS=NO]
				;]
				[CHARWEIGHTS wgt_1 wgt_2 ... wgt_nchar;]
				[CHARSTATELABELS character-number [ character-name ][ /state-name [ state-name... ] ], ...;]
				MATRIX
					sequence data in specified format
				;
			END;
			""";

	@Override
	public String getSyntax() {
		return SYNTAX;
	}

	/**
	 * parse a characters block
	 *
	 * @return taxon names, if found
	 */
	@Override
	public List<String> parse(NexusStreamParser np, TaxaBlock taxa, CharactersBlock charactersBlock) throws IOException {
		charactersBlock.clear();

		final boolean hasTaxonNames = taxa.getLabels().size() > 0;

		final var format = charactersBlock.getFormat();

		if (np.peekMatchIgnoreCase("#nexus"))
			np.matchIgnoreCase("#nexus");

		if (np.peekMatchIgnoreCase("begin CHARACTERS"))
			np.matchBeginBlock("CHARACTERS");
		else if (np.peekMatchIgnoreCase("begin DATA"))
			np.matchBeginBlock("DATA");

		parseTitleAndLink(np);

		final int ntax;
		final int nchar;

		if (taxa.getNtax() == 0) {
			np.matchIgnoreCase("dimensions ntax=");
			ntax = np.getInt(0, Integer.MAX_VALUE);
			np.matchIgnoreCase("nchar=");
			nchar = np.getInt(0, Integer.MAX_VALUE);
			charactersBlock.setDimension(ntax, nchar);
			np.matchIgnoreCase(";");
		} else {
			np.matchIgnoreCase("dimensions");
			if (np.peekMatchIgnoreCase("ntax"))
				np.matchIgnoreCase("ntax=" + taxa.getNtax());
			ntax = taxa.getNtax();
			np.matchIgnoreCase("nchar=");
			nchar = np.getInt(1, Integer.MAX_VALUE);
			charactersBlock.setDimension(ntax, nchar);
			np.matchIgnoreCase(";");
		}

		if (np.peekMatchIgnoreCase("PROPERTIES")) { // legacy: SPlitsTree4 might report this, we ignore it
			final var tokens = np.getTokensLowerCase("properties", ";");
			np.findIgnoreCase(tokens, "gammaShape=", Float.MAX_VALUE);
			np.findIgnoreCase(tokens, "PINVAR=", Float.MAX_VALUE);
			if (tokens.size() != 0)
				throw new IOExceptionWithLineNumber(np.lineno(), "'" + tokens + "' unexpected in PROPERTIES");
		}

		if (np.peekMatchIgnoreCase("FORMAT")) {
			final var formatTokens = np.getTokensLowerCase("FORMAT", ";");
			{
				final var dataType = np.findIgnoreCase(formatTokens, "dataType=", StringUtils.toString(CharactersType.values(), " ") + " nucleotide", CharactersType.Unknown.toString());
				charactersBlock.setDataType(dataType.equalsIgnoreCase("nucleotide") ? CharactersType.DNA : CharactersType.valueOfIgnoreCase(dataType));
			}

			// we ignore respect case:
			{
				var respectCase = np.findIgnoreCase(formatTokens, "respectCase=yes", true, false);
				respectCase = np.findIgnoreCase(formatTokens, "respectCase=no", false, respectCase);
				respectCase = np.findIgnoreCase(formatTokens, "respectCase", true, respectCase);
				respectCase = np.findIgnoreCase(formatTokens, "no respectCase", false, respectCase);
				if (respectCase)
					System.err.println("WARNING: Format 'RespectCase' not implemented. All character-states will be converted to lower case");
			}

			charactersBlock.setMissingCharacter(Character.toLowerCase(np.findIgnoreCase(formatTokens, "missing=", null, '?')));
			charactersBlock.setGapCharacter(Character.toLowerCase(np.findIgnoreCase(formatTokens, "gap=", null, '-')));

			{
				var nomatchchar = np.findIgnoreCase(formatTokens, "no matchChar", true, false);
				if (nomatchchar)
					format.setOptionMatchCharacter((char) 0);
			}
			format.setOptionMatchCharacter(np.findIgnoreCase(formatTokens, "matchChar=", null, (char) 0));

			{
				var symbols = np.findIgnoreCase(formatTokens, "symbols=", "\"", "\"", charactersBlock.getSymbols());
				if (charactersBlock.getDataType() == CharactersType.Standard || charactersBlock.getDataType() == CharactersType.Microsat || charactersBlock.getDataType() == CharactersType.Unknown) {
					charactersBlock.setSymbols(symbols.replaceAll("\\s", "").toLowerCase());
				}
			}

			{
				var labels = np.findIgnoreCase(formatTokens, "labels=no", false, true);
				labels = np.findIgnoreCase(formatTokens, "labels=left", true, labels);
				labels = np.findIgnoreCase(formatTokens, "labels=yes", true, labels);
				labels = np.findIgnoreCase(formatTokens, "no labels", false, labels);
				labels = np.findIgnoreCase(formatTokens, "labels", true, labels);
				format.setOptionLabels(labels);

				if (ntax == 0 && !format.isOptionLabels())
					throw new IOExceptionWithLineNumber("Format 'no labels' invalid because no taxLabels given in TAXA block", np.lineno());
			}

			{
				var transpose = np.findIgnoreCase(formatTokens, "transpose=no", false, false);
				transpose = np.findIgnoreCase(formatTokens, "transpose=yes", true, transpose);
				transpose = np.findIgnoreCase(formatTokens, "no transpose", false, transpose);
				transpose = np.findIgnoreCase(formatTokens, "transpose", true, transpose);
				format.setOptionTranspose(transpose);

				var interleave = np.findIgnoreCase(formatTokens, "interleave=no", false, false);
				interleave = np.findIgnoreCase(formatTokens, "interleave=yes", true, interleave);
				interleave = np.findIgnoreCase(formatTokens, "no interleave", false, interleave);
				interleave = np.findIgnoreCase(formatTokens, "interleave", true, interleave);
				format.setOptionInterleave(interleave);

				var tokens = np.findIgnoreCase(formatTokens, "tokens=no", false, false);
				tokens = np.findIgnoreCase(formatTokens, "tokens=yes", true, tokens);
				tokens = np.findIgnoreCase(formatTokens, "no tokens", false, tokens);
				tokens = np.findIgnoreCase(formatTokens, "tokens", true, tokens);
				format.setOptionTokens(tokens);

				var diploid = np.findIgnoreCase(formatTokens, "diploid=no", false, false);
				diploid = np.findIgnoreCase(formatTokens, "diploid=yes", true, diploid);
				diploid = np.findIgnoreCase(formatTokens, "diploid", true, diploid);
				charactersBlock.setDiploid(diploid);
			}

			if (formatTokens.size() != 0)
				throw new IOExceptionWithLineNumber("Unexpected in FORMAT: '" + StringUtils.toString(formatTokens, " ") + "'", np.lineno());
		}

		if (np.peekMatchIgnoreCase("CharWeights")) {
			np.matchIgnoreCase("CharWeights");
			var charWeights = new double[nchar + 1];
			for (var i = 1; i <= nchar; i++)
				charWeights[i] = np.getDouble();
			np.matchIgnoreCase(";");
			charactersBlock.setCharacterWeights(charWeights);
		} else
			charactersBlock.setCharacterWeights(null);
		// adding CharStateLabels

		charactersBlock.setCharLabeler(null);
		charactersBlock.setStateLabeler(null);
		if (np.peekMatchIgnoreCase("CharStateLabels")) { // todo: is false for ferment4-diploid (microsat data)
			np.matchIgnoreCase("CharStateLabels");
			switch (charactersBlock.getDataType()) {
                case Protein -> charactersBlock.setStateLabeler(new ProteinStateLabeler());
                case Microsat -> charactersBlock.setStateLabeler(new MicrostatStateLabeler());
                case Unknown -> charactersBlock.setStateLabeler(new StandardStateLabeler(nchar, charactersBlock.getMissingCharacter(), format.getOptionMatchCharacter(), charactersBlock.getGapCharacter()));
            }

			charactersBlock.setCharLabeler(new HashMap<>());
			readCharStateLabels(np, charactersBlock.getCharLabeler(), charactersBlock.getStateLabeler());
			np.matchIgnoreCase(";");

			if (charactersBlock.getSymbols() == null || charactersBlock.getSymbols().length() == 0)
				charactersBlock.setSymbols(StringUtils.toString(charactersBlock.getCharLabeler().keySet(), ""));
		}

		List<String> taxonNamesFound;
		final var unknownStates = new TreeSet<Character>();
		{
			np.matchIgnoreCase("MATRIX");
			if (!isIgnoreMatrix()) {
				if (!format.isOptionTranspose() && !format.isOptionInterleave()) {
					taxonNamesFound = readMatrix(np, hasTaxonNames, taxa, charactersBlock, format, unknownStates);
				} else if (format.isOptionTranspose() && !format.isOptionInterleave()) {
					taxonNamesFound = readMatrixTransposed(np, hasTaxonNames, taxa, charactersBlock, format, unknownStates);
				} else if (!format.isOptionTranspose() && format.isOptionInterleave()) {
					taxonNamesFound = readMatrixInterleaved(np, hasTaxonNames, taxa, charactersBlock, format, unknownStates);
				} else
					throw new IOExceptionWithLineNumber(np.lineno(), "can't read matrix!");
				np.matchIgnoreCase(";");
			} else
				taxonNamesFound = new ArrayList<>();
		}
		np.matchEndBlock();

		if (unknownStates.size() > 0)  // warn that stuff has been replaced!
		{
			NotificationManager.showWarning("Unknown states encountered in matrix:\n" + StringUtils.toString(unknownStates, " ") + "\n"
											+ "All replaced by the gap-char '" + charactersBlock.getGapCharacter() + "'");
		}

		return taxonNamesFound;
	}

	/**
	 * read the matrix
	 */
	private ArrayList<String> readMatrix(NexusStreamParser np, boolean hasTaxonNames, TaxaBlock taxa, CharactersBlock characters, CharactersFormat format,
										 Set<Character> unknownStates) throws IOException {
		final var checkStates = characters.getDataType() == CharactersType.Protein ||
								characters.getDataType() == CharactersType.DNA || characters.getDataType() == CharactersType.RNA;
		final var taxonNamesFound = new ArrayList<String>(characters.getNtax());

		for (var t = 1; t <= characters.getNtax(); t++) {
			if (format.isOptionLabels()) {
				if (hasTaxonNames) {
					np.matchLabelRespectCase(taxa.getLabel(t));
					taxonNamesFound.add(taxa.getLabel(t));
				} else
					taxonNamesFound.add(np.getLabelRespectCase());
			}

			final String str;
			var length = 0;

			if (format.isOptionTokens()) {
				if (characters.getStateLabeler() == null)
					characters.setStateLabeler(new StandardStateLabeler(characters.getNchar(), characters.getMissingCharacter(), format.getOptionMatchCharacter(), characters.getGapCharacter()));
				final var tokenList = new ArrayList<String>();
				while (length < characters.getNchar()) {
					final var word = np.getWordRespectCase();
					tokenList.add(word);
					length++;
				}
				// System.err.print("StateLabeler is " + characters.getStateLabeler());
				str = characters.getStateLabeler().parseSequence(tokenList, 1, false);
			} else {
				final var buf = new StringBuilder();
				while (length < characters.getNchar()) {
					final String word = np.getWordRespectCase();
					length += word.length();
					buf.append(word);
				}
				str = buf.toString().toLowerCase(); // @todo: until we know that respectcase works, fold all characters to lower-case
			}


			if (str.length() != characters.getNchar())
				throw new IOExceptionWithLineNumber(np.lineno(), "wrong number of chars: " + str.length() + ", expected: " + characters.getNchar());

			for (var i = 1; i <= str.length(); i++) {

				//TODo clean this up.
				final var ch = str.charAt(i - 1);

				if (ch == format.getOptionMatchCharacter()) {
					if (t == 1)
						throw new IOExceptionWithLineNumber(np.lineno(), "matchchar illegal in first sequence");
					else
						characters.set(t, i, characters.get(1, i));
				} else {
					if (!checkStates || isValidState(characters, format, ch))
						characters.set(t, i, ch);
					else if (treatUnknownAsError)
						throw new IOExceptionWithLineNumber(np.lineno(), "invalid character: " + ch);
					else  // don't know this, replace by gap
					{
						characters.set(t, i, characters.getGapCharacter());
						unknownStates.add(ch);
					}
				}
			}
		}
		return taxonNamesFound;
	}

	/**
	 * read the matrix
	 */
	private ArrayList<String> readMatrixTransposed(NexusStreamParser np, boolean hasTaxonNames, TaxaBlock taxa, CharactersBlock characters, CharactersFormat format,
												   Set<Character> unknownStates) throws IOException {
		final var checkStates = characters.getDataType() == CharactersType.Protein ||
								characters.getDataType() == CharactersType.DNA || characters.getDataType() == CharactersType.RNA;
		final var taxonNamesFound = new ArrayList<String>(characters.getNtax());

		if (format.isOptionLabels()) {
			for (var t = 1; t <= characters.getNtax(); t++) {
				if (hasTaxonNames) {
					np.matchLabelRespectCase(taxa.getLabel(t));
					taxonNamesFound.add(taxa.getLabel(t));
				} else
					taxonNamesFound.add(np.getLabelRespectCase());
			}
		}
		// read the matrix:
		for (var i = 1; i <= characters.getNchar(); i++) {
			var length = 0;

			final String str;
			if (format.isOptionTokens()) {
				if (characters.getStateLabeler() == null)
					characters.setStateLabeler(new StandardStateLabeler(characters.getNchar(), characters.getMissingCharacter(), format.getOptionMatchCharacter(), characters.getGapCharacter()));
				final var tokenList = new ArrayList<String>();
				while (length < characters.getNtax()) {
					var tmp = np.getWordRespectCase();
					tokenList.add(tmp);
					length++;
				}
				str = characters.getStateLabeler().parseSequence(tokenList, i, true);
			} else {
				final var buf = new StringBuilder();
				while (length < characters.getNtax()) {
					var tmp = np.getWordRespectCase();
					length += tmp.length();
					buf.append(tmp);
				}
				str = buf.toString();
			}


			if (str.length() != characters.getNtax())
				throw new IOExceptionWithLineNumber(np.lineno(), "wrong number of chars: " + str.length());
			for (var t = 1; t <= characters.getNtax(); t++) {
				//char ch = str.getRowSubset(t - 1);
				// @todo: until we know that respectcase works, fold all characters to lower-case
				char ch;
				if (!format.isOptionTokens())
					ch = Character.toLowerCase(str.charAt(t - 1));
				else
					ch = str.charAt(t - 1);

				if (ch == format.getOptionMatchCharacter()) {
					if (t == 1)
						throw new IOExceptionWithLineNumber(np.lineno(), "matchchar illegal in first col");
					else
						characters.set(t, i, characters.get(1, i));
				} else {
					if (!checkStates || isValidState(characters, format, ch))
						characters.set(t, i, ch);
					else if (treatUnknownAsError)
						throw new IOExceptionWithLineNumber(np.lineno(), "invalid character: " + ch);
					else  // don't know this, replace by gap
					{
						characters.set(t, i, characters.getGapCharacter());
						unknownStates.add(ch);
					}
				}
			}
		}
		return taxonNamesFound;
	}

	/**
	 * read the matrix
	 */
	private ArrayList<String> readMatrixInterleaved(NexusStreamParser np, boolean hasTaxonNames, TaxaBlock taxa, CharactersBlock characters, CharactersFormat format,
													Set<Character> unknownStates) throws IOException {
		final var checkStates = characters.getDataType() == CharactersType.Protein ||
								characters.getDataType() == CharactersType.DNA || characters.getDataType() == CharactersType.RNA;
		final var taxonNamesFound = new ArrayList<String>(characters.getNtax());

		try {
			var c = 0;
			var firstBlock = true;
			while (c < characters.getNchar()) {
				var lineLength = 0;
				for (var t = 1; t <= characters.getNtax(); t++) {
					if (format.isOptionLabels()) {
						if (!hasTaxonNames) {
							final String name = np.getLabelRespectCase();
							if (firstBlock) {
								taxonNamesFound.add(name);
							}
						} else {
							np.matchLabelRespectCase(taxa.getLabel(t));
							taxonNamesFound.add(taxa.getLabel(t));
						}
						np.setEolIsSignificant(true);
					} else {
						np.setEolIsSignificant(true);
						if (t == 1 && np.nextToken() != StreamTokenizer.TT_EOL) //cosume eol
							throw new IOExceptionWithLineNumber("EOL expected", np.lineno());
					}

					final String str;
					try {
						if (format.isOptionTokens()) {
							if (characters.getStateLabeler() == null)
								characters.setStateLabeler(new StandardStateLabeler(characters.getNchar(), characters.getMissingCharacter(), format.getOptionMatchCharacter(), characters.getGapCharacter()));
							final var tokenList = new ArrayList<String>();
							while (np.peekNextToken() != StreamTokenizer.TT_EOL && np.peekNextToken() != StreamTokenizer.TT_EOF) {
								tokenList.add(np.getWordRespectCase());
							}
							str = characters.getStateLabeler().parseSequence(tokenList, c + 1, false);
						} else {
							final var buf = new StringBuilder();
							while (np.peekNextToken() != StreamTokenizer.TT_EOL && np.peekNextToken() != StreamTokenizer.TT_EOF) {
								buf.append(np.getWordRespectCase());
							}
							str = buf.toString();
						}
						np.nextToken(); // consume the eol
					} finally {
						np.setEolIsSignificant(false);
					}

					if (t == 1) { // first line in this block
						lineLength = str.length();
					} else if (lineLength != str.length())
						throw new IOExceptionWithLineNumber("Wrong number of chars: " + str.length() + " should be: " + lineLength, np.lineno());

					for (var d = 1; d <= lineLength; d++) {
						var i = c + d;
						if (i > characters.getNchar())
							throw new IOExceptionWithLineNumber(np.lineno(), "too many chars");

//char ch = str.getRowSubset(d - 1);
// @todo: until we now that respectcase works, fold all characters to lower-case
						char ch;
						if (!format.isOptionTokens())
							ch = Character.toLowerCase(str.charAt(d - 1));
						else
							ch = str.charAt(d - 1);

						if (ch == format.getOptionMatchCharacter()) {
							if (t == 1) {
								throw new IOExceptionWithLineNumber("matchChar illegal in first sequence", np.lineno());
							} else
								characters.set(t, i, characters.get(1, i));
						} else {
							if (!checkStates || isValidState(characters, format, ch))
								characters.set(t, i, ch);
							else if (treatUnknownAsError)
								throw new IOExceptionWithLineNumber("Invalid character: " + ch, np.lineno());
							else  // don't know this, replace by gap
							{
								characters.set(t, i, characters.getGapCharacter());
								unknownStates.add(ch);
							}
						}
					}
				}
				firstBlock = false;
				c += lineLength;
			}
		} finally {
			np.setEolIsSignificant(false);
		}
		return taxonNamesFound;
	}

	/**
	 * read the character state labels
	 */
	private void readCharStateLabels(NexusStreamParser np, Map<Integer, String> charLabeler, StateLabeler stateLabeler) throws IOException {
		while (np.peekNextToken() != (int) ';') {
			var charNumber = np.getInt(); //get the number in front of the label

			// Deal with the fact that it is possible to not have a label for some number.
			if (np.peekNextToken() == ',' || np.peekNextToken() == '/') {
			} else {
				var charLabel = np.getWordRespectCase();   //get the label otherwise
				charLabeler.put(charNumber, charLabel);
			}

			if (np.peekMatchIgnoreCase(",")) {
				np.nextToken(); //Skipping the ',' between labels
			} else if (np.peekMatchIgnoreCase("/")) {
				np.nextToken(); //Skipping the '/' between label and states
				while (np.peekNextToken() != (int) ',' && np.peekNextToken() != (int) ';') {
					stateLabeler.token2char(charNumber, np.getWordRespectCase());
				}
				if (np.peekNextToken() == (int) ',')
					np.nextToken(); //Skipping the ',' between labels
			}
		}
	}


	/**
	 * Checks if the character is a valid state symbol. Will always return
	 * true if the datatype is UNKNOWN.
	 *
	 * @param ch character to check
	 * @return boolean  true if character consistent with the symbol list of the block's datatype
	 */
	private boolean isValidState(CharactersBlock characters, CharactersFormat format, char ch) {
		return characters.getDataType() == CharactersType.Unknown || ch == characters.getMissingCharacter() || ch == characters.getGapCharacter() || ch == format.getOptionMatchCharacter()
			   || characters.getSymbols().indexOf(ch) >= 0
			   || (characters.getDataType() == CharactersType.DNA && AmbiguityCodes.isAmbiguityCode(ch));
	}

	public boolean isIgnoreMatrix() {
		return ignoreMatrix;
	}

	public void setIgnoreMatrix(boolean ignoreMatrix) {
		this.ignoreMatrix = ignoreMatrix;
	}
}
