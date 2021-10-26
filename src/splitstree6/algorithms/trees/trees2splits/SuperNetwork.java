/*
 * SuperNetwork.java Copyright (C) 2021. Daniel H. Huson
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

import javafx.beans.property.*;
import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressPercentage;
import splitstree6.algorithms.splits.splits2splits.LeastSquaresWeights;
import splitstree6.algorithms.trees.trees2distances.AverageDistances;
import splitstree6.algorithms.utils.PartialSplit;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;
import splitstree6.utils.SplitsUtilities;
import splitstree6.utils.TreesUtilities;

import java.io.IOException;
import java.util.*;

/**
 * compute network from partial trees
 * <p>
 * Created on 22.06.2017
 *
 * @author Daniel Huson and Daria Evseeva
 */

public class SuperNetwork extends Trees2Splits {
	public enum EdgeWeights {AverageRelative, Mean, TreeSizeWeightedMean, Sum, Min, None}

	private final BooleanProperty optionZRule = new SimpleBooleanProperty(true);
	private final BooleanProperty noOptionLeastSquare = new SimpleBooleanProperty(false); // todo this needs work
	private final BooleanProperty optionSuperTree = new SimpleBooleanProperty(false);
	private final IntegerProperty optionNumberOfRuns = new SimpleIntegerProperty(1);
	private final BooleanProperty optionApplyRefineHeuristic = new SimpleBooleanProperty(false);
	private final IntegerProperty optionSeed = new SimpleIntegerProperty(0);

	private final SimpleObjectProperty<EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(EdgeWeights.TreeSizeWeightedMean);

	@Override
	public String getCitation() {
		return "Huson et al 2004;D.H. Huson, T. Dezulian, T. Kloepper, and M. A. Steel. Phylogenetic super-networks from partial trees. " +
			   "IEEE/ACM Transactions in Computational Biology and Bioinformatics, 1(4):151–158, 2004.";
	}

	@Override
	public List<String> listOptions() {
		return Arrays.asList("EdgeWeights", "ZRule", "SuperTree", "NumberOfRuns", "ApplyRefineHeuristic", "LeastSquare", "Seed");
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "EdgeWeights" -> "Determine how to calculate edge weights in resulting network";
			case "LeastSquare" -> "Use least squares";
			case "ZRule" -> "Apply the Z-closure rule";
			case "SuperTree" -> "Enforce the strong induction property, which results in a super tree";
			case "NumberOfRuns" -> "Number of runs using random permutations of the input splits";
			case "ApplyRefineHeuristic" -> "Apply a simple refinement heuristic";
			case "Seed" -> "Set seed used for random permutations";
			default -> optionName;
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		if (treesBlock.getNTrees() == 0) {
			throw new IOException("No trees in input");
		}
		if (!treesBlock.isPartial()) {
			if (treesBlock.getNTrees() == 1) {
				NotificationManager.showInformation("SuperNetwork: Only one input tree, extracting all splits");
				TreesUtilities.computeSplits(taxaBlock.getTaxaSet(), treesBlock.getTree(1), splitsBlock.getSplits());
				splitsBlock.setCompatibility(Compatibility.compatible);
				splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
				return;
			} else {
				NotificationManager.showInformation("SuperNetwork: None of the input trees are partial, computing 50% consensus network using TreeSizeWeightedMean edge weights");
				final ConsensusNetwork consensusNetwork = new ConsensusNetwork();
				consensusNetwork.setOptionThresholdPercent(50);
				consensusNetwork.setOptionEdgeWeights(ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean);
				consensusNetwork.compute(progress, taxaBlock, treesBlock, splitsBlock);
				return;
			}
		}
		/*
		 * Determine the set of taxa for partial trees.
		 * If the block contains partial trees, then the translate statement must mention all
		 * taxa. We use this info to build a taxa block
		 */
		// todo move to trees block?
        /*if (treesBlock.isPartial()){

            // trees.setTaxaFromPartialTrees(taxa);

            // contains partial trees, most determine
            // full set of taxa
            Set<String> taxaLabels = new HashSet<>();
            for (int i = 0; i < treesBlock.getNTrees(); i++) {
                PhyloTree tree = treesBlock.getTrees().get(i);
                Set<String> nodeLabels = tree.nodeLabels();

                for (String nodeLabel : nodeLabels) {
                    //taxaLabels.add(translate.get(nodeLabel));
                    taxaLabels.add(nodeLabel);
                }
            }

            //are these taxa equals taxa, if so, do nothing:
            boolean areLabelsInTaxa = true;
            for(String label : taxaLabels){
                if(!taxaBlock.getLabel().contains(label)) {
                    areLabelsInTaxa = false;
                    break;
                }
            }
            if (taxaBlock.getNtax() == taxaLabels.size() && areLabelsInTaxa)
                return;

            // if they are contained in the original taxa, unhide them:
            if (taxaBlock.getTaxa() != null && areLabelsInTaxa) {
                BitSet toHide = new BitSet();
                for (int t = 1; t <= taxa.getOriginalTaxa().getNtax(); t++)
                    if (!taxaLabels.contains(taxa.getOriginalTaxa().getLabel(t)))
                        toHide.set(t);
                taxa.hideTaxa(toHide);
            } else {
                taxa.setNtax(taxaLabels.size());
                Iterator it = taxaLabels.iterator();
                int t = 0;
                while (it.hasNext()) {
                    taxa.setLabel(++t, (String) it.next());
                }
            }

        }*/


		progress.setTasks("Z-closure", "init");

		Map[] pSplitsOfTrees = new Map[treesBlock.getNTrees() + 1];
		// for each tree, identity map on set of splits
		BitSet[] supportSet = new BitSet[treesBlock.getNTrees() + 1];
		Set<PartialSplit> allPSplits = new HashSet<>();

		progress.setSubtask("extracting partial splits from trees");
		progress.setMaximum(treesBlock.getNTrees());

		for (int which = 1; which <= treesBlock.getNTrees(); which++) {
			progress.incrementProgress();
			pSplitsOfTrees[which] = new HashMap();
			supportSet[which] = new BitSet();
			computePartialSplits(taxaBlock, treesBlock, which, pSplitsOfTrees[which], supportSet[which]);
			for (Object o : pSplitsOfTrees[which].keySet()) {
				PartialSplit ps = (PartialSplit) o;
				if (ps.isNonTrivial()) {
					allPSplits.add((PartialSplit) ps.clone());
					progress.incrementProgress();
				}
			}
		}
		SplitsBlock splits = new SplitsBlock();

		if (isOptionZRule()) {
			computeClosureOuterLoop(progress, allPSplits);
		}

		if (isOptionApplyRefineHeuristic()) {
			progress.setSubtask("Refinement heuristic");
			applyRefineHeuristic(allPSplits);
		}

		////doc.notifySubtask("collecting full splits");
		////doc.notifySetMaximumProgress(allPSplits.size());
		for (PartialSplit ps : allPSplits) {
			int size = ps.getXsize();

			// for now, keep all splits of correct size
			if (size == taxaBlock.getNtax()) {
				boolean ok = true;
				if (isOptionSuperTree()) {
					for (int t = 1; ok && t <= treesBlock.getNTrees(); t++) {
						Map pSplits = (pSplitsOfTrees[t]);
						BitSet support = supportSet[t];
						PartialSplit induced = ps.getInduced(support);
						if (induced != null && !pSplits.containsKey(induced))
							ok = false;     // found a tree that doesn't contain the induced split
					}
				}
				if (ok) {
					ASplit split = new ASplit(ps.getA(), taxaBlock.getNtax());
					splits.getSplits().add(split);
				}
			}
		}

		// add all missing trivial splits
		for (int t = 1; t <= taxaBlock.getNtax(); t++) {
			BitSet ts = new BitSet();
			ts.set(t);
			PartialSplit ps = new PartialSplit(ts);
			BitSet ts1 = new BitSet();
			ts1.set(1, taxaBlock.getNtax() + 1);
			ps.setComplement(ts1);
			if (!allPSplits.contains(ps)) {
				ASplit split = new ASplit(ps.getA(), taxaBlock.getNtax());
				splits.getSplits().add(split);
			}

		}

		if (getOptionEdgeWeights().equals(EdgeWeights.AverageRelative)) {
			setWeightAverageReleativeLength(pSplitsOfTrees, supportSet, splits);
		} else if (!getOptionEdgeWeights().equals(EdgeWeights.None)) {
			setWeightsConfidences(pSplitsOfTrees, supportSet, splits);
		}

		// todo how do we get here ?
		if (getNoOptionLeastSquare()) {
			if (!TreesUtilities.hasAllPairs(taxaBlock, treesBlock)) {
				NotificationManager.showWarning("Partial trees don't have the 'All Pairs' property, can't computeCycle Least Squares");
				setNoOptionLeastSquare(false);
			} else {
				DistancesBlock distances = new DistancesBlock();
				AverageDistances ad = new AverageDistances();
				ad.compute(new ProgressPercentage(), taxaBlock, treesBlock, distances);

				LeastSquaresWeights leastSquares = new LeastSquaresWeights();
				leastSquares.setDistancesBlock(distances);

				leastSquares.compute(new ProgressPercentage(), taxaBlock, splits, splitsBlock);
			}
		}

		splitsBlock.copy(splits);
		splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
		progress.close();
	}


	/**
	 * set the weight to the mean weight of all projections of this split and confidence to
	 * the count of trees containing a projection of the split
	 *
	 * @param pSplits
	 * @param supportSet
	 * @param splits
	 */
	private void setWeightsConfidences(Map[] pSplits, BitSet[] supportSet, SplitsBlock splits) {
		for (int s = 1; s <= splits.getNsplits(); s++) {
			final PartialSplit current = new PartialSplit(splits.getSplits().get(s - 1).getA(),
					splits.getSplits().get(s - 1).getB());
			//new PartialSplit(splits.get(s),
			//splits.get(s).getComplement(taxa.getNtax()));

			float min = 1000000;
			float sum = 0;
			float weighted = 0;
			float confidence = 0;
			int total = 0;
			for (int t = 1; t < pSplits.length; t++) {
				PartialSplit projection = current.getInduced(supportSet[t]);
				if (projection != null)  // split cuts support set of tree t
				{
					if (pSplits[t].containsKey(projection)) {
						float cur = ((PartialSplit) pSplits[t].get(projection)).getWeight();
						weighted += supportSet[t].cardinality() * cur;
						if (cur < min)
							min = cur;
						sum += cur;
						confidence += supportSet[t].cardinality() * ((PartialSplit) pSplits[t].get(projection)).getConfidence();
					}
					total += supportSet[t].cardinality();
				}
			}

			float value = 1;
			switch (getOptionEdgeWeights()) {
				case Min:
					value = min;
					break;
				case Mean:
					value = (total > 0 ? weighted / total : 0);
					break;
				case TreeSizeWeightedMean:
					value = (total > 0 ? sum / total : 0);
					break;
				case Sum:
					value = sum;
					break;
			}
			splits.getSplits().get(s - 1).setWeight(value);
			splits.getSplits().get(s - 1).setConfidence(total);
		}
	}

	/**
	 * sets the weight of a split in the network as the average relative length of the edge
	 * in the input trees
	 *
	 * @param pSplits
	 * @param supportSet
	 * @param splits
	 * @throws CanceledException
	 */
	private void setWeightAverageReleativeLength(Map[] pSplits, BitSet[] supportSet, SplitsBlock splits) {
		// compute average of weights and num of edges for each input tree
		float[] averageWeight = new float[pSplits.length];
		int[] numEdges = new int[pSplits.length];

		for (int t = 1; t < pSplits.length; t++) {
			numEdges[t] = pSplits[t].size();
			float sum = 0;
			for (Object o : pSplits[t].keySet()) {
				PartialSplit ps = (PartialSplit) o;
				sum += ps.getWeight();
			}
			averageWeight[t] = sum / numEdges[t];
		}

		// consider each network split in turn:
		for (int s = 1; s <= splits.getNsplits(); s++) {
			//doc.notifySetProgress(-1);
			PartialSplit current = new PartialSplit(splits.getSplits().get(s - 1).getA(), splits.getSplits().get(s - 1).getB());
			//new PartialSplit(splits.get(s),
			//splits.get(s).getComplement(taxa.getNtax()));

			BitSet activeTrees = new BitSet(); // trees that contain projection of
			// current split

			for (int t = 1; t < pSplits.length; t++) {
				PartialSplit projection = current.getInduced(supportSet[t]);
				if (projection != null && pSplits[t].containsKey(projection)) {
					activeTrees.set(t);
				}
			}

			float weight = 0;
			for (int t = activeTrees.nextSetBit(1); t >= 0; t = activeTrees.nextSetBit(t + 1)) {
				PartialSplit projection = current.getInduced(supportSet[t]);

				weight += ((PartialSplit) pSplits[t].get(projection)).getWeight()
						  / averageWeight[t];
			}
			weight /= activeTrees.cardinality();
			splits.getSplits().get(s - 1).setWeight(weight); //setWeight(s, weight);
		}
	}

	/**
	 * returns the set of all partial splits in the given tree
	 *
	 * @param trees
	 * @param which
	 * @param pSplitsOfTree partial splits are returned here
	 * @param support       supporting taxa are returned here
	 */
	private void computePartialSplits(TaxaBlock taxa, TreesBlock trees, int which, Map<PartialSplit, PartialSplit> pSplitsOfTree, BitSet support) {
		final ArrayList<PartialSplit> list = new ArrayList<>(); // list of (onesided) partial splits
		Node v = trees.getTrees().get(which - 1).getFirstNode();
		computePSplitsFromTreeRecursively(v, null, trees, taxa, list, which, support);

		for (Object aList : list) {
			PartialSplit ps = (PartialSplit) aList;
			ps.setComplement(support);
			pSplitsOfTree.put(ps, ps);
		}
	}

	// recursively compute the splits:

	private BitSet computePSplitsFromTreeRecursively(Node v, Edge e, TreesBlock trees, TaxaBlock taxa, List<PartialSplit> list, int which, BitSet seen) {
		PhyloTree tree = trees.getTrees().get(which - 1);
		BitSet e_taxa = new BitSet();
		if (taxa.indexOf(tree.getLabel(v)) != -1)
			e_taxa.set(taxa.indexOf(tree.getLabel(v)));

		seen.or(e_taxa);

		for (Edge f : v.adjacentEdges()) {
			if (f != e) {
				final BitSet f_taxa = computePSplitsFromTreeRecursively(tree.getOpposite(v, f), f, trees, taxa, list, which, seen);
				PartialSplit ps = new PartialSplit(f_taxa);
				ps.setWeight((float) tree.getWeight(f));
				list.add(ps);
				for (int t = 1; t < f_taxa.length(); t++) {
					if (f_taxa.get(t))
						e_taxa.set(t);
				}
			}
		}
		return e_taxa;
	}

	Random rand = null;

	/**
	 * runs the closure method. Does this multiple times, if desired
	 *
	 * @param partialSplits
	 * @throws CanceledException
	 */
	private void computeClosureOuterLoop(ProgressListener progress, Set<PartialSplit> partialSplits) throws CanceledException {
		this.rand = new Random(getOptionSeed());

		final Set<PartialSplit> allEverComputed = new HashSet<>(partialSplits);

		for (int i = 0; i < getOptionNumberOfRuns(); i++) {
			////doc.notifySubtask("compute closure" + (i == 0 ? "" : "(" + (i + 1) + ")"));

			Set<PartialSplit> clone = new LinkedHashSet<>(partialSplits);

			{
				final Vector<PartialSplit> tmp = new Vector<>(clone);
				Collections.shuffle(tmp, rand);
				clone = new LinkedHashSet<>(tmp);
				computeClosure(progress, clone);
				progress.checkForCancel();
			}

			allEverComputed.addAll(clone);

		}
		partialSplits.clear();
		partialSplits.addAll(allEverComputed);
	}

	/**
	 * gets the number of full splits
	 *
	 * @param numAllTaxa
	 * @param partialSplits
	 * @return number of full splits
	 */
	public int getNumberOfFullSplits(int numAllTaxa, Set partialSplits) {
		int nfs = 0;
		for (Object partialSplit1 : partialSplits) {
			PartialSplit partialSplit = (PartialSplit) partialSplit1;
			if (partialSplit.getXsize() == numAllTaxa) nfs++;
		}
		return nfs;
	}


	/**
	 * computes the split closure obtained using the zig-zap rule
	 *
	 * @param partialSplits
	 */
	private void computeClosure(ProgressListener progress, Set<PartialSplit> partialSplits) throws CanceledException {

		PartialSplit[] splits;
		Set<Integer> seniorSplits = new LinkedHashSet<>();
		Set<Integer> activeSplits = new LinkedHashSet<>();
		Set<Integer> newSplits = new LinkedHashSet<>();
		{
			splits = new PartialSplit[partialSplits.size()];
			Iterator it = partialSplits.iterator();
			int pos = 0;
			while (it.hasNext()) {
				splits[pos] = (PartialSplit) it.next();
				seniorSplits.add(pos);
				pos++;
				progress.checkForCancel();
			}
		}

		// init:
		{
			for (int pos1 = 0; pos1 < splits.length; pos1++) {

				for (int pos2 = pos1 + 1; pos2 < splits.length; pos2++) {
					PartialSplit ps1 = splits[pos1];
					PartialSplit ps2 = splits[pos2];
					PartialSplit qs1 = new PartialSplit();
					PartialSplit qs2 = new PartialSplit();
					if (PartialSplit.applyZigZagRule(ps1, ps2, qs1, qs2)) {
						splits[pos1] = qs1;
						splits[pos2] = qs2;
						newSplits.add(pos1);
						newSplits.add(pos2);
					}
				}
				progress.checkForCancel();
			}
		}

		// main loop:
		{
			while (newSplits.size() != 0) {
				seniorSplits.addAll(activeSplits);
				activeSplits = newSplits;
				newSplits = new HashSet<>();

				Iterator it1 = seniorSplits.iterator();
				while (it1.hasNext()) {
					Integer pos1 = ((Integer) it1.next());

					for (Object activeSplit : activeSplits) {
						Integer pos2 = ((Integer) activeSplit);
						PartialSplit ps1 = splits[pos1];
						PartialSplit ps2 = splits[pos2];
						PartialSplit qs1 = new PartialSplit();
						PartialSplit qs2 = new PartialSplit();
						if (PartialSplit.applyZigZagRule(ps1, ps2, qs1, qs2)) {
							splits[pos1] = qs1;
							splits[pos2] = qs2;
							newSplits.add(pos1);
							newSplits.add(pos2);
						}
					}
					progress.checkForCancel();
				}
				it1 = activeSplits.iterator();
				while (it1.hasNext()) {
					Integer pos1 = ((Integer) it1.next());

					for (Object activeSplit : activeSplits) {
						Integer pos2 = ((Integer) activeSplit);
						PartialSplit ps1 = splits[pos1];
						PartialSplit ps2 = splits[pos2];
						PartialSplit qs1 = new PartialSplit();
						PartialSplit qs2 = new PartialSplit();
						if (PartialSplit.applyZigZagRule(ps1, ps2, qs1, qs2)) {
							splits[pos1] = qs1;
							splits[pos2] = qs2;
							newSplits.add(pos1);
							newSplits.add(pos2);
						}
					}
					progress.checkForCancel();
				}
			}
		}

		partialSplits.clear();
		Iterator it = seniorSplits.iterator();
		while (it.hasNext()) {
			Integer pos1 = (Integer) it.next();
			partialSplits.add(splits[pos1]);
			progress.checkForCancel();
		}
		it = activeSplits.iterator();
		while (it.hasNext()) {
			Integer pos1 = (Integer) it.next();
			partialSplits.add(splits[pos1]);
			progress.checkForCancel();
		}
	}

	/**
	 * applies a simple refinement heuristic
	 *
	 * @param partialSplits
	 */
	private void applyRefineHeuristic(Set partialSplits) {


		for (int i = 1; i <= 10; i++) {
			int count = 0;
			PartialSplit[] splits = new PartialSplit[partialSplits.size()];
			splits = (PartialSplit[]) partialSplits.toArray(splits);

			for (int a = 0; a < splits.length; a++) {
				//doc.notifySetMaximumProgress(a);
				final PartialSplit psa = splits[a];
				for (int p = 1; p <= 2; p++) {
					final BitSet Aa, Ba;
					if (p == 1) {
						Aa = psa.getA();
						Ba = psa.getB();
					} else {
						Aa = psa.getB();
						Ba = psa.getA();
					}
					for (int b = a + 1; b < splits.length; b++) {
						final PartialSplit psb = splits[b];
						for (int q = 1; q <= 2; q++) {
							final BitSet Ab, Bb;
							if (q == 1) {
								Ab = psb.getA();
								Bb = psb.getB();
							} else {
								Ab = psb.getB();
								Bb = psb.getA();
							}
							if (Aa.intersects(Ab)
								&& !Ba.intersects(Ab) && !Bb.intersects(Aa)
								&& Ba.intersects(Bb)) {
								PartialSplit ps = new PartialSplit(PartialSplit.union(Aa, Ab), PartialSplit.union(Ba, Bb));
								if (!partialSplits.contains(ps)) {
									partialSplits.add(ps);
									count++;
								}
							}
						}
					}
				}
			}
			System.err.println("# Refinement heuristic [" + i + "] added " + count + " partial splits");
			if (count == 0)
				break;
		}
	}

	public boolean isOptionZRule() {
		return optionZRule.get();
	}

	public BooleanProperty optionZRuleProperty() {
		return optionZRule;
	}

	public void setOptionZRule(boolean optionZRule) {
		this.optionZRule.set(optionZRule);
	}

	public boolean getNoOptionLeastSquare() {
		return noOptionLeastSquare.get();
	}

	public BooleanProperty noOptionLeastSquareProperty() {
		return noOptionLeastSquare;
	}

	public void setNoOptionLeastSquare(boolean noOptionLeastSquare) {
		this.noOptionLeastSquare.set(noOptionLeastSquare);
	}

	public boolean isOptionSuperTree() {
		return optionSuperTree.get();
	}

	public BooleanProperty optionSuperTreeProperty() {
		return optionSuperTree;
	}

	public void setOptionSuperTree(boolean optionSuperTree) {
		this.optionSuperTree.set(optionSuperTree);
	}

	public int getOptionNumberOfRuns() {
		return optionNumberOfRuns.get();
	}

	public IntegerProperty optionNumberOfRunsProperty() {
		return optionNumberOfRuns;
	}

	public void setOptionNumberOfRuns(int optionNumberOfRuns) {
		this.optionNumberOfRuns.set(optionNumberOfRuns);
	}

	public boolean isOptionApplyRefineHeuristic() {
		return optionApplyRefineHeuristic.get();
	}

	public BooleanProperty optionApplyRefineHeuristicProperty() {
		return optionApplyRefineHeuristic;
	}

	public void setOptionApplyRefineHeuristic(boolean optionApplyRefineHeuristic) {
		this.optionApplyRefineHeuristic.set(optionApplyRefineHeuristic);
	}

	public int getOptionSeed() {
		return optionSeed.get();
	}

	public IntegerProperty optionSeedProperty() {
		return optionSeed;
	}

	public void setOptionSeed(int optionSeed) {
		this.optionSeed.set(optionSeed);
	}

	public EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public SimpleObjectProperty<EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(EdgeWeights optionEdgeWeights) {
		this.optionEdgeWeights.set(optionEdgeWeights);
	}
}
