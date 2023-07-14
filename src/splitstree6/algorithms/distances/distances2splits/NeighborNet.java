/*
 * NeighborNet.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.*;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle2023;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycleSplitsTree4;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeightOptimizerSplitsTree4;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeightsClean;
import splitstree6.algorithms.splits.IToCircularSplits;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NeighborNet extends Distances2Splits implements IToCircularSplits {

	public enum InferenceAlgorithm {GradientProjection, ActiveSet, APGD, SplitsTree4}

	public enum CircularOrderingAlgorithm {SplitsTree4, BryantHuson2023}
	private final ObjectProperty<InferenceAlgorithm> optionInferenceAlgorithm = new SimpleObjectProperty<>(this, "optionInferenceAlgorithm", InferenceAlgorithm.ActiveSet);
	private final DoubleProperty optionThreshold = new SimpleDoubleProperty(this, "optionThreshold", 1e-8);
	private final ObjectProperty<CircularOrderingAlgorithm> optionCircularOrdering = new SimpleObjectProperty<>(this, "optionCircularOrdering", CircularOrderingAlgorithm.SplitsTree4);

	private final BooleanProperty optionActiveCleanup = new SimpleBooleanProperty(this, "optionActiveCleanup", false);

	public List<String> listOptions() {
		return List.of(optionInferenceAlgorithm.getName()/*, optionThreshold.getName()*/ /*, optionCircularOrdering.getName()*/ /*, optionActiveCleanup.getName()*/);
	}

	@Override
	public String getCitation() {
		return "Bryant & Moulton 2004; " +
			   "D. Bryant and V. Moulton. Neighbor-net: An agglomerative method for the construction of phylogenetic networks. " +
			   "Molecular Biology and Evolution, 21(2):255– 265, 2004.;" +
			   "Bryant & Huson 2023;D. Bryant and D.H. Huson, NeighborNet- algorithms and implementation, in preparation.";
	}

	/**
	 * run the neighbor net algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock splitsBlock) throws IOException {

		progress.setMaximum(-1);
		long start = System.currentTimeMillis();

		var cycle = switch (getOptionCircularOrdering()) {
			case SplitsTree4 -> NeighborNetCycleSplitsTree4.compute(distancesBlock.size(), distancesBlock.getDistances());
			case BryantHuson2023 -> NeighborNetCycle2023.computeOrdering(distancesBlock);
		};

		progress.setTasks("NNet", "split weight optimization");

		var params = new NeighborNetSplitWeightsClean.NNLSParams();
		params.activeCleanup = getOptionActiveCleanup();

		if (getOptionInferenceAlgorithm() == InferenceAlgorithm.ActiveSet)
			params.method = NeighborNetSplitWeightsClean.NNLSParams.MethodTypes.ACTIVESET;
		else if (getOptionInferenceAlgorithm() == InferenceAlgorithm.APGD)
			params.method = NeighborNetSplitWeightsClean.NNLSParams.MethodTypes.APGD;
		//else if (getOptionInferenceAlgorithm() == InferenceAlgorithm.IPG)
		//	params.method = NeighborNetSplitWeightsClean.NNLSParams.MethodTypes.IPG;
		else if (getOptionInferenceAlgorithm() == InferenceAlgorithm.GradientProjection)
			params.method = NeighborNetSplitWeightsClean.NNLSParams.MethodTypes.GRADPROJECTION;
		else
			params.method = NeighborNetSplitWeightsClean.NNLSParams.MethodTypes.SPLITSTREE4;

		ArrayList<ASplit> splits;
		if (getOptionInferenceAlgorithm() != InferenceAlgorithm.SplitsTree4) {
			//splits = NeighborNetSplitWeightsClean.compute(cycle, distancesBlock.getDistances(), params, progress);
			splits = NeighborNetSplitWeightsClean.computeUse1D(cycle, distancesBlock.getDistances(), params, progress);
//			System.err.println("OLD");
//			for(int i=0;i<splits.size();i++)
//				System.err.println(splits.get(i));
//
//			System.err.println("NEW");
//			for(int i=0;i<splits2.size();i++)
//				System.err.println(splits2.get(i));
		}
		else
			splits = NeighborNetSplitWeightOptimizerSplitsTree4.apply(cycle, distancesBlock);

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
			System.err.printf("NNet algorithm: %s taxa: %d time: %,.1fs fit: %.1f%% splits: %d%n", getOptionInferenceAlgorithm().name(),
					taxaBlock.getNtax(), seconds, splitsBlock.getFit(), splitsBlock.getNsplits());
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

	public double getOptionThreshold() {
		return optionThreshold.get();
	}

	public DoubleProperty optionThresholdProperty() {
		return optionThreshold;
	}

	public void setOptionThreshold(double threshold) {
		this.optionThreshold.set(threshold);
	}

	public boolean getOptionActiveCleanup() {
		return optionActiveCleanup.get();
	}

	public BooleanProperty optionActiveCleanupProperty() {
		return optionActiveCleanup;
	}

	public void isOptionActiveCleanup(boolean active) {
		this.optionActiveCleanup.set(active);
	}

	public CircularOrderingAlgorithm getOptionCircularOrdering() {
		return optionCircularOrdering.get();
	}

	public ObjectProperty<CircularOrderingAlgorithm> optionCircularOrderingProperty() {
		return optionCircularOrdering;
	}

	public void setOptionCircularOrdering(CircularOrderingAlgorithm optionCircularOrdering) {
		this.optionCircularOrdering.set(optionCircularOrdering);
	}
}
