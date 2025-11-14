/*
 * GML.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package razornet.utils;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * write out the network in GML
 * Daniel Huson, 10.2025
 */
public class GML {
	/* write out the network in GML */
	public static void write(Writer w, IntGraph graph, Map<Integer, Integer> nodeTaxonMap, ToDoubleFunction<Integer> mapBack) throws IOException {
		w.write("graph [\n");
		w.write("\tcomment \"Exported from RazorNet: %d nodes, %d edges\"\n".formatted(graph.getNumberOfNodes(), graph.countNumberOfEdges()));
		w.write("\tdirected 0\n");
		w.write("\tid 1\n");
		w.write("\tlabel \"Network\"\n");
		for (var v : graph.nodes()) {
			if (nodeTaxonMap.containsKey(v)) {
				w.write("""
						\tnode [
							id %d
							label "t%d"
						]
						""".formatted(v, nodeTaxonMap.get(v)));
			} else {
				w.write("""
						\tnode [
							id %d
						]
						""".formatted(v));
			}
		}
		for (var e : graph.edges()) {
			w.write("""
					\tedge [
						source %d
						target %d
						weight "%f"
					]
					""".formatted(e.u(), e.v(), mapBack.applyAsDouble(graph.getWeight(e))));
		}
		w.write("]\n");
	}
}
