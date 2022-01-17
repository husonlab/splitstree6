/*
 *  NormalizeNetworks.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2view;

import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.trees2trees.Trees2Trees;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.BitSet;

/**
 * normalizes rooted networks
 */
public class NormalizeNetworks extends Trees2Trees {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) throws IOException {
		progress.setMaximum(inputData.getNTrees());
		progress.setProgress(0);
		outputData.clear();
		for (var inputTree : inputData.getTrees()) {
			var outputTree = new PhyloTree();
			outputTree.setName(inputTree.getName());
			Normalize.apply(inputTree, null, outputTree, null);
			for (var v : outputTree.nodes()) {
				if (outputTree.getNumberOfTaxa(v) > 0)
					System.err.println("Node has taxa: " + IteratorUtils.asList(outputTree.getTaxa(v)));
				var seen = new BitSet();
				var label = outputTree.getLabel(v);
				if (label != null) {
					var taxonId = taxaBlock.indexOf(label);
					if (taxonId == -1)
						System.err.println("Unknown label: " + label);
					else {
						if (seen.get(taxonId))
							System.err.println("Multiple occurrences of: " + label + " and/or " + taxonId);
						else
							seen.set(taxonId);
						outputTree.addTaxon(v, taxonId);
						// System.err.println(label+" -> "+taxonId);
					}
				} else if (v.isLeaf())
					System.err.println("Leaf without label");
			}

			for (var v : outputTree.nodes()) {
				if (v.getInDegree() > 1) {
					for (var e : v.inEdges()) {
						outputTree.setWeight(e, 0.0);
						outputTree.setReticulated(e, true);
						outputData.setReticulated(true);
					}
				}
			}

			//System.err.println(outputTree.toBracketString(false)+";");

			outputData.getTrees().add(outputTree);
			progress.incrementProgress();
		}
	}

	@Override
	public String getCitation() {
		return "Francis et al, 2021; A Francis, DH Huson and MA MikeSteel. Normalising phylogenetic networks. Molecular Phylogenetics and Evolution, 163 (2021)";
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.isReticulated();
	}
}
