/*
 *  FilteredSuperNetwork.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.Distortion;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.ASplit;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * filtered super network
 * Daniel Huson, 2006, 3.2018
 */
public class FilteredSuperNetwork extends SuperNetwork {
	private final IntegerProperty optionMinNumberTrees = new SimpleIntegerProperty(this, "optionMinNumberTrees", 1);
	private final IntegerProperty optionMaxDistortionScore = new SimpleIntegerProperty(this, "optionMaxDistortionScore", 0);
	private final BooleanProperty optionUseTotalScore = new SimpleBooleanProperty(this, "optionUseTotalScore", false);


	@Override
	public String getCitation() {
		return "Whitfield et al 2008;JB Whitfield, SA Cameron, DH Huson and MA Steel. " +
			   "Filtered Z-Closure Supernetworks for Extracting and Visualizing Recurrent Signal from Incongruent Gene Trees, " +
			   "Systematic Biology, 57(6):939-947, 2008.";
	}

	public List<String> listOptions() {
		return Arrays.asList(optionMinNumberTrees.getName(), optionMaxDistortionScore.getName(), optionUseTotalScore.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return "Set the " + StringUtils.fromCamelCase(optionName).toLowerCase();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		// first compute splits using Z-closure method:
		SplitsBlock zClosureSplits = new SplitsBlock();
		super.compute(progress, taxaBlock, treesBlock, zClosureSplits);

		progress.setSubtask("Processing trees");
		progress.setMaximum(zClosureSplits.getNsplits());

		final BitSet[] tree2taxa = new BitSet[treesBlock.getNTrees() + 1];
		for (int t = 1; t <= treesBlock.getNTrees(); t++) {
			tree2taxa[t] = TreesUtils.getTaxa(treesBlock.getTree(t));
			//System.err.println("number of taxa in tree " + t + ":" + tree2taxa[t].size());
			progress.setProgress(t);
		}

		progress.setSubtask("Processing splits");
		progress.setMaximum((long) zClosureSplits.getNsplits() * treesBlock.getNTrees());
		progress.setProgress(0);

		System.err.println("Filtering splits:");
		if (isOptionUseTotalScore()) {
			for (int s = 1; s <= zClosureSplits.getNsplits(); s++) {
				int totalScore = 0;
				BitSet A = zClosureSplits.get(s).getA();
				BitSet B = zClosureSplits.get(s).getB();
				for (int t = 1; t <= treesBlock.getNTrees(); t++) {
					final BitSet treeTaxa = tree2taxa[t];
					final BitSet treeTaxaAndA = (BitSet) (treeTaxa.clone());
					treeTaxaAndA.and(A);
					final BitSet treeTaxaAndB = (BitSet) (treeTaxa.clone());
					treeTaxaAndB.and(B);

					if (treeTaxaAndA.cardinality() > 1 && treeTaxaAndB.cardinality() > 1) {
						final PhyloTree tree = treesBlock.getTree(t);
						totalScore += Distortion.computeDistortionForSplit(tree, A, B);
					}
					progress.incrementProgress();
				}
				if (totalScore <= getOptionMaxDistortionScore()) {
					final ASplit aSplit = zClosureSplits.get(s);
					splitsBlock.getSplits().add(new ASplit(aSplit.getA(), aSplit.getB(), aSplit.getWeight()));
				}
			}
		} else // do not use total score
		{
			for (int s = 1; s <= zClosureSplits.getNsplits(); s++) {
				//System.err.print("s " + s + ":");
				final BitSet A = zClosureSplits.get(s).getA();
				final BitSet B = zClosureSplits.get(s).getB();
				int count = 0;
				for (int t = 1; t <= treesBlock.getNTrees(); t++) {
					BitSet treeTaxa = tree2taxa[t];
					BitSet treeTaxaAndA = (BitSet) (treeTaxa.clone());
					treeTaxaAndA.and(A);
					BitSet treeTaxaAndB = (BitSet) (treeTaxa.clone());
					treeTaxaAndB.and(B);

					if (treeTaxaAndA.cardinality() > 1 && treeTaxaAndB.cardinality() > 1) {
						final PhyloTree tree = treesBlock.getTree(t);
						int score = Distortion.computeDistortionForSplit(tree, A, B);
						//System.err.print(" " + score);
						if (score <= getOptionMaxDistortionScore())
							count++;
						if (count + (treesBlock.getNTrees() - t) < getOptionMinNumberTrees())
							break; // no hope to get above threshold
					} else if ((A.cardinality() == 1 || B.cardinality() == 1)
							   && treeTaxaAndB.cardinality() > 0 && treeTaxaAndB.cardinality() > 0) {
						count++; // is confirmed split
						//System.err.print(" +");
					} else {
						//System.err.print(" .");
					}
					progress.incrementProgress();
				}
				//System.err.println(" sum=" + count);
				if (A.cardinality() == 1 || B.cardinality() == 1 || count >= getOptionMinNumberTrees()) {
					final ASplit aSplit = zClosureSplits.get(s);
					splitsBlock.getSplits().add(new ASplit(aSplit.getA(), aSplit.getB(), aSplit.getWeight(), (float) count / (float) treesBlock.getNTrees()));
				}
			}
		}
		splitsBlock.getSplits().addAll(SplitsBlockUtilities.createAllMissingTrivial(splitsBlock.getSplits(), taxaBlock.getNtax(), 0.0));

		System.err.println("Splits: " + zClosureSplits.getNsplits() + " -> " + splitsBlock.getNsplits());
	}

	public int getOptionMinNumberTrees() {
		return optionMinNumberTrees.get();
	}

	public IntegerProperty optionMinNumberTreesProperty() {
		return optionMinNumberTrees;
	}

	public void setOptionMinNumberTrees(int optionMinNumberTrees) {
		this.optionMinNumberTrees.set(optionMinNumberTrees);
	}

	public int getOptionMaxDistortionScore() {
		return optionMaxDistortionScore.get();
	}

	public IntegerProperty optionMaxDistortionScoreProperty() {
		return optionMaxDistortionScore;
	}

	public void setOptionMaxDistortionScore(int optionMaxDistortionScore) {
		this.optionMaxDistortionScore.set(optionMaxDistortionScore);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isReticulated();
	}

	public boolean isOptionUseTotalScore() {
		return optionUseTotalScore.get();
	}

	public BooleanProperty optionUseTotalScoreProperty() {
		return optionUseTotalScore;
	}

	public void setOptionUseTotalScore(boolean optionUseTotalScore) {
		this.optionUseTotalScore.set(optionUseTotalScore);
	}
}
