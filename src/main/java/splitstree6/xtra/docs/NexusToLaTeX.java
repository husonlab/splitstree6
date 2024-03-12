/*
 *  NexusToLaTeX.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.docs;

import splitstree6.io.nexus.*;

public class NexusToLaTeX {
	public static void main(String[] args) {
		var buf = new StringBuilder();

		buf.append(output("Taxa block", TaxaNexusInput.DESCRIPTION, TaxaNexusInput.SYNTAX));

		buf.append(output("Characters block", CharactersNexusInput.DESCRIPTION, CharactersNexusInput.SYNTAX));

		buf.append(output("Distances block", DistancesNexusInput.DESCRIPTION, DistancesNexusInput.SYNTAX));

		buf.append(output("Trees block", TreesNexusInput.DESCRIPTION, TreesNexusInput.SYNTAX));

		buf.append(output("Splits block", SplitsNexusInput.DESCRIPTION, SplitsNexusInput.SYNTAX));

		buf.append(output("Network block", NetworkNexusInput.DESCRIPTION, NetworkNexusInput.SYNTAX));

		buf.append(output("View block", ViewNexusInput.DESCRIPTION, ViewNexusInput.SYNTAX));

		buf.append(output("Algorithms block", AlgorithmNexusInput.DESCRIPTION, AlgorithmNexusInput.SYNTAX));

		buf.append(output("Report block", ReportNexusInput.DESCRIPTION, ReportNexusInput.SYNTAX));

		buf.append(output("Sets block", SetsNexusInput.DESCRIPTION, SetsNexusInput.SYNTAX));

		buf.append(output("SplitsTree6 block", SplitsTree6NexusInput.DESCRIPTION, SplitsTree6NexusInput.SYNTAX));

		buf.append(output("Genomes block", GenomesNexusInput.DESCRIPTION, GenomesNexusInput.SYNTAX));

		System.out.println(buf.toString());
	}

	public static String output(String name, String description, String syntax) {
		return "\\subsection{%s}\n%n%s%n".formatted(name, description) +
			   "\\begin{verbatim}\n" +
			   syntax +
			   "\\end{verbatim}\n\n";
	}
}
