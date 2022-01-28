/*
 * MicrostatStateLabeler.java Copyright (C) 2022 Daniel H. Huson
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

import java.util.TreeSet;

/**
 * State labeler for Microsat data
 * Original author: David Bryant
 * Daria Evseeva,30.10.2016.
 */
public class MicrostatStateLabeler extends StateLabeler {
	private final static int OFFSET = 256; //Offset for chars used to store microsattelite alleles (to avoid conflicts)
	private final TreeSet<Character> charsUsed = new TreeSet<>(); //Set of characters used in microsatelite data.

	@Override
	public char token2char(int site, String token) {
		int val = Integer.parseInt(token);
		char ch = (char) (val + OFFSET);
		charsUsed.add(ch);
		return ch;
	}

	@Override
	public String char2token(int site, char ch) {
		return "" + (((int) ch - OFFSET));
	}

	@Override
	public int getMaximumLabelLength() {
        Character ch = charsUsed.last();
        int maxVal = (int) ch;
        return (Integer.toString(maxVal)).length();
    }

	@Override
	public String getSymbolsUsed() {
		StringBuilder symbols = new StringBuilder();
		for (Object aCharsUsed : charsUsed) {
			symbols.append(((Character) aCharsUsed).charValue());
		}
		return symbols.toString();
	}

	@Override
	public boolean hasStates(int pos) {
		return false;
	}
}
