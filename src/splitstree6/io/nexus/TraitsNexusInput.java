/*
 * TraitsNexusInput.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.util.IOExceptionWithLineNumber;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.data.TraitsNexusFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * taxa nexus input
 * Daniel Huson, 2.2018
 */
public class TraitsNexusInput extends NexusIOBase implements INexusInput<TraitsBlock> {
	public static final String SYNTAX = """
			BEGIN TRAITS;
				[TITLE {title};]
				DIMENSIONS [NTAX=number-of-taxa] NTRAITS=number-of-traits;
				[FORMAT
					[LABELS={YES|NO}]
					[MISSING=symbol]
					[SEPARATOR={COMMA|SEMICOLON|WHITESPACE}]
				;]
				[TRAITLATITUDE  latitude-trait-1  latitude-trait-2 ...  latitude-trait-n;
				 TRAITLONGITUDE longitude-trait-1 longitude-trait-2 ... longitude-trait-n;]
				 TRAITLABELS label-trait-1 label-trait-2 ... label-trait-n;
				MATRIX
					trait data in specified format
				;
			END;
			""";

	public String getSyntax() {
		return SYNTAX;
	}

	@Override
	public List<String> parse(NexusStreamParser np, TaxaBlock taxaBlock, TraitsBlock traitsBlock) throws IOException {
		final ArrayList<String> taxonNamesFound = new ArrayList<>();

		traitsBlock.clear();

		final var format = traitsBlock.getFormat();

		np.matchBeginBlock("TRAITS");
		parseTitleAndLink(np);

		np.matchIgnoreCase("DIMENSIONS");
		final int ntax;
		if (np.peekMatchIgnoreCase("nTax=")) {
			np.matchIgnoreCase("nTax=");
			ntax = np.getInt();
			if (taxaBlock.getNtax() > 0 && ntax != taxaBlock.getNtax())
				throw new IOExceptionWithLineNumber("Wrong number of taxa", np.lineno());
		} else {
			ntax = taxaBlock.getNtax();
			if (ntax == 0)
				throw new IOExceptionWithLineNumber("Can't determine number of taxa", np.lineno());
		}

		np.matchIgnoreCase("nTraits=");
		final int ntraits = np.getInt();
		np.matchIgnoreCase(";");
		traitsBlock.setDimensions(ntax, ntraits);

		if (np.peekMatchIgnoreCase("FORMAT")) {
			final List<String> formatTokens = np.getTokensLowerCase("FORMAT", ";");

			boolean labels = np.findIgnoreCase(formatTokens, "labels=no", false, false);
			labels = np.findIgnoreCase(formatTokens, "labels=yes", true, labels);
			labels = np.findIgnoreCase(formatTokens, "labels=left", true, labels);
			labels = np.findIgnoreCase(formatTokens, "no labels", false, labels);
			labels = np.findIgnoreCase(formatTokens, "labels", true, labels);
			format.setOptionLabel(labels);

			if (ntax == 0 && !format.isOptionLabel())
				throw new IOExceptionWithLineNumber("Format 'no labels' invalid because no taxLabels given in TAXA block", np.lineno());

			final String separator = np.findIgnoreCase(formatTokens, "separator=", StringUtils.toString(TraitsNexusFormat.Separator.values(), " "), "");
			if (separator.length() > 0) {
				format.setOptionSeparator(separator);
			}

			format.setOptionMissingCharacter(Character.toLowerCase(np.findIgnoreCase(formatTokens, "missing=", null, '?')));

			if (formatTokens.size() != 0)
				throw new IOExceptionWithLineNumber("Unexpected in FORMAT: '" + StringUtils.toString(formatTokens, " ") + "'", np.lineno());
		}

		if (np.peekMatchIgnoreCase("TRAITLATITUDE")) {
			np.matchIgnoreCase("TRAITLATITUDE");
			for (int i = 1; i <= ntraits; i++)
				traitsBlock.setTraitLatitude(i, (float) np.getDouble());
			np.matchIgnoreCase(";");
			np.matchIgnoreCase("TRAITLONGITUDE");
			for (int i = 1; i <= ntraits; i++)
				traitsBlock.setTraitLongitude(i, (float) np.getDouble());
			np.matchIgnoreCase(";");
		}

		np.matchIgnoreCase("TRAITLABELS");
		for (int j = 1; j <= ntraits; j++) {
			traitsBlock.setTraitLabel(j, np.getWordRespectCase());
		}
		np.matchIgnoreCase(";");

		np.matchIgnoreCase("MATRIX");

		// need these two to map trait state labels to integer values
		final Map<String, Integer>[] trait2map = new Map[ntraits + 1];
		final int[] trait2count = new int[ntraits + 1];

		for (int i = 1; i <= ntax; i++) {
			int taxonId = i;
			if (format.isOptionLabel()) {
				String taxonName = np.getWordRespectCase();
				if (taxaBlock.getNtax() == ntax) {
					taxonId = taxaBlock.indexOf(taxonName);
					if (taxonId == -1)
						throw new IOExceptionWithLineNumber("Unknown taxon: '" + taxonName, np.lineno());
				}
			}
			for (int traitId = 1; traitId <= ntraits; traitId++) {
				if (traitId > 1) {
					if (format.getOptionSeparator() != TraitsNexusFormat.Separator.WhiteSpace)
						np.matchIgnoreCase(format.getSeparatorString());
				}
				if (np.peekMatchIgnoreCase("" + format.getOptionMissingCharacter())) {
					np.matchIgnoreCase("" + format.getOptionMissingCharacter());
					traitsBlock.setTraitValue(taxonId, traitId, Integer.MAX_VALUE);
				} else {
					final String word = np.getWordRespectCase();

					if (NumberUtils.isInteger(word))
						traitsBlock.setTraitValue(taxonId, traitId, NumberUtils.parseInt(word));
					else {
						Map<String, Integer> map = trait2map[traitId];
						if (map == null) {
							map = new HashMap<>();
							trait2map[traitId] = map;
						}
						Integer value = map.get(word);
						if (value == null) {
							value = (++trait2count[traitId]);
							map.put(word, value);
						}
						traitsBlock.setTraitValue(taxonId, traitId, value);
						traitsBlock.setTraitValueLabel(taxonId, traitId, word);
					}
				}
			}
		}
		np.matchIgnoreCase(";");
		np.matchEndBlock();
		return taxonNamesFound;
	}
}