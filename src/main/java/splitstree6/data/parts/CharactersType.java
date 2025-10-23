/*
 *  CharactersType.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.data.parts;

import jloda.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * characters type
 * Daniel Huson, 1/16/17.
 */
public enum CharactersType {
	Standard("01"),
	DNA("acgt"),
	DNAwithAmbiguityCodes("acgtryswkmbdhvn"),
	RNA("acgu"),
	RNAwithAmbiguityCodes("acgury"),
	Protein("arndcqeghilkmfpstwyvbzx*"),
	Microsat(""),
	Unknown("");

	private final String symbols;

	CharactersType(String symbols) {
		this.symbols = symbols;
	}

	/**
	 * get symbols for a characters type
	 *
	 * @return symbols
	 */
	public String getSymbols() {
		return this.symbols;
	}

	public static CharactersType valueOfIgnoreCase(String str) {
		for (CharactersType type : values()) {
			if (type.toString().equalsIgnoreCase(str))
				return type;
		}
		return Unknown;
	}

	public boolean isNucleotides() {
		return this == DNA || this == RNA || this == RNAwithAmbiguityCodes || this == DNAwithAmbiguityCodes;
	}

	public static CharactersType guessType(String sequence) {
		var set = new HashSet<Character>();
		for (int i = 0; i < sequence.length(); i++) {
			set.add(sequence.charAt(i));
		}
		return guessType(set);
	}

	public static CharactersType guessType(Set<Character> set) {
		var alphabet = StringUtils.toString(set, "").toLowerCase();

		var result = Unknown;
		var best = 0.0;

		if (DNAwithAmbiguityCodes.containsAllLetters(alphabet))
			return DNAwithAmbiguityCodes;
		else if (RNAwithAmbiguityCodes.containsAllLetters(alphabet))
			return RNAwithAmbiguityCodes;
		else if (Protein.containsAllLetters(alphabet))
			return Protein;
		else return Unknown;
	}

	private boolean containsAllLetters(String alphabet) {
		var matches = 0;
		for (var ch : alphabet.toCharArray()) {
			ch = Character.toLowerCase(ch);
			if (Character.isLetter(ch) && ch != 'x' && ch != 'n') {
				if (symbols.indexOf(ch) == -1)
					return false;
				else matches++;
			}
		}
		return matches > 1;
	}

	public static Set<Character> intersection(String a, String b) {
		var set = new HashSet<Character>();
		for (int i = 0; i < a.length(); i++) {
			var ch = a.charAt(i);
			if (Character.isLetterOrDigit(ch) && b.indexOf(ch) >= 0) {
				set.add(ch);
			}
		}
		return set;
	}

	public static Set<Character> union(String... strs) {
		var set = new HashSet<Character>();
		for (var a : strs) {
			for (int i = 0; i < a.length(); i++) {
				var ch = a.charAt(i);
				if (Character.isLetterOrDigit(ch))
					set.add(ch);
			}
		}
		return set;
	}
}
