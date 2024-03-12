/*
 *  BlocksToLaTeX.java Copyright (C) 2024 Daniel H. Huson
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

import splitstree6.io.nexus.*;

import static splitstree6.xtra.latex.IOToLaTeX.FOOTER;
import static splitstree6.xtra.latex.IOToLaTeX.HEADER;

public class BlocksToLaTeX {
	public static void main(String[] args) {

		System.out.println(HEADER +
						   "\\section{Main data blocks and their `Nexus format' syntax}\n\n" +
						   output("Taxa block", TaxaNexusInput.DESCRIPTION, TaxaNexusInput.SYNTAX) +
						   output("Traits block", TraitsNexusInput.DESCRIPTION, TraitsNexusInput.SYNTAX) +
						   output("Characters block", CharactersNexusInput.DESCRIPTION, CharactersNexusInput.SYNTAX) +
						   output("Distances block", DistancesNexusInput.DESCRIPTION, DistancesNexusInput.SYNTAX) +
						   output("Trees block", TreesNexusInput.DESCRIPTION, TreesNexusInput.SYNTAX) +
						   output("Splits block", SplitsNexusInput.DESCRIPTION, SplitsNexusInput.SYNTAX) +
						   output("Network block", NetworkNexusInput.DESCRIPTION, NetworkNexusInput.SYNTAX) +
						   output("View block", ViewNexusInput.DESCRIPTION, ViewNexusInput.SYNTAX) +
						   output("Algorithms block", AlgorithmNexusInput.DESCRIPTION, AlgorithmNexusInput.SYNTAX) +
						   output("Report block", ReportNexusInput.DESCRIPTION, ReportNexusInput.SYNTAX) +
						   output("Sets block", SetsNexusInput.DESCRIPTION, SetsNexusInput.SYNTAX) +
						   output("SplitsTree6 block", SplitsTree6NexusInput.DESCRIPTION, SplitsTree6NexusInput.SYNTAX) +
						   output("Genomes block", GenomesNexusInput.DESCRIPTION, GenomesNexusInput.SYNTAX) +
						   FOOTER);
	}

	public static String output(String name, String description, String syntax) {
		return "\\subsection{%s}\n%n%s%n".formatted(name, description.replaceAll("\t", "    ")) +
			   "{\\footnotesize\\begin{verbatim}\n" +
			   syntax.replaceAll("\t", "  ") +
			   "\\end{verbatim}}\n\n";
	}
}
