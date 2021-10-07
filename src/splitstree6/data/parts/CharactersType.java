package splitstree6.data.parts;

import jloda.util.Basic;

import java.util.HashSet;
import java.util.Set;

/**
 * characters type
 * Daniel Huson, 1/16/17.
 */
public enum CharactersType {
	Standard("01"),
	DNA("acgt"), // todo: have changed this to acgt from atgc 25Feb2019, does this break anything?
	RNA("acgu"),
	Protein("arndcqeghilkmfpstwyvz"),
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
		return this == DNA || this == RNA;
	}

	public static CharactersType guessType(String sequence) {
		var set = new HashSet<Character>();
		for (int i = 0; i < sequence.length(); i++) {
			set.add(sequence.charAt(i));
		}
		return guessType(set);
	}

	public static CharactersType guessType(Set<Character> set) {
		var alphabet = Basic.toString(set, "").toLowerCase();

		var result = Unknown;
		var best = 0.0;

		for (var type : values()) {
			var size = (float) intersection(type.symbols, alphabet).size() / (float) union(type.symbols, alphabet).size();
			if (size > best) {
				result = type;
				best = size;
			}

		}
		return result;
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
