/*
 *  CheckContainment.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools;

import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.FileUtils;
import splitstree6.utils.PathMultiplicityDistance;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;

public class CheckContainment {
	public static void main(String[] args) throws IOException {
		var trees = new ArrayList<PhyloTree>();
		var networks = new ArrayList<PhyloTree>();

		var files = (args.length == 0 ? new String[]{"stdin"} : args);

		for (var file : files) {
			try (var r = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(file))) {
				var parts = new ArrayList<String>();
				while (r.ready()) {
					var line = r.readLine();
					parts.add(line.trim());
					if (line.endsWith(";")) {
						var newick = String.join("", parts);
						parts.clear();
						var phylo = new PhyloTree();
						phylo.parseBracketNotation(newick, true);
						for (var e : phylo.edges()) {
							if (e.getTarget().getInDegree() > 1)
								phylo.setReticulate(e, true);
						}
						// System.err.println(phylo.toBracketString(false)+";");
						if (phylo.isReticulated()) {
							phylo.setName("Network-" + (networks.size() + 1));
							networks.add(phylo);
						} else {
							phylo.setName("Tree-" + (trees.size() + 1));
							trees.add(phylo);
						}
					}
				}
			}
		}

		var taxa = new BitSet();
		for (var tree : trees) {
			taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
		}
		for (var network : networks) {
			taxa.or(BitSetUtils.asBitSet(network.getTaxa()));
		}

		for (var network : networks) {
			for (var tree : trees) {
				System.out.printf("%s contains %s: %s%n", network.getName(), tree.getName(), PathMultiplicityDistance.contains(taxa, network, tree));
			}
		}
	}
}
