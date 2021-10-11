/*
 * RootByOutGroupAlgorithm.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.phylo.PhyloTree;
import jloda.util.Basic;
import jloda.util.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.utils.RerootingUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * tree rerooting by outgroup
 * Daniel Huson, 5.2018
 */
public class RootByOutGroupAlgorithm extends Trees2Trees implements IFilter {

	private final ObservableList<Taxon> optionInGroupTaxa = FXCollections.observableArrayList();
	private final ObservableList<Taxon> optionOutGroupTaxa = FXCollections.observableArrayList();

	@Override
	public List<String> listOptions() {
		return Arrays.asList("InGroupTaxa", "OutGroupTaxa");
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "InGroupTaxa" -> "List of taxa belonging to the in-group";
			case "GroupTaxa" -> "List of taxa belonging to the out-group";
			default -> optionName;
		};
	}


	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, TreesBlock parent, TreesBlock child) throws IOException {
		if (optionInGroupTaxa.size() == 0 || optionOutGroupTaxa.size() == 0) // nothing has been explicitly set, copy everything
		{
			child.getTrees().setAll(parent.getTrees());
		} else { // reroot using outgroup
			// System.err.println("Outgroup taxa: "+Basic.toString(outGroupTaxa," "));

			final BitSet outGroupTaxonSet = new BitSet();
			for (Taxon taxon : optionOutGroupTaxa) {
				int index = taxa.indexOf(taxon);
				if (index >= 0)
					outGroupTaxonSet.set(index);
			}

			child.getTrees().clear();

			for (PhyloTree orig : parent.getTrees()) {
				if (orig.getNumberOfNodes() > 0) {
					final PhyloTree tree = new PhyloTree();
					tree.copy(orig);
					if (tree.getRoot() == null) {
						tree.setRoot(tree.getFirstNode());
						tree.redirectEdgesAwayFromRoot();
					}
					if (outGroupTaxonSet.cardinality() > 0)
						// todo: ask about internal node labels
						RerootingUtils.rerootByOutGroup(false, tree, outGroupTaxonSet);
					child.getTrees().add(tree);
				}
			}
			child.setRooted(true);
		}

		if (optionInGroupTaxa.size() == 0 || optionOutGroupTaxa.size() == 0)
			setShortDescription(Basic.fromCamelCase(Basic.getShortName(this.getClass())));
		else
			setShortDescription("using " + optionOutGroupTaxa.size() + " of " + (taxa.getNtax() + " for tree rooting"));

	}

	@Override
	public void clear() {
		optionInGroupTaxa.clear();
		optionOutGroupTaxa.clear();
	}

	public ObservableList<Taxon> getOptionInGroupTaxa() {
		return optionInGroupTaxa;
	}

	public ObservableList<Taxon> getOptionOutGroupTaxa() {
		return optionOutGroupTaxa;
	}

	@Override
	public boolean isActive() {
		return optionOutGroupTaxa.size() > 0;
	}

}
