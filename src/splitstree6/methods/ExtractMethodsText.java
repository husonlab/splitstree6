/*
 * ExtractMethodsText.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.methods;

import jloda.util.Basic;
import jloda.util.Pair;
import splitstree6.algorithms.IFilter;
import splitstree6.main.Version;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

/**
 * generates the methods text for a given Workflow
 * Daniel Huson, June 2017
 */
public class ExtractMethodsText {
	private static ExtractMethodsText instance;

	public static String preambleTemplate = "Analysis was performed using SplitsTree6 %s%s.\n";
	public static String inputDataTemplate = "The original input consisted of %s and %s.\n";
	public static String taxonFilterTemplateOne = "After removal of one taxon, the input consisted of %s and %s.\n";
	public static String taxonFilterTemplate = "After removal of %d taxa, the input consisted of %s and %s.\n";
	public static String methodWithOutputTemplate = "The %s method%s was used%s so as to obtain %s.\n";
	public static String methodTemplate = "The %s method%s was used%s.\n";

	public static String filterTemplate = "A %s%s was applied so as to be %s.\n";

	/**
	 * constructor
	 */
	private ExtractMethodsText() {
	}

	/**
	 * gets the single instance
	 *
	 * @return instance
	 */
	public static ExtractMethodsText getInstance() {
		if (instance == null)
			instance = new ExtractMethodsText();
		return instance;
	}

	/**
	 * generate the current methods text
	 *
	 * @return method text
	 */
	public String apply(Workflow workflow) {
		if (workflow.getInputTaxaNode() == null || workflow.getInputDataNode() == null)
			return "";

		var buf = new StringBuilder();
		buf.append("Methods:\n");

		buf.append(String.format(preambleTemplate, Version.VERSION, ExtractCitations.getSplitsTreeKeysString()));

		final Set<Pair<String, String>> allKeysAndPapers = new TreeSet<>(ExtractCitations.getSplitsTreeKeysAndPapers());

		final Set<String> set = new HashSet<>(); // use this to avoid duplicate lines

		buf.append(String.format(inputDataTemplate, workflow.getInputTaxaNode().getDataBlock().getInfo(), workflow.getInputDataNode().getDataBlock().getInfo()));

		var topTaxaBlock = workflow.getInputTaxonBlock();
		var workingTaxaBlock = workflow.getWorkingTaxaBlock();
		if (workingTaxaBlock != null && workingTaxaBlock.getNtax() < topTaxaBlock.getNtax()) {
			int removed = (topTaxaBlock.getNtax() - workingTaxaBlock.getNtax());
			if (removed == 1)
				buf.append(String.format(taxonFilterTemplateOne, workflow.getWorkingTaxaBlock().getInfo(), workflow.getWorkingDataNode().getDataBlock().getInfo()));
			else
				buf.append(String.format(taxonFilterTemplate, removed, workflow.getWorkingTaxaBlock().getInfo(), workflow.getWorkingDataNode().getDataBlock().getInfo()));
		}
		final var root = workflow.getWorkingDataNode();
		if (root.isValid()) {
			final var visited = new HashSet<DataNode>();
			final var stack = new Stack<DataNode>();
			stack.push(root); // should only contain data nodes
			while (stack.size() > 0) {
				final var v = stack.pop();
				if (!visited.contains(v)) {
					visited.add(v);
					for (var child : v.getChildren()) {
						if (child.isValid()) {
							if (child instanceof AlgorithmNode algorithmNode) {
								final var algorithm = algorithmNode.getAlgorithm();
								final var targetNode = algorithmNode.getTargetNode();
								if (!(algorithm instanceof IgnoredInMethodsText)) {
									if (algorithm instanceof IFilter filter) {
										if (filter.isActive()) {
											var name = Basic.fromCamelCase(algorithm.getName());
											var optionsReport = ExtractOptionsText.apply(algorithm);
											var line = String.format(filterTemplate, name, optionsReport.length() > 0 ? " (" + optionsReport + ")" : "", algorithm.getShortDescription());
											if (!set.contains(line)) {
												buf.append(line);
												set.add(line);
											}
										}
									} else {
										if (algorithm != null) {
											var keys = getKeysString(algorithm);
											var keysAndPapers = ExtractCitations.apply(algorithm);
											if (keysAndPapers != null)
												allKeysAndPapers.addAll(keysAndPapers);
											var name = Basic.fromCamelCase(algorithm.getName());

											var optionsReport = ExtractOptionsText.apply(algorithm);
											String line;
											if (targetNode != null) {
												line = String.format(methodWithOutputTemplate, name, keys, optionsReport.length() > 0 ? " (" + optionsReport + ")" : "", targetNode.getDataBlock().getInfo());
											} else {
												line = String.format(methodTemplate, name, keys, optionsReport.length() > 0 ? " (" + optionsReport + ")" : "");
											}
											if (!set.contains(line)) {
												buf.append(line);
												set.add(line);
											}
										}
									}
								}
								stack.push(targetNode);
							}
						} else
							buf.append("*** Calculation incomplete. ***\n");
					}
				}
			}
			buf.append("\n");
			if (allKeysAndPapers.size() > 0) {
				buf.append("References:\n");

				for (Pair<String, String> pair : allKeysAndPapers) {
					buf.append(String.format("%s: %s\n", pair.getFirst(), pair.getSecond()));
				}
			}
		} else
			buf.append("*** Calculation incomplete. ***\n");

		return buf.toString();
	}

	/**
	 * gets the citation keys for an algorithm
	 *
	 * @param algorithm
	 * @return citation
	 */
	public static String getKeysString(Algorithm algorithm) {
		if (algorithm.getCitation() == null || algorithm.getCitation().length() < 2)
			return "";
		else {
			var tokens = Basic.split(algorithm.getCitation(), ';');
			var buf = new StringBuilder();
			buf.append(" (");
			for (int i = 0; i < tokens.length; i += 2) {
				if (i > 0)
					buf.append(", ");
				else
					buf.append(tokens[i]);
			}
			buf.append(")");
			return buf.toString();
		}
	}
}
