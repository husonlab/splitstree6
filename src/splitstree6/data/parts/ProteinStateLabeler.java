/*
 * ProteinStateLabeler.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.data.parts;

import java.io.IOException;
import java.util.HashMap;

/**
 * state labeler for protein sequences
 * Original author: David Bryant
 * Daria Evseeva,30.10.2016.
 */
public class ProteinStateLabeler extends StateLabeler {
	private final HashMap<String, Character>[] token2charMaps; //Map from strings to char, one for each site
	private final HashMap<Character, String>[] char2tokenMaps;  //Reverse of the above map

	/**
	 * Constructor
	 */
	public ProteinStateLabeler() {
		final String availableChars = CharactersType.Protein.getSymbols();
		final String[] codes = {"ala", "arg", "asn", "asp", "cys", "gln", "glu", "gly", "his", "ile", "leu",
				"lys", "met", "phe", "pro", "ser", "thr", "trp", "tyr", "val"};

		token2charMaps = new HashMap[1];
		char2tokenMaps = new HashMap[1];
		token2charMaps[0] = new HashMap();
		char2tokenMaps[0] = new HashMap();

		for (int i = 0; i < 20; i++) {
			token2charMaps[0].put(codes[i], availableChars.charAt(i));
			char2tokenMaps[0].put(availableChars.charAt(i), codes[i]);
		}
	}

	@Override
	public char token2char(int site, String token) throws IOException {
		if (token2charMaps[0].containsKey(token))
			return token2charMaps[site].get(token);
		else
			throw new IOException("Unidentified amino acid: " + token);
	}

	@Override
	public String char2token(int site, char ch) {
		return char2tokenMaps[0].get(ch);
	}

	@Override
	public int getMaximumLabelLength() {
		return 3;
	}

	@Override
	public String getSymbolsUsed() {
		return CharactersType.Protein.getSymbols();
	}

	@Override
	public boolean hasStates(int pos) {
		return pos < token2charMaps.length && token2charMaps[pos] != null && token2charMaps[pos].size() > 0;
	}
}
