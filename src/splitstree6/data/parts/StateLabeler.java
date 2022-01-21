/*
 * StateLabeler.java Copyright (C) 2022 Daniel H. Huson
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
import java.util.List;

/**
 * Character state labeler
 * Original author: David Bryant
 * Daria Evseeva,30.10.2016.
 */
public abstract class StateLabeler {

	/**
	 * Takes a token and site. If the token has appeared at that site, returns corresponding char.
	 * Otherwise, adds token to the map and returns a newly assigned char.
	 *
	 * @param site  site in the characters block
	 * @param token name of token
	 * @return char used to encode that token
	 */
	abstract public char token2char(int site, String token) throws IOException;

	/**
	 * Returns token associated to a given char value at a particular site, or null if
	 * there is none.
	 *
	 * @param site number of the site
	 * @param ch   char
	 * @return token name, or null if ch not stored for this site.
	 */
	abstract public String char2token(int site, char ch);

	/**
	 * Takes a list of tokens and converts them into a string of associated char values.
	 * It uses the token maps stored at each site; hence the need to know which site we
	 * are start at, and if we are reading a transposed matrix or not.
	 *
	 * @param tokens     list of tokens
	 * @param firstSite  site that first token is read for.
	 * @param transposed true if the tokens all come from the same character/site
	 * @return String of encoded chars.
	 */
	public String parseSequence(List<String> tokens, int firstSite, boolean transposed) throws IOException {
		final StringBuilder buf = new StringBuilder();
		int site = firstSite;
		for (String token : tokens) {
			buf.append(token2char(site, token));
			if (!transposed)
				site++;
		}
		return buf.toString();
	}

	// TODO: functions between compute only in standard/unknown ?
	//todo: deleted removeMaskedSites function (never used)

	/**
	 * Return the length of the longest token.
	 *
	 * @return int the longest token
	 */
	abstract public int getMaximumLabelLength();

	abstract public String getSymbolsUsed();

	abstract public boolean hasStates(int pos);

	public String[] getStates(int site) {
		return new String[0];
	}
}
