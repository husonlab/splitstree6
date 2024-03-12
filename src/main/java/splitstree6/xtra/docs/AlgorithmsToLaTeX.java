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

package splitstree6.xtra.docs;

import jloda.util.StringUtils;
import org.apache.commons.collections4.list.TreeList;
import splitstree6.algorithms.AlgorithmList;
import splitstree6.options.OptionIO;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.interfaces.DoNotLoadThisAlgorithm;

import java.util.HashMap;
import java.util.List;

/**
 * outputs a LaTeX document containing a description of all algorithms
 * Daniel Huson, 3.2024
 */
public class AlgorithmsToLaTeX {
	public static void main(String[] args) {
		var header = """
				\\documentclass{article}
				\\usepackage{graphicx}
				\\usepackage{hyperref}
				\\usepackage{fullpage}
				\\usepackage{parskip}\s
				     
				\\title{SplitsTree Community Edition - Algorithms}
				\\author{Daniel H. Huson and Dave Bryant}
				\\date{March 2024}
				    
				\\begin{document}
				    
				\\maketitle
								
				""";
		var footer = """
				\\end{document}
								
					""";
		var ordering = List.of("Characters Block", "Distances Block", "Splits Block", "Trees Block", "Network Block", "View Block", "Report Block");

		var fromLineMap = new HashMap<String, List<String>>();
		var toLineMap = new HashMap<String, List<String>>();

		for (var algorithm : AlgorithmList.list()) {
			if (!(algorithm instanceof DataTaxaFilter || algorithm instanceof DoNotLoadThisAlgorithm)) {
				var method = StringUtils.fromCamelCase(algorithm.getClass().getSimpleName());
				var fromName = StringUtils.fromCamelCase(algorithm.getFromClass().getSimpleName());
				var toName = StringUtils.fromCamelCase(algorithm.getToClass().getSimpleName());
				var buf = new StringBuilder();
				buf.append("\\subsubsection{%s}%n%n".formatted(StringUtils.fromCamelCase(algorithm.getClass().getSimpleName())));
				buf.append("The ``%s'' algorithm takes a %s as input and produces a %s as output.".formatted(
						method, fromName.replaceAll("Block", "block"), toName.replaceAll("Block", "block")));

				var description = algorithm.getShortDescription();
				if (description != null && description.length() > 2) {
					buf.append(" It ").append(description.substring(0, 1).toLowerCase()).append(description.substring(1));
				}

				buf.append("\n");

				var options = OptionIO.optionsUsage(algorithm);
				if (!options.isBlank()) {
					buf.append("\n{\\footnotesize\\obeylines\n");
					for (var line : StringUtils.toList(options)) {
						var pos = line.indexOf("-");
						if (pos > 0) {
							buf.append("\\verb^%s^ - %s%n".formatted(line.substring(0, pos).trim(), line.substring(pos + 1).trim()));
						} else buf.append(line).append("\n");
					}
					buf.append("}\n");
				} else if (!options.isBlank())
					buf.append("%nOptions:%n{\\footnotesize%n\\begin{verbatim}%n%s\\end{verbatim}%n}%n".formatted(options));
				var citations = algorithm.getCitation();
				if (citations != null && !citations.isBlank()) {
					var pos = citations.indexOf(";");
					if (pos > 0)
						citations = citations.substring(pos + 1);
					buf.append("%nSee: %s%n".formatted(citations));
				}

				var line = buf.toString().replaceAll("&", "\\\\&")
						.replaceAll("(?i)\\bDna\\b", "DNA");

				fromLineMap.computeIfAbsent(fromName, k -> new TreeList<>()).add(line);
				toLineMap.computeIfAbsent(toName, k -> new TreeList<>()).add(line);
			}
		}

		System.out.println(header);

		if (true) {
			System.out.println("\\section{List of algorithms sorted by input}\n\n");
			for (var key : ordering) {
				if (fromLineMap.containsKey(key)) {
					System.out.printf("\\subsection{Input %s}%n%n", key);
					for (var line : fromLineMap.get(key)) {
						System.out.println(line);
					}
				}
			}
		}

		if (false) {
			System.out.println("\\section{List of algorithms sorted by output}\n\n");
			for (var key : ordering) {
				if (toLineMap.containsKey(key)) {
					System.out.printf("\\subsection{Output %s}%n%n", key);
					for (var line : toLineMap.get(key)) {
						System.out.println(line);
					}
				}
			}
		}
		System.out.println(footer);

	}
}
