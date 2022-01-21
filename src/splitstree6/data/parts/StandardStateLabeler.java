/*
 * StandardStateLabeler.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;

/**
 * state labeler for standard data
 * Original author: David Bryant
 * Daria Evseeva,30.10.2016.
 */
public class StandardStateLabeler extends StateLabeler {

	private final HashMap<String, Character>[] token2charMaps; //Map from strings to char, one for each site
	private final HashMap<Character, String>[] char2tokenMaps;  //Reverse of the above map

	int maxState; //The states used will be characters 0....maxStates (incl) in availableChars
	String availableChars; //List of ascii characters for use in standard mode

	/**
	 * constructor
	 *
	 * @param nchar
	 * @param missingChar
	 * @param matchChar
	 * @param gapChar
	 */
	public StandardStateLabeler(int nchar, char missingChar, char matchChar, char gapChar) {
		maxState = -1;

		availableChars = "1234567890";  //These are the standard ones for paup, mesquite etc.
		availableChars += "abcdefghijklmnopqrstuvwxyz";    //augment them with lower case letters
		for (char ch = 192; ch <= 255; ch++)           //and misc. ascii characters.
			availableChars += "" + ch;

		//Now remove characters that are forbidden
		String forbidden = ";\\[\\],\\(\\)/"; //punctuation characters  // todo: there is a problem with this expression
		forbidden += regString(missingChar);
		forbidden += regString(matchChar);
		forbidden += regString(gapChar);
		availableChars = availableChars.replaceAll("[" + forbidden + "]", "");

		//Initialise the maps at each site.
		token2charMaps = new HashMap[nchar + 1];
		char2tokenMaps = new HashMap[nchar + 1];
		for (int i = 1; i <= nchar; i++) {
			token2charMaps[i] = new HashMap<>();
			char2tokenMaps[i] = new HashMap<>();
		}
	}

	//TODO: The following don't computeCycle to microsat or protein+token

	/**
	 * Check if a site has states stored for it
	 *
	 * @param site NUmber of site (character)
	 * @return true if states/tokens have been stored for that site.
	 */
	public boolean hasStates(int site) {
		return !(token2charMaps.length <= site || token2charMaps[site] == null) && (!token2charMaps[site].isEmpty());
	}

	public String[] getStates(int site) {
		int size = char2tokenMaps[site].size();
		String[] stateArray = new String[size];
		int i = 0;
		char ch = availableChars.charAt(i);
		while (char2tokenMaps[site].containsKey(ch)) {
			stateArray[i] = char2tokenMaps[site].get(ch);
			i++;
			ch = availableChars.charAt(i);
		}
		return stateArray;
	}

	/**
	 * Encode ch for use in a reg exp.
	 *
	 * @param ch character
	 * @return String the character, possibly with a backslash before.
	 */
	private String regString(char ch) {
		if (ch == '^' || ch == '-' || ch == ']' || ch == '\\')
			return "\\" + ch;
		else
			return "" + ch;
	}

	@Override
	public char token2char(int site, String token) throws IOException {
		if (token2charMaps[site].containsKey(token)) {
			return token2charMaps[site].get(token);
		} else {
			int id = token2charMaps[site].size() + 1;
			if (id >= availableChars.length())
				throw new IOException("Too many alleles per site: please contact authors");
			Character ch = availableChars.charAt(id - 1);
			maxState = Math.max(maxState, id - 1);
			token2charMaps[site].put(token, ch);
			char2tokenMaps[site].put(ch, token);
			return ch;
		}
	}

	@Override
	public String char2token(int site, char ch) {
		return char2tokenMaps[site].get(ch);
	}

	@Override
	public int getMaximumLabelLength() {
		int max = 0;
		for (int i = 1; i < token2charMaps.length; i++)
			for (String token : token2charMaps[i].keySet())
				max = Math.max(max, StringUtils.quoteIfNecessary(token).length());
		return max;
	}

	@Override
	public String getSymbolsUsed() {
		if (maxState < 0)
			return null;
		else
			return availableChars.substring(0, maxState + 1);
	}
}
