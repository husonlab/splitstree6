package splitstree6.data.parts;

import splitstree6.data.CharactersBlock;

public class AmbiguityCodes {
	public static String CODES = "wrkysmbhdvn";

	/**
	 * gets all nucleotides associated with a given code
	 *
	 * @param code a character coding an ambiguous state
	 * @return all (lowercase) letters associated with the given code, or the nucleotide it self, if not a code
	 */
	public static String getNucleotides(char code) {
		switch (Character.toLowerCase(code)) {
			case 'w':
				return "at";

			case 'r':
				return "ag";

			case 'k':
				return "gt";

			case 'y':
				return "ct";

			case 's':
				return "cg";

			case 'm':
				return "ac";

			case 'b':
				return "cgt";

			case 'h':
				return "act";

			case 'd':
				return "agt";

			case 'v':
				return "acg";

			case 'n':
				return "acgt";

			default:
				return "" + Character.toLowerCase(code); // this is not a code, but a nucleotide
		}
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
