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
	RNAwithAmbiguityCodes("acguryswkmbdhvn"),
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

	/**
	 * gets those symbols of this data type that do not stand for a state of their own
	 * <p>
	 * The nucleotide types list the ambiguity codes among their symbols, because a code is a legal character in
	 * such an alignment and the readers validate against this list. A code is not a state, though: it stands for
	 * a set of bases and is expanded into them before it ever reaches a frequency matrix. Anything that means
	 * "the states" must therefore ask for the state symbols and not for getSymbols(). Counting the codes as
	 * states gave DNAwithAmbiguityCodes 15 states rather than 4, eleven of whose rows can never be filled, which
	 * made every such frequency matrix singular and left LogDet undefined for every pair of taxa.
	 *
	 * @return the symbols that are not states, or an empty string if every symbol is one
	 */
	public String getNonStateSymbols() {
		return switch (this) {
			case DNA, DNAwithAmbiguityCodes, RNA, RNAwithAmbiguityCodes -> AmbiguityCodes.CODES;
			// 'b' is D or N, 'z' is E or Q and 'x' is any residue, so none of the three is a state; '*' is a stop
			// codon rather than a residue, and ProteinMLDistance says in so many words that such sites are to be
			// ignored. Unlike the nucleotide codes these are not expanded - PairwiseCompare has no machinery for
			// protein ambiguity - so they are treated as missing, which is what excluding them here achieves.
			case Protein -> "bzx*";
			default -> "";
		};
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

		if (DNA.containsAllLetters(alphabet))
			return DNA;
		else if (RNA.containsAllLetters(alphabet))
			return RNA;
		else if (DNAwithAmbiguityCodes.containsAllLetters(alphabet))
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
			if (Character.isLetter(ch)) {
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
