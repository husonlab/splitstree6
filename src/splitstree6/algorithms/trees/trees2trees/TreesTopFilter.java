package splitstree6.algorithms.trees.trees2trees;

import jloda.phylo.PhyloTree;
import jloda.util.Basic;
import jloda.util.BitSetUtils;
import jloda.util.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.sflow.TopFilter;
import splitstree6.utils.TreesUtilities;

import java.io.IOException;

public class TreesTopFilter extends TopFilter<TreesBlock, TreesBlock> {
	public TreesTopFilter(Class<TreesBlock> fromClass, Class<TreesBlock> toClass) {
		super(fromClass, toClass);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, TreesBlock inputData, TreesBlock outputData) throws IOException {
		if (originalTaxaBlock.getTaxa().equals(modifiedTaxaBlock.getTaxa())) {
			outputData.copy(inputData);
			setShortDescription("using all " + modifiedTaxaBlock.size() + " taxa");
		} else {
			final int[] oldTaxonId2NewTaxonId = new int[originalTaxaBlock.getNtax() + 1];
			for (int t = 1; t <= originalTaxaBlock.getNtax(); t++) {
				oldTaxonId2NewTaxonId[t] = modifiedTaxaBlock.indexOf(originalTaxaBlock.get(t).getName());
			}

			progress.setMaximum(inputData.getNTrees());

			for (PhyloTree tree : inputData.getTrees()) {
				// PhyloTree inducedTree = computeInducedTree(tree, modifiedTaxaBlock.getLabels());
				final PhyloTree inducedTree = TreesUtilities.computeInducedTree(oldTaxonId2NewTaxonId, tree);
				if (inducedTree != null) {
					outputData.getTrees().add(inducedTree);
					if (false && !BitSetUtils.contains(modifiedTaxaBlock.getTaxaSet(), TreesUtilities.getTaxa(inducedTree))) {
						System.err.println("taxa:" + Basic.toString(modifiedTaxaBlock.getTaxaSet()));
						System.err.println("tree:" + Basic.toString(TreesUtilities.getTaxa(inducedTree)));
						throw new RuntimeException("Induce tree failed");
					}
				}
				progress.incrementProgress();
			}

			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " taxa");
		}
		outputData.setPartial(inputData.isPartial());
		outputData.setRooted(inputData.isRooted());
	}
}
