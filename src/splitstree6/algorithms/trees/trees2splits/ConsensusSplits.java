/*
 * ConsensusSplits.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.SimpleObjectProperty;
import jloda.graph.algorithms.PQTree;
import jloda.util.BitSetUtils;
import jloda.util.CollectionUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

/**
 * implements consensus tree splits
 * <p>
 * Daniel Huson, 2.2018
 */
public class ConsensusSplits extends Trees2Splits {
	public enum Consensus {Majority, Strict, GreedyCompatible, GreedyPlanar, GreedyWeaklyCompatible} // todo: add loose?

	private final SimpleObjectProperty<Consensus> optionConsensus = new SimpleObjectProperty<>(this, "optionConsensus", Consensus.Majority);
	private final SimpleObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights", ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean);

	@Override
	public List<String> listOptions() {
		return List.of(optionConsensus.getName(), optionEdgeWeights.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionConsensus.getName()))
			return "Select consensus method";
		else if (optionName.equals(optionEdgeWeights.getName()))
			return "Determine how to calculate edge weights in resulting network";
		else
			return super.getToolTip(optionName);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		final ConsensusNetwork consensusNetwork = new ConsensusNetwork();
		switch (getOptionConsensus()) {
			default -> consensusNetwork.setOptionThresholdPercent(50);
			case Majority -> consensusNetwork.setOptionThresholdPercent(50);
			case Strict -> consensusNetwork.setOptionThresholdPercent(99.999999); // todo: implement without use of splits
			case GreedyCompatible, GreedyPlanar, GreedyWeaklyCompatible -> consensusNetwork.setOptionThresholdPercent(0);
		}

		final SplitsBlock consensusSplits = new SplitsBlock();
		consensusNetwork.setOptionEdgeWeights(getOptionEdgeWeights());
		consensusNetwork.setOptionHighDimensionFilter(false);
		consensusNetwork.compute(progress, taxaBlock, treesBlock, consensusSplits);

		splitsBlock.clear();

		switch (getOptionConsensus()) {
			case GreedyCompatible -> {
				final var list = new ArrayList<>(consensusSplits.getSplits());
				list.sort((s1, s2) -> {
					if (s1.getConfidence() > s2.getConfidence())
						return -1;
					else if (s1.getConfidence() < s2.getConfidence())
						return 1;
					else
						return BitSetUtils.compare(s1.getA(), s2.getA());
				});
				for (var split : list) {
					if (Compatibility.isCompatible(split, splitsBlock.getSplits()))
						splitsBlock.getSplits().add(split);
				}
				splitsBlock.setCompatibility(Compatibility.compatible);
				splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
			}
			case GreedyPlanar -> {
				final var list = new ArrayList<>(consensusSplits.getSplits());
				list.sort((s1, s2) -> {
					if (s1.getConfidence() > s2.getConfidence())
						return -1;
					else if (s1.getConfidence() < s2.getConfidence())
						return 1;
					else
						return BitSetUtils.compare(s1.getA(), s2.getA());
				});

				var pqTree = new PQTree(taxaBlock.getTaxaSet());
				for (var split : list) {
					var set = split.getPartNotContaining(1);
					if (pqTree.accept(set)) {
						splitsBlock.getSplits().add(split);
					}
				}
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));

				if (splitsBlock.getCompatibility() != Compatibility.compatible && splitsBlock.getCompatibility() != Compatibility.cyclic) {
					for (var split : splitsBlock.splits()) {
						if (!Compatibility.isCyclic(taxaBlock.getNtax(), List.of(split), splitsBlock.getCycle())) {
							System.err.println("Looks like a bug in the PQ-tree code, please contact me (Daniel Huson) about this");
							System.err.println("Internal error: greedyPlanar: is not circular: " + split);
							var cluster = split.getPartNotContaining(1);
							System.err.println("Set: " + StringUtils.toString(cluster));
							System.err.println("pqTree says: " + pqTree.check(cluster));
						}
					}
				}

				if (false) {
					progress.setSubtask("testing pqtree");
					progress.setMaximum(1000);
					progress.setProgress(0);
					var random = new Random(666);
					for (var run = 0; run < 1000; run++) {
						var pqTree1 = new PQTree(taxaBlock.getTaxaSet());
						var accepted = new ArrayList<BitSet>();
						var randomized = CollectionUtils.randomize(list, random);
						for (var split : randomized) {
							if (pqTree1.accept(split.getPartNotContaining(1))) {
								accepted.add(split.getPartNotContaining(1));
							}
						}
						BitSet bad = null;
						for (var cluster : accepted) {
							if (!pqTree1.check(cluster)) {
								System.err.println("Not accepted: " + StringUtils.toString(cluster));
								bad = cluster;
								break;
							}
						}
						if (bad != null) {
							var pqtree2 = new PQTree(taxaBlock.getTaxaSet());
							pqtree2.verbose = true;
							for (var split : randomized) {
								var cluster = split.getPartNotContaining(1);
								if (cluster.equals(bad))
									System.err.println("Bad: " + StringUtils.toString(bad));
								pqtree2.accept(cluster);
								if (!pqtree2.check(bad))
									System.err.println("Not accepted: " + StringUtils.toString(bad));
								progress.checkForCancel();
							}
						}
						progress.incrementProgress();
					}
					progress.reportTaskCompleted();
				}
			}
			case GreedyWeaklyCompatible -> {
				final var list = new ArrayList<>(consensusSplits.getSplits());
				list.sort((s1, s2) -> {
					if (s1.getConfidence() > s2.getConfidence())
						return -1;
					else if (s1.getConfidence() < s2.getConfidence())
						return 1;
					else
						return BitSetUtils.compare(s1.getA(), s2.getA());
				});
				for (var split : list) {
					if (Compatibility.isWeaklyCompatible(split, splitsBlock.getSplits()))
						splitsBlock.getSplits().add(split);
				}
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
			}
			case Majority, Strict -> {
				splitsBlock.getSplits().clear();
				splitsBlock.getSplits().addAll(consensusSplits.getSplits());
				splitsBlock.setCompatibility(Compatibility.compatible);
				splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
			}
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
	}

	public Consensus getOptionConsensus() {
		return optionConsensus.get();
	}

	public SimpleObjectProperty<Consensus> optionConsensusProperty() {
		return optionConsensus;
	}

	public void setOptionConsensus(Consensus optionConsensus) {
		this.optionConsensus.set(optionConsensus);
	}

	public void setOptionConsensus(String optionConsensus) {
		this.optionConsensus.set(Consensus.valueOf(optionConsensus));
	}

	public ConsensusNetwork.EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public SimpleObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(ConsensusNetwork.EdgeWeights optionEdgeWeights) {
		this.optionEdgeWeights.set(optionEdgeWeights);
	}
}
