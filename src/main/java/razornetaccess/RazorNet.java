/*
 * RazorNet_next.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package razornetaccess;

import javafx.beans.property.*;
import jloda.fx.util.ProgramProperties;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import razornet.cactusrealizer.RunCactusRealizer;
import razornet.razor_broken.RunRazorNet2Broken;
import razornet.razor_double.RunRazorNet1;
import razornet.razor_int.RunRazorNetInt;
import razornet.utils.Quantization;
import splitstree6.algorithms.distances.distances2network.CheckPairwiseDistances;
import splitstree6.algorithms.distances.distances2network.Distances2Network;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.List;

import static razornetaccess.RazorNet.Algorithm.Tighten2Polish1;

/**
 * implementation of the  Razor-net algorithm
 * Momomoko Hayamizu and Daniel Huson, 10.2025
 */
public class RazorNet extends Distances2Network {
	public enum Algorithm {Tighten1Polish1, Tighten2Polish1, Tighten2Polish2, Algorithm1Double, Algorithm2Broken, CactusRealizer}

	private final ObjectProperty<Algorithm> optionAlgorithm = new SimpleObjectProperty<>(this, "optionAlgorithm", Tighten2Polish1);
	private final BooleanProperty optionPolish = new SimpleBooleanProperty(this, "optionPolish", true);
	private final BooleanProperty optionLocalPruning = new SimpleBooleanProperty(this, "optionLocalPruning", true);
	private final IntegerProperty optionMaxRounds = new SimpleIntegerProperty(this, "optionMaxRounds", 100);

	{
		ProgramProperties.track(optionAlgorithm, Algorithm::valueOf, Tighten2Polish1);
		ProgramProperties.track(optionPolish, true);
		ProgramProperties.track(optionLocalPruning, true);
		ProgramProperties.track(optionMaxRounds, 100);
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionAlgorithm.getName(), optionPolish.getName(), optionLocalPruning.getName(), optionMaxRounds.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionPolish.getName().equals(optionName))
			return "perform polishing";
		else if (optionLocalPruning.getName().equals(optionName))
			return "perform local pruning";
		else if (optionMaxRounds.getName().equals(optionName))
			return "maximum number of rounds of polishing and/or pruning";
		else return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progressListener, TaxaBlock taxaBlock, DistancesBlock distancesBlock, NetworkBlock networkBlock) throws IOException {
		System.err.println("Running " + this.getClass().getSimpleName());
		var progress = new ProgressAdapter(progressListener);
		var distances = distancesBlock.getDistances();

		var quantization = Quantization.quantizeToEvenRazorMatrix(distances, 0.0000001, 0.0000001);

		var graphAdapter = new PhyloGraphAdapter(networkBlock.getGraph());

		switch (optionAlgorithm.get()) {
			case Algorithm1Double ->
					RunRazorNet1.run(graphAdapter, quantization.matrixAsDoubles(), isOptionPolish(), isOptionLocalPruning(), getOptionMaxRounds(), progress);
			case Tighten1Polish1 ->
					RunRazorNetInt.run(graphAdapter, quantization.matrix(), isOptionPolish(), isOptionLocalPruning(), getOptionMaxRounds(), 1, 1, progress);
			case Tighten2Polish1 ->
					RunRazorNetInt.run(graphAdapter, quantization.matrix(), isOptionPolish(), isOptionLocalPruning(), getOptionMaxRounds(), 2, 1, progress);
			case Tighten2Polish2 ->
					RunRazorNetInt.run(graphAdapter, quantization.matrix(), isOptionPolish(), isOptionLocalPruning(), getOptionMaxRounds(), 2, 2, progress);
			case Algorithm2Broken ->
					RunRazorNet2Broken.run(graphAdapter, quantization.matrix(), isOptionPolish(), isOptionLocalPruning(), getOptionMaxRounds(), progress);
			case CactusRealizer ->
					RunCactusRealizer.run(graphAdapter, quantization.matrixAsDoubles(), isOptionPolish(), isOptionLocalPruning(), progress);
		}

		if (true) {
			// update edge weights
			for (var e : graphAdapter.edges()) {
				var weight = quantization.mapDistanceBack().applyAsDouble((int) graphAdapter.getWeight(e));
				graphAdapter.setWeight(e, weight);
			}
			// uncollapse identical taxa
			for (var v0 : graphAdapter.nodes()) {
				var rep1 = graphAdapter.getTaxon(v0);
				if (rep1 != -1) {
					var rep0 = rep1 - 1;
					var taxa = quantization.mapNodeBack().apply(rep0);
					var v = v0;
					var first = true;
					for (var t0 : taxa) {
						var t1 = t0 + 1;
						if (first)
							first = false;
						else {
							v = graphAdapter.newNode();
							var e = graphAdapter.newEdge(v0, v);
							graphAdapter.setWeight(e, 0);
						}
						graphAdapter.setTaxon(v, t1);
						graphAdapter.setLabel(v, taxaBlock.getLabel(t1));
					}
				}
			}

		}

		for (var e : graphAdapter.edges()) {
			networkBlock.getEdgeData(e).put("weight", StringUtils.removeTrailingZerosAfterDot(graphAdapter.getWeight(e)));
		}

		CheckPairwiseDistances.apply(graphAdapter.getGraph(), distancesBlock.getDistances(), 0.000001);

	}

	public boolean isOptionPolish() {
		return optionPolish.get();
	}

	public BooleanProperty optionPolishProperty() {
		return optionPolish;
	}

	public boolean isOptionLocalPruning() {
		return optionLocalPruning.get();
	}

	public BooleanProperty optionLocalPruningProperty() {
		return optionLocalPruning;
	}

	public int getOptionMaxRounds() {
		return optionMaxRounds.get();
	}

	public IntegerProperty optionMaxRoundsProperty() {
		return optionMaxRounds;
	}

	public Algorithm getOptionAlgorithm() {
		return optionAlgorithm.get();
	}

	public ObjectProperty<Algorithm> optionAlgorithmProperty() {
		return optionAlgorithm;
	}
}
