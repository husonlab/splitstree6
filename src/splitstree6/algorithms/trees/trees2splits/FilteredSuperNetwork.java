/*
 * FilteredSuperNetwork.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.phylo.Distortion;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.utils.TreesUtilities;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * filtered super network
 * Daniel Huson, 2006, 3.2018
 */
public class FilteredSuperNetwork extends SuperNetwork {
	private final IntegerProperty optionMinNumberTrees = new SimpleIntegerProperty(this, "MinNumberTrees", 1);
	private final IntegerProperty optionMaxDistortionScore = new SimpleIntegerProperty(this, "MaxDistortionScore", 0);
	private final BooleanProperty optionAllTrivial = new SimpleBooleanProperty(this, "AllTrivial", true);
	private final BooleanProperty optionUseTotalScore = new SimpleBooleanProperty(this, "UseTotalScore", false);


	@Override
	public String getCitation() {
		return "Whitfield et al.;James B. Whitfield, Sydney A. Cameron, Daniel H. Huson, Mike A. Steel. " +
			   "Filtered Z-Closure Supernetworks for Extracting and Visualizing Recurrent Signal from Incongruent Gene Trees, " +
			   "Systematic Biology, Volume 57, Issue 6, 1 December 2008, Pages 939–947.";
	}

	public List<String> listOptions() {
		return Arrays.asList("MinNumberTrees", "MaxDistortionScore", "AllTrivial", "UseTotalScore");
	}

	@Override
	public String getToolTip(String optionName) {
		return "Set the " + StringUtils.fromCamelCase(optionName).toLowerCase();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock trees, SplitsBlock child) throws IOException {
		// first compute splits using Z-closure method:
		SplitsBlock splits = new SplitsBlock();
		super.compute(progress, taxaBlock, trees, splits);

		progress.setSubtask("Processing trees");
		progress.setMaximum(splits.getNsplits());

		final BitSet[] tree2taxa = new BitSet[trees.getNTrees() + 1];
		for (int t = 1; t <= trees.getNTrees(); t++) {
			tree2taxa[t] = TreesUtilities.getTaxa(trees.getTree(t));
			//System.err.println("number of taxa in tree " + t + ":" + tree2taxa[t].cardinality());
			progress.setProgress(t);
		}

		progress.setSubtask("Processing splits");
		progress.setMaximum(splits.getNsplits() * trees.getNTrees());
		progress.setProgress(0);

		System.err.println("Filtering splits:");
		if (isOptionUseTotalScore()) {
			for (int s = 1; s <= splits.getNsplits(); s++) {
				int totalScore = 0;
				BitSet A = splits.get(s).getA();
				BitSet B = splits.get(s).getB();
				for (int t = 1; t <= trees.getNTrees(); t++) {
					final BitSet treeTaxa = tree2taxa[t];
					final BitSet treeTaxaAndA = (BitSet) (treeTaxa.clone());
					treeTaxaAndA.and(A);
					final BitSet treeTaxaAndB = (BitSet) (treeTaxa.clone());
					treeTaxaAndB.and(B);

					if (treeTaxaAndA.cardinality() > 1 && treeTaxaAndB.cardinality() > 1) {
						final PhyloTree tree = trees.getTree(t);
						totalScore += Distortion.computeDistortionForSplit(tree, A, B);
					}
					progress.incrementProgress();
				}
				if (totalScore <= getOptionMaxDistortionScore()) {
					final ASplit aSplit = splits.get(s);
					child.getSplits().add(new ASplit(aSplit.getA(), aSplit.getB(), aSplit.getWeight()));
				}
			}
		} else // do not use total score
		{
			for (int s = 1; s <= splits.getNsplits(); s++) {
				//System.err.print("s " + s + ":");
				final BitSet A = splits.get(s).getA();
				final BitSet B = splits.get(s).getB();
				int count = 0;
				for (int t = 1; t <= trees.getNTrees(); t++) {
					BitSet treeTaxa = tree2taxa[t];
					BitSet treeTaxaAndA = (BitSet) (treeTaxa.clone());
					treeTaxaAndA.and(A);
					BitSet treeTaxaAndB = (BitSet) (treeTaxa.clone());
					treeTaxaAndB.and(B);

					if (treeTaxaAndA.cardinality() > 1 && treeTaxaAndB.cardinality() > 1) {
						final PhyloTree tree = trees.getTree(t);
						int score = Distortion.computeDistortionForSplit(tree, A, B);
						//System.err.print(" " + score);
						if (score <= getOptionMaxDistortionScore())
							count++;
						if (count + (trees.getNTrees() - t) < getOptionMinNumberTrees())
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
				if ((isOptionAllTrivial() && (A.cardinality() == 1 || B.cardinality() == 1))
					|| count >= getOptionMinNumberTrees()) {
					final ASplit aSplit = splits.get(s);
					child.getSplits().add(new ASplit(aSplit.getA(), aSplit.getB(), aSplit.getWeight(), (float) count / (float) trees.getNTrees()));
				}
			}
		}
		System.err.println("Splits: " + splits.getNsplits() + " -> " + child.getNsplits());
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

	public boolean isOptionAllTrivial() {
		return optionAllTrivial.get();
	}

	public BooleanProperty optionAllTrivialProperty() {
		return optionAllTrivial;
	}

	public void setOptionAllTrivial(boolean optionAllTrivial) {
		this.optionAllTrivial.set(optionAllTrivial);
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
