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
	public enum InferenceAlgorithm {FastMethod,CarefulMethod,SplitsTree4Method}  //TODO: ActiveSet not working at present. Will be rewritten.

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
			params.nnlsAlgorithm= NeighborNetSplitWeights.NNLSParams.PROJ_GRAD;
			params.collapseMultiple = false;
		} else if (getOptionInferenceAlgorithm()==InferenceAlgorithm.CarefulMethod) {
			params.greedy = false;
			params.nnlsAlgorithm= NeighborNetSplitWeights.NNLSParams.PROJ_GRAD;
			params.collapseMultiple = false;
			int n = cycle.length - 1; //ntax
			params.outerIterations = n*(n-1)/2;
		} else {
			params.greedy = false;
			params.nnlsAlgorithm = NeighborNetSplitWeights.NNLSParams.ACTIVE_SET;
			int n = cycle.length - 1;
			params.outerIterations = n*(n-1)/2;
			params.collapseMultiple = true;
			params.fractionNegativeToKeep = 0.4;
		}

			splits = NeighborNetSplitWeights.compute(cycle, distancesBlock.getDistances(), params, progress);

		progress.setTasks("NNet", "post-analysis");


		// add all missing trivial
		//TODO: Daniel, should we just return all of these from NeighborNetSplitWeights.compute?
		splits.addAll(SplitsUtilities.createAllMissingTrivial(splits, taxaBlock.getNtax()));

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
				System.err.printf("Time: %,.1fs%n", seconds);
		}
	}

	/**
	 * Compute the average distance in the distances block, used to pick an appropriately scaled tolerance level.
	 * @param dist  distances block
	 * @return  average distance between taxa
	 */
	private double averageDistance(DistancesBlock dist) {
		double total = 0.0;
		for(int i=1;i<=dist.getNtax();i++) {
			double rowSum = 0.0;

			for (int j = 1; j < i; j++)
				rowSum += dist.get(i, j);
			total += rowSum;
		}
		return total / (dist.getNtax()*(dist.getNtax()-1)/2.0);
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
