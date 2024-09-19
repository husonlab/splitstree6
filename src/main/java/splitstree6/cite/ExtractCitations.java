/*
 *  ExtractCitations.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.cite;

import jloda.util.Pair;
import jloda.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ExtractCitations {
	/**
	 * get all the key - paper pairs for an object with citations
	 *
	 * @return pairs
	 */
	public static Collection<Pair<String, String>> apply(IHasCitations citationsCarrier) {
		if (citationsCarrier.getCitation() == null || citationsCarrier.getCitation().length() < 2)
			return null;
		else {
			Set<Pair<String, String>> set = new TreeSet<>();
			var tokens = StringUtils.split(citationsCarrier.getCitation(), ';');
			if (tokens.length % 2 == 1)
				System.err.println("Internal error: Citation string has odd number of tokens: " + citationsCarrier.getCitation());
			for (int i = 0; i < tokens.length - 1; i += 2) {
				set.add(new Pair<>(tokens[i], tokens[i + 1]));
			}
			return set;
		}
	}

	public static String getSplitsTreeKeysString() {
		return " (Huson and Bryant 2006)";
	}

	public static Collection<Pair<String, String>> getSplitsTreeKeysAndPapers() {
		return List.of(new Pair<>("Huson and Bryant 2024",
				"D.H. Huson and D. Bryant." +
				" The SplitsTree App: interactive analysis and visualization using phylogenetic trees and networks." +
				" Nature Method, 2024, https://doi.org/10.1038/s41592-024-02406-3."));
	}
}
