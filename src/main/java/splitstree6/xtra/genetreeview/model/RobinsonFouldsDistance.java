/*
 *  RobinsonFouldsDistance.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.model;

import jloda.phylo.PhyloTree;
import jloda.util.Pair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

public class RobinsonFouldsDistance {

	// Calculation of a modified Robinson-Foulds distance that supports partial and multifurcating phylogenetic trees
	public static int calculate(PhyloTree tree1, PhyloTree tree2) {
		ArrayList<HashMap<String, Integer>> idMaps = createNewTaxonName2IdMaps(tree1, tree2);
		ArrayList<Pair<BitSet, BitSet>> splits1 = getSplitsFromEdges(tree1, idMaps.get(0));
		ArrayList<Pair<BitSet, BitSet>> splits2 = getSplitsFromEdges(tree2, idMaps.get(1));
		int totalSplitNumber = tree1.getNumberOfEdges() + tree2.getNumberOfEdges();
		int numberOfSplitsInCommon = 0;
		for (var split1 : splits1) {
			var part1A = split1.getFirst();
			var part1B = split1.getSecond();
			int toRemove = -1;
			for (var split2 : splits2) {
				var part2A = split2.getFirst();
				var part2B = split2.getSecond();
				if (part1A.equals(part2A)) {
					numberOfSplitsInCommon += 1;
					toRemove = splits2.indexOf(split2);
					break;
				} else if (part1B.equals(part2A)) {
					numberOfSplitsInCommon += 1;
					toRemove = splits2.indexOf(split2);
					break;
				} else if (part1A.equals(part2B)) {
					numberOfSplitsInCommon += 1;
					toRemove = splits2.indexOf(split2);
					break;
				} else if (part1B.equals(part2B)) {
					numberOfSplitsInCommon += 1;
					toRemove = splits2.indexOf(split2);
					break;
				}
			}
			if (toRemove != -1) splits2.remove(toRemove);
		}
		return totalSplitNumber - (2 * numberOfSplitsInCommon);
	}

	private static ArrayList<HashMap<String, Integer>> createNewTaxonName2IdMaps(PhyloTree tree1, PhyloTree tree2) {
		HashMap<String, Integer> taxonName2IdTree1 = new HashMap<>();
		HashMap<String, Integer> taxonName2IdTree2 = new HashMap<>();
		int newTaxonId = 0;
		for (var taxonId : tree1.getTaxonNodeMap().keySet()) {
			String taxonName = tree1.getTaxon2Node(taxonId).getLabel();
			taxonName2IdTree1.put(taxonName, newTaxonId);
			for (var taxonNode : tree2.getTaxonNodeMap().values()) {
				if (taxonNode.getLabel().equals(taxonName)) {
					taxonName2IdTree2.put(taxonName, newTaxonId);
					break;
				}
			}
			newTaxonId++;
		}
		for (var taxonId : tree2.getTaxonNodeMap().keySet()) {
			String taxonName = tree2.getTaxon2Node(taxonId).getLabel();
			if (taxonName2IdTree2.containsKey(taxonName)) continue;
			taxonName2IdTree2.put(taxonName, newTaxonId);
			newTaxonId++;
		}
		ArrayList<HashMap<String, Integer>> idMaps = new ArrayList<>();
		idMaps.add(0, taxonName2IdTree1);
		idMaps.add(1, taxonName2IdTree2);
		return idMaps;
	}

	private static ArrayList<Pair<BitSet, BitSet>> getSplitsFromEdges(PhyloTree tree, HashMap<String, Integer> name2id) {
		ArrayList<Pair<BitSet, BitSet>> splits = new ArrayList<>();
		for (var edge : tree.edges()) {
			BitSet partA = new BitSet();
			tree.postorderTraversal(edge.getTarget(), n -> n.outEdges().forEach(e -> {
				if (e.getTarget().isLeaf()) {
					var taxonNode = e.getTarget();
					partA.set(name2id.get(taxonNode.getLabel()));
				}
			}));
			BitSet partB = new BitSet();
			for (var taxonId : name2id.values()) {
				if (!partA.get(taxonId)) partB.set(taxonId);
			}
			if (partA.cardinality() <= partB.cardinality()) splits.add(new Pair<>(partA, partB));
			else splits.add(new Pair<>(partB, partA));
		}
		return splits;
	}
}
