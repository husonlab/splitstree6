/*
 * NeighborNet.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeights;
import splitstree6.algorithms.splits.IToCircularSplits;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.util.ArrayList;
import java.util.List;

public class NeighborNet extends Distances2Splits implements IToCircularSplits {

	//public enum InferenceAlgorithm {ActiveSet, BlockPivot}
	public enum InferenceAlgorithm {FastMethod,CarefulMethod,LegacySplitstree4,ProjectedGradient,BlockPivot}

	private final ObjectProperty<InferenceAlgorithm> optionInferenceAlgorithm = new SimpleObjectProperty<>(this, "optionInferenceAlgorithm", InferenceAlgorithm.FastMethod);

	//private final BooleanProperty optionUsePreconditioner = new SimpleBooleanProperty(this, "optionUsePreconditioner", false);

	//private final BooleanProperty optionUseDual = new SimpleBooleanProperty(this, "optionUseDual", true);

	public List<String> listOptions() {
		return List.of(optionInferenceAlgorithm.getName());
	}

	@Override
	public String getCitation() {
		return "Bryant & Moulton 2004; " +
			   "D. Bryant and V. Moulton. Neighbor-net: An agglomerative method for the construction of phylogenetic networks. " +
			   "Molecular Biology and Evolution, 21(2):255– 265, 2004.";
	}

	/**
	 * run the neighbor net algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock splitsBlock) throws CanceledException {

		if (SplitsUtilities.computeSplitsForLessThan4Taxa(taxaBlock, distancesBlock, splitsBlock))
			return; //TODO: Incorporate this into later code.

		progress.setMaximum(-1);
		final var cycle = NeighborNetCycle.compute(progress, distancesBlock.size(), distancesBlock.getDistances());

		progress.setTasks("NNet", "split weight optimization");

		final ArrayList<ASplit> splits;

		final var start = System.currentTimeMillis();

		NeighborNetSplitWeights.NNLSParams params = new NeighborNetSplitWeights.NNLSParams(taxaBlock.getNtax());

		params.tolerance =1e-6;
		if (getOptionInferenceAlgorithm()==InferenceAlgorithm.FastMethod) {
			params.greedy=true;
			params.nnlsAlgorithm= NeighborNetSplitWeights.NNLSParams.GRADPROJECTION;
			params.collapseMultiple = false;
			int n = cycle.length - 1; //ntax
			params.cgIterations = Math.min(Math.max(n,10),20);
		} else if (getOptionInferenceAlgorithm()==InferenceAlgorithm.CarefulMethod) {
			params.greedy = false;
			params.nnlsAlgorithm= NeighborNetSplitWeights.NNLSParams.GRADPROJECTION;
			params.collapseMultiple = false;
			int n = cycle.length - 1; //ntax
			params.outerIterations = n*(n-1)/2;
		} else if (getOptionInferenceAlgorithm()==InferenceAlgorithm.ProjectedGradient) {
			params.nnlsAlgorithm = NeighborNetSplitWeights.NNLSParams.PROJECTEDGRAD;
		} else if (getOptionInferenceAlgorithm()==InferenceAlgorithm.BlockPivot) {
				params.cgIterations = Math.max(cycle.length,10);
				params.nnlsAlgorithm = NeighborNetSplitWeights.NNLSParams.BLOCKPIVOT;
		} else {//ST4 version
			params.greedy = false;
			params.nnlsAlgorithm = NeighborNetSplitWeights.NNLSParams.ACTIVE_SET;
			int n = cycle.length - 1;
			params.outerIterations = n*(n-1)/2;
			params.collapseMultiple = true;
			params.fractionNegativeToKeep = 0.4;
			params.useInsertionAlgorithm = false;
		}

		splits = NeighborNetSplitWeights.compute(cycle, distancesBlock.getDistances(), params, progress);

		progress.setTasks("NNet", "post-analysis");

		if (Compatibility.isCompatible(splits))
			splitsBlock.setCompatibility(Compatibility.compatible);
		else
			splitsBlock.setCompatibility(Compatibility.cyclic);
		splitsBlock.setCycle(cycle);
		splitsBlock.setFit(SplitsUtilities.computeLeastSquaresFit(distancesBlock, splits));

		splitsBlock.getSplits().addAll(splits);

		if (!(progress instanceof ProgressSilent)) {
			var seconds = (System.currentTimeMillis() - start) / 1000.0;
			if (seconds > 10)
				System.err.printf("NNet time: %,.1fs%n", seconds);
		}
	}


	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, DistancesBlock parent) {
		return parent.getNtax() > 0;
	}

	public InferenceAlgorithm getOptionInferenceAlgorithm() {
		return optionInferenceAlgorithm.get();
	}

	public ObjectProperty<InferenceAlgorithm> optionInferenceAlgorithmProperty() {
		return optionInferenceAlgorithm;
	}

	public void setOptionInferenceAlgorithm(InferenceAlgorithm optionInferenceAlgorithm) {
		this.optionInferenceAlgorithm.set(optionInferenceAlgorithm);
	}
}
