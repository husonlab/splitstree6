/*
 *  AlgorithmsToLaTeX.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.latex;

import splitstree6.algorithms.AlgorithmList;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.interfaces.DoNotLoadThisAlgorithm;

import java.util.TreeSet;

/**
 * outputs a LaTeX document containing a description of all algorithms
 * Daniel Huson, 3.2024
 */
public class ExtractReferences {
	public static void main(String[] args) {
		var set = new TreeSet<String>();

		for (var algorithm : AlgorithmList.list()) {
			if (!(algorithm instanceof DataTaxaFilter || algorithm instanceof DoNotLoadThisAlgorithm)) {


				var citations = algorithm.getCitation();
				if (citations != null && !citations.isBlank()) {
					var pos = citations.indexOf(";");
					if (pos > 0)
						citations = citations.substring(pos + 1);
					set.add(citations);
				}
			}
		}

		for (var line : set) {
			System.out.println("\n- " + line);
		}


	}
}
