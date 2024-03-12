/*
 * TraitsNexusInput.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.IOExceptionWithLineNumber;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.SetsBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * SETS nexus input
 * Daniel Huson, 9.2022
 */
public class SetsNexusInput extends NexusIOBase implements INexusInput<SetsBlock> {
	public static final String SYNTAX = """
			BEGIN SETS;
				[TITLE {title};]
				[TAXSET {name}={list of names and/or IDS};]
				...
				[CHARSET {name}={list of positions};]
				...
			END;
			""";

	public String getSyntax() {
		return SYNTAX;
	}

	public static final String DESCRIPTION = """
			This block represents a collection of taxon sets and/or character sets.
			""";

	@Override
	public List<String> parse(NexusStreamParser np, TaxaBlock taxaBlock, SetsBlock setBlock) throws IOException {
		setBlock.clear();

		np.matchBeginBlock("SETS");
		parseTitleAndLink(np);

		while (np.peekMatchAnyTokenIgnoreCase("taxset charset")) {
			var which = np.getWordMatchesIgnoringCase("taxset charset");
			var name = np.getWordRespectCase();
			np.matchIgnoreCase("=");
			var tokens = new ArrayList<>(Arrays.asList(StringUtils.splitFurther(np.getTokensFileNamePunctuation(null, ";").trim().split("\\s"), "-")));
			if (which.equals("taxset")) {
				var set = new BitSet();
				for (int i = 0; i < tokens.size(); i++) {
					var range = getRange(i, tokens, false);
					if (range != null) {
						set.or(range);
						i += 2;
					} else if (NumberUtils.isInteger(tokens.get(i))) {
						set.set(NumberUtils.parseInt(tokens.get(i)));
					} else {
						var id = taxaBlock.indexOf(tokens.get(i));
						if (id == -1)
							throw new IOExceptionWithLineNumber(np.lineno(), "Unknown taxon: " + tokens.get(i));
						set.set(id);
					}
				}
				setBlock.getTaxSets().add(new SetsBlock.TaxSet(name, set));
			} else // charset
			{
				var set = new BitSet();
				for (int i = 0; i < tokens.size(); i++) {
					var range = getRange(i, tokens, true);
					if (range != null) {
						set.or(range);
						i += 2;
					} else if (NumberUtils.isInteger(tokens.get(i))) {
						set.set(NumberUtils.parseInt(tokens.get(i)));
					} else {
						throw new IOExceptionWithLineNumber(np.lineno(), "Number expected, got: " + tokens.get(i));
					}
				}
				setBlock.getCharSets().add(new SetsBlock.CharSet(name, set));
			}
		}
		np.matchEndBlock();
		return List.of();
	}

	private BitSet getRange(int i, ArrayList<String> tokens, boolean allowModulo) {
		if (i + 2 < tokens.size() && NumberUtils.isInteger(tokens.get(i)) && tokens.get(i + 1).equals("-")) {
			var third = tokens.get(i + 2).trim();
			if (allowModulo && third.endsWith("\\3") && NumberUtils.isInteger(third.substring(0, third.length() - 2))) {
				var a = NumberUtils.parseInt(tokens.get(i));
				var b = NumberUtils.parseInt(third.substring(0, third.length() - 2));
				var set = new BitSet();
				for (var t = a; t <= b; t += 3)
					set.set(t);
				return set;
			} else if (NumberUtils.isInteger(third)) {
				var a = NumberUtils.parseInt(tokens.get(i));
				var b = NumberUtils.parseInt(third);
				var set = new BitSet();
				for (var t = a; t <= b; t++)
					set.set(t);
				return set;
			}
		}
		return null;
	}
}
