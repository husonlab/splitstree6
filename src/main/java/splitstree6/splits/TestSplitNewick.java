/*
 *  TestSplitNewick.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.splits;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.io.GraphGML;
import jloda.phylo.PhyloSplitsGraph;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.function.BiFunction;

/**
 * test split netwick i/o
 * Daniel Huson, 8.2023
 */
public class TestSplitNewick {
	public static void main(String[] args) throws IOException {
		var input = "(A.andrenof:0,(A.florea:0.00368726,((<2|A.cerana:0.04676762,<1|A.mellifer:0.03322177):0.00728603,(A.dorsata|1:0.01010407>|2:0.00637327>:0.04925101,A.koschev:0.03768609):0.00329972):0.03362335):0.00073305);";
		System.err.println("Input:\n" + input);

		var splitNetwork = new PhyloSplitsGraph();
		SplitNewick.read(new StringReader(input), null, null, splitNetwork);

		if (false) {
			var w = new StringWriter();

			BiFunction<String, Node, String> labelNodeValueFunction = (label, v) -> {
				if (label.equals("taxon") && splitNetwork.getNumberOfTaxa(v) > 0)
					return String.valueOf(splitNetwork.getTaxon(v));
				else return null;
			};
			BiFunction<String, Edge, String> labelEdgeValueFunction = (label, e) -> {
				if (label.equals("split"))
					return String.valueOf(splitNetwork.getSplit(e));
				else return null;
			};
			GraphGML.writeGML(splitNetwork, "", "Splits", false, 1, w, List.of("taxon"), labelNodeValueFunction, List.of("split"), labelEdgeValueFunction);
			System.err.println(w);
		}

		var splits = SplitNewick.extractSplits(splitNetwork);

		if (false) {
			System.err.println("Splits (" + splits.size() + ")");
			for (var split : splits) {
				System.err.println(split);
			}
		}
		var output = SplitNewick.toString(splitNetwork, true, false);

		System.err.println("Output:\n" + output + ";");

	}
}
