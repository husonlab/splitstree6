/*
 * AmbiguityCodes.java Copyright (C) 2022 Daniel H. Huson
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

import splitstree6.data.CharactersBlock;

public class AmbiguityCodes {
	public static final String CODES = "wrkysmbhdvn";

	/**
	 * gets all nucleotides associated with a given code
	 *
	 * @param code a character coding an ambiguous state
	 * @return all (lowercase) letters associated with the given code, or the nucleotide it self, if not a code
	 */
	public static String getNucleotides(char code) {
        return switch (Character.toLowerCase(code)) {
            case 'w' -> "at";
            case 'r' -> "ag";
            case 'k' -> "gt";
            case 'y' -> "ct";
            case 's' -> "cg";
            case 'm' -> "ac";
            case 'b' -> "cgt";
            case 'h' -> "act";
            case 'd' -> "agt";
            case 'v' -> "acg";
            case 'n' -> "acgt";
            default -> "" + Character.toLowerCase(code); // this is not a code, but a nucleotide
        };
    }

	/**
	 * is the given letter an ambiguity code?
	 *
	 * @param ch char
	 * @return true, if code, false otherwise
	 */
	public static boolean isAmbiguityCode(char ch) {
		return CODES.indexOf(Character.toLowerCase(ch)) != -1;
	}

	/**
	 * does the given character block contains an ambiguity code?
	 *
	 * @param charactersBlock nexus block
	 * @return true, if code, false otherwise
	 */
	public static boolean isAmbiguityCode(CharactersBlock charactersBlock) {
		if (!charactersBlock.getDataType().isNucleotides()) {
			charactersBlock.setHasAmbiguityCodes(false);
			return false;
		}

		for (int t = 0; t < charactersBlock.getNtax(); t++) {
			for (char c : charactersBlock.getRow0(t))
				if (isAmbiguityCode(c)) {
					charactersBlock.setHasAmbiguityCodes(true);
					return true;
				}
		}

		charactersBlock.setHasAmbiguityCodes(false);
		return false;
	}
}
