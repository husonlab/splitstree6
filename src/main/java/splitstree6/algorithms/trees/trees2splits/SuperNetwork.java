/*
 * SuperNetwork.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.*;
import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2splits.DimensionFilter;
import splitstree6.algorithms.utils.PartialSplit;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.Compatibility;
import splitstree6.splits.SplitUtils;

import java.io.IOException;
import java.util.*;

/**
 * compute network from partial trees
 * <p>
 * Created on 22.06.2017
 *
 * @author Daniel Huson
 */

public class SuperNetwork extends Trees2Splits {
	public enum EdgeWeights {AverageRelative, Mean, TreeSizeWeightedMean, Sum, Min, None}

	private final BooleanProperty noOptionLeastSquare = new SimpleBooleanProperty(this, "noOptionLeastSquare", false); // todo this needs work
	private final BooleanProperty optionSuperTree = new SimpleBooleanProperty(this, "optionSuperTree", false);
	private final IntegerProperty optionNumberOfRuns = new SimpleIntegerProperty(this, "optionNumberOfRuns", 1);
	private final BooleanProperty optionApplyRefineHeuristic = new SimpleBooleanProperty(this, "optionApplyRefineHeuristic", false);
	private final IntegerProperty optionSeed = new SimpleIntegerProperty(this, "optionSeed", 0);
	private final SimpleObjectProperty<EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights", EdgeWeights.TreeSizeWeightedMean);
	private final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);

	@Override
	public String getCitation() {
		return "Huson et al 2004;DH Huson, T. Dezulian, T. Kloepper, and MA Steel. Phylogenetic super-networks from partial trees. " +
			   "IEEE/ACM Transactions in Computational Biology and Bioinformatics, 1(4):151–158, 2004.";
	}

	@Override
	public String getShortDescription() {
		return "Computes a super network using the Z-closure method.";
	}

	@Override
	public List<String> listOptions() {
		return Arrays.asList(optionEdgeWeights.getName(), optionSuperTree.getName(), optionNumberOfRuns.getName(),
				optionApplyRefineHeuristic.getName(), optionSeed.getName(), optionHighDimensionFilter.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;

		if (optionEdgeWeights.getName().equals(optionName)) {
			return "Determine how to calculate edge weights in resulting network";
		} else if (noOptionLeastSquare.getName().equals(optionName)) {
			return "Use least squares";
		} else if (optionSuperTree.getName().equals(optionName)) {
			return "Enforce the strong induction property, which results in a super tree";
		} else if (optionNumberOfRuns.getName().equals(optionName)) {
			return "Number of runs using random permutations of the input splits";
		} else if (optionApplyRefineHeuristic.getName().equals(optionName)) {
			return "Apply a simple refinement heuristic";
		} else if (optionSeed.getName().equals(optionName)) {
			return "Set seed used for random permutations";
		} else if (optionHighDimensionFilter.getName().equals(optionName)) {
			return "Heuristically remove splits causing high-dimensional network";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		if (treesBlock.getNTrees() == 0) {
			throw new IOException("No trees in input");
		}
		if (!treesBlock.isPartial()) {
			if (treesBlock.getNTrees() == 1) {
				NotificationManager.showInformation("SuperNetwork: Only one input tree, extracting all splits");
				SplitUtils.computeSplits(taxaBlock.getTaxaSet(), treesBlock.getTree(1), splitsBlock.getSplits());
				splitsBlock.setCompatibility(Compatibility.compatible);
				splitsBlock.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
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

		var pSplitsOfTrees = (Map<PartialSplit, PartialSplit>[]) new Map[treesBlock.getNTrees() + 1];
		// for each tree, identity map on set of splits
		var supportSet = new BitSet[treesBlock.getNTrees() + 1];
		var allPSplits = new HashSet<PartialSplit>();

		progress.setSubtask("extracting partial splits from trees");
		progress.setMaximum(treesBlock.getNTrees());

		for (var which = 1; which <= treesBlock.getNTrees(); which++) {
			progress.incrementProgress();
			pSplitsOfTrees[which] = new HashMap<>();
			supportSet[which] = new BitSet();
			computePartialSplits(taxaBlock, treesBlock, which, pSplitsOfTrees[which], supportSet[which]);
			for (var ps : pSplitsOfTrees[which].keySet()) {
				if (ps.isNonTrivial()) {
					allPSplits.add((PartialSplit) ps.clone());
					progress.incrementProgress();
				}
			}
		}

		computeClosureOuterLoop(progress, allPSplits);

		if (isOptionApplyRefineHeuristic()) {
			progress.setSubtask("Refinement heuristic");
			applyRefineHeuristic(allPSplits);
		}

		////doc.notifySubtask("collecting full splits");
		////doc.notifySetMaximumProgress(allPSplits.size());
		var computedSplits = new SplitsBlock();

		for (PartialSplit ps : allPSplits) {
			var size = ps.getXsize();

			// for now, keep all splits of correct size
			if (size == taxaBlock.getNtax()) {
				var ok = true;
				if (isOptionSuperTree()) {
					for (var t = 1; ok && t <= treesBlock.getNTrees(); t++) {
						var pSplits = (pSplitsOfTrees[t]);
						var support = supportSet[t];
						var induced = ps.getInduced(support);
						if (induced != null && !pSplits.containsKey(induced))
							ok = false;     // found a tree that doesn't contain the induced split
					}
				}
				if (ok) {
					var split = new ASplit(ps.getA(), taxaBlock.getNtax());
					computedSplits.getSplits().add(split);
				}
			}
		}

		// add all missing trivial splits
		computedSplits.getSplits().addAll(SplitsBlockUtilities.createAllMissingTrivial(computedSplits.getSplits(), taxaBlock.getNtax(), 0.0));

		if (getOptionEdgeWeights().equals(EdgeWeights.AverageRelative)) {
			setWeightAverageReleativeLength(pSplitsOfTrees, supportSet, computedSplits);
		} else if (!getOptionEdgeWeights().equals(EdgeWeights.None)) {
			setWeightsConfidences(pSplitsOfTrees, supportSet, computedSplits);
		}

		if (isOptionHighDimensionFilter()) {
			var dimensionsFilter = new DimensionFilter();
			dimensionsFilter.compute(progress, taxaBlock, computedSplits, splitsBlock);
		} else
			splitsBlock.copy(computedSplits);

		splitsBlock.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
		progress.close();
	}


	/**
	 * set the weight to the mean weight of all projections of this split and confidence to
	 * the count of trees containing a projection of the split
	 */
	private void setWeightsConfidences(Map[] pSplits, BitSet[] supportSet, SplitsBlock splits) {
		for (int s = 1; s <= splits.getNsplits(); s++) {
			final PartialSplit current = new PartialSplit(splits.getSplits().get(s - 1).getA(),
					splits.getSplits().get(s - 1).getB());
			//new PartialSplit(splits.get(s),
			//splits.get(s).getComplement(taxa.getNtax()));

			var min = 1000000f;
			var sum = 0f;
			var weighted = 0f;
			var confidence = 0f;
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

			var value = switch (getOptionEdgeWeights()) {
				case Min -> min;
				case Mean -> (total > 0 ? weighted / total : 0);
				case TreeSizeWeightedMean -> (total > 0 ? sum / total : 0);
				case Sum -> sum;
				default -> 1;
			};
			splits.getSplits().get(s - 1).setWeight(value);
			// splits.getSplits().get(s - 1).setConfidence(total);
		}
	}

	/**
	 * sets the weight of a split in the network as the average relative length of the edge
	 * in the input trees
	 */
	private void setWeightAverageReleativeLength(Map[] pSplits, BitSet[] supportSet, SplitsBlock splits) {
		// compute average of weights and num of edges for each input tree
		var averageWeight = new float[pSplits.length];
		var numEdges = new int[pSplits.length];

		for (var t = 1; t < pSplits.length; t++) {
			numEdges[t] = pSplits[t].size();
			var sum = 0f;
			for (var o : pSplits[t].keySet()) {
				PartialSplit ps = (PartialSplit) o;
				sum += ps.getWeight();
			}
			averageWeight[t] = sum / numEdges[t];
		}

		// consider each network split in turn:
		for (var s = 1; s <= splits.getNsplits(); s++) {
			//doc.notifySetProgress(-1);
			PartialSplit current = new PartialSplit(splits.getSplits().get(s - 1).getA(), splits.getSplits().get(s - 1).getB());
			//new PartialSplit(splits.get(s),
			//splits.get(s).getComplement(taxa.getNtax()));

			var activeTrees = new BitSet(); // trees that contain projection of
			// current split

			for (var t = 1; t < pSplits.length; t++) {
				PartialSplit projection = current.getInduced(supportSet[t]);
				if (projection != null && pSplits[t].containsKey(projection)) {
					activeTrees.set(t);
				}
			}

			var weight = 0f;
			for (var t = activeTrees.nextSetBit(1); t >= 0; t = activeTrees.nextSetBit(t + 1)) {
				var projection = current.getInduced(supportSet[t]);

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
	 * @param pSplitsOfTree partial splits are returned here
	 * @param support       supporting taxa are returned here
	 */
	private void computePartialSplits(TaxaBlock taxa, TreesBlock trees, int which, Map<PartialSplit, PartialSplit> pSplitsOfTree, BitSet support) {
		final var list = new ArrayList<PartialSplit>(); // list of (onesided) partial splits
		var v = trees.getTrees().get(which - 1).getFirstNode();
		computePSplitsFromTreeRecursively(v, null, trees, taxa, list, which, support);

		for (var ps : list) {
			ps.setComplement(support);
			pSplitsOfTree.put(ps, ps);
		}
	}

	// recursively compute the splits:

	private BitSet computePSplitsFromTreeRecursively(Node v, Edge e, TreesBlock trees, TaxaBlock taxa, List<PartialSplit> list, int which, BitSet seen) {
		var tree = trees.getTrees().get(which - 1);
		var e_taxa = new BitSet();
		if (taxa.indexOf(tree.getLabel(v)) != -1)
			e_taxa.set(taxa.indexOf(tree.getLabel(v)));

		seen.or(e_taxa);

		for (var f : v.adjacentEdges()) {
			if (f != e) {
				final var f_taxa = computePSplitsFromTreeRecursively(tree.getOpposite(v, f), f, trees, taxa, list, which, seen);
				var ps = new PartialSplit(f_taxa);
				ps.setWeight((float) tree.getWeight(f));
				list.add(ps);
				for (var t = 1; t < f_taxa.length(); t++) {
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
	 */
	private void computeClosureOuterLoop(ProgressListener progress, Set<PartialSplit> partialSplits) throws CanceledException {
		this.rand = new Random(getOptionSeed());

		final var allEverComputed = new HashSet<>(partialSplits);

		for (var i = 0; i < getOptionNumberOfRuns(); i++) {
			////doc.notifySubtask("compute closure" + (i == 0 ? "" : "(" + (i + 1) + ")"));

			var clone = new LinkedHashSet<>(partialSplits);

			{
				final var tmp = new Vector<>(clone);
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
	 * @return number of full splits
	 */
	public int getNumberOfFullSplits(int numAllTaxa, Set<PartialSplit> partialSplits) {
		int nfs = 0;
		for (var partialSplit1 : partialSplits) {
			if (partialSplit1.getXsize() == numAllTaxa) nfs++;
		}
		return nfs;
	}


	/**
	 * computes the split closure obtained using the zig-zap rule
	 */
	private void computeClosure(ProgressListener progress, Set<PartialSplit> partialSplits) throws CanceledException {
		PartialSplit[] splits;
		var seniorSplits = new LinkedHashSet<Integer>();
		var activeSplits = new LinkedHashSet<Integer>();
		var newSplits = new LinkedHashSet<Integer>();
		{
			splits = new PartialSplit[partialSplits.size()];
			var pos = 0;
			for (var partialSplit : partialSplits) {
				splits[pos] = partialSplit;
				seniorSplits.add(pos);
				pos++;
				progress.checkForCancel();
			}
		}

		// init:
		{
			for (var pos1 = 0; pos1 < splits.length; pos1++) {
				for (var pos2 = pos1 + 1; pos2 < splits.length; pos2++) {
					var ps1 = splits[pos1];
					var ps2 = splits[pos2];
					var qs1 = new PartialSplit();
					var qs2 = new PartialSplit();
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
				newSplits = new LinkedHashSet<>();

				for (var pos1 : seniorSplits) {
					for (var pos2 : activeSplits) {
						var ps1 = splits[pos1];
						var ps2 = splits[pos2];
						var qs1 = new PartialSplit();
						var qs2 = new PartialSplit();
						if (PartialSplit.applyZigZagRule(ps1, ps2, qs1, qs2)) {
							splits[pos1] = qs1;
							splits[pos2] = qs2;
							newSplits.add(pos1);
							newSplits.add(pos2);
						}
					}
					progress.checkForCancel();
				}
				for (var pos1 : activeSplits) {
					for (var activeSplit : activeSplits) {
						var ps1 = splits[pos1];
						var ps2 = splits[activeSplit];
						var qs1 = new PartialSplit();
						var qs2 = new PartialSplit();
						if (PartialSplit.applyZigZagRule(ps1, ps2, qs1, qs2)) {
							splits[pos1] = qs1;
							splits[activeSplit] = qs2;
							newSplits.add(pos1);
							newSplits.add(activeSplit);
						}
					}
					progress.checkForCancel();
				}
			}
		}

		partialSplits.clear();
		for (var pos1 : seniorSplits) {
			partialSplits.add(splits[pos1]);
			progress.checkForCancel();
		}
		for (var pos1 : activeSplits) {
			partialSplits.add(splits[pos1]);
			progress.checkForCancel();
		}
	}

	/**
	 * applies a simple refinement heuristic
	 */
	private void applyRefineHeuristic(Set<PartialSplit> partialSplits) {


		for (var i = 1; i <= 10; i++) {
			var count = 0;
			var splits = partialSplits.toArray(new PartialSplit[0]);

			for (var a = 0; a < splits.length; a++) {
				//doc.notifySetMaximumProgress(a);
				final var psa = splits[a];
				for (int p = 1; p <= 2; p++) {
					final BitSet Aa, Ba;
					if (p == 1) {
						Aa = psa.getA();
						Ba = psa.getB();
					} else {
						Aa = psa.getB();
						Ba = psa.getA();
					}
					for (var b = a + 1; b < splits.length; b++) {
						final var psb = splits[b];
						for (var q = 1; q <= 2; q++) {
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

	public boolean isOptionHighDimensionFilter() {
		return optionHighDimensionFilter.get();
	}

	public BooleanProperty optionHighDimensionFilterProperty() {
		return optionHighDimensionFilter;
	}

	public void setOptionHighDimensionFilter(boolean optionHighDimensionFilter) {
		this.optionHighDimensionFilter.set(optionHighDimensionFilter);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isReticulated();
	}
}
