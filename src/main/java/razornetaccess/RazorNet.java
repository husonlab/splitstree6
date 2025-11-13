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
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import razornet.razor_int.RunRazorNetIntGraph;
import razornet.utils.CanceledException;
import razornet.utils.Quantization;
import razornet.utils.TriConsumer;
import razornet.utils.TriangleInequalities;
import splitstree6.algorithms.distances.distances2network.CheckPairwiseDistances;
import splitstree6.algorithms.distances.distances2network.Distances2Network;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * implementation of the  Razor-net algorithm
 * Momomoko Hayamizu and Daniel Huson, 10.2025
 */
public class RazorNet extends Distances2Network {
	public enum Algorithm {Tighten1Polish1, CactusRealizer}

	private final ObjectProperty<Algorithm> optionAlgorithm = new SimpleObjectProperty<>(this, "optionAlgorithm", Algorithm.Tighten1Polish1);
	private final IntegerProperty optionSignificantDigits = new SimpleIntegerProperty(this, "optionSignificantDigits", 6);

	private final BooleanProperty optionPolish = new SimpleBooleanProperty(this, "optionPolish", true);
	private final BooleanProperty optionRemoveRedundant = new SimpleBooleanProperty(this, "optionRemoveRedundant", true);
	private final IntegerProperty optionMaxRounds = new SimpleIntegerProperty(this, "optionMaxRounds", 100);

	{
		//ProgramProperties.track(optionAlgorithm, Algorithm::valueOf, Tighten2Polish1);
		//ProgramProperties.track(optionPolish, true);
		//ProgramProperties.track(optionLocalPruning, true);
		//ProgramProperties.track(optionMaxRounds, 100);
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionAlgorithm.getName(), optionPolish.getName(), optionMaxRounds.getName(), optionRemoveRedundant.getName(), optionSignificantDigits.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionPolish.getName().equals(optionName))
			return "run polishing algorithm";
		else if (optionRemoveRedundant.getName().equals(optionName))
			return "remove superfluous edges";
		else if (optionMaxRounds.getName().equals(optionName))
			return "maximum number of rounds of polishing";
		else return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progressListener, TaxaBlock taxaBlock, DistancesBlock distancesBlock, NetworkBlock networkBlock) throws IOException {
		System.err.println("Running " + getOptionAlgorithm());
		progressListener.setTasks("RazorNet", "Initialization");
		var progress = new ProgressAdapter(progressListener);
		var distances = distancesBlock.getDistances();
		var n = distances.length;

		var quantization = Quantization.apply(distances, getOptionSignificantDigits());

		if (!TriangleInequalities.check(quantization.matrix(), false)) {
			System.err.println("Triangle Inequality check failed, fixing distances");
			TriangleInequalities.fix(quantization.matrix());
		}

		var graph = networkBlock.getGraph();
		var nodeMap = new HashMap<Integer, Node>();
		IntConsumer ensureNode = (id) -> {
			if (!nodeMap.containsKey(id)) {
				var v = graph.newNode();
				nodeMap.put(id, v);
				if (id < n) {
					var taxa = quantization.mapNodeBack().apply(id);
					if (taxa.size() == 1) {
						var t = taxa.iterator().next();
						graph.addTaxon(v, t);
						graph.setLabel(v, taxaBlock.getLabel(t));
					} else if (taxa.size() > 1) {
						for (var t : taxa) {
							var w = graph.newNode();
							graph.addTaxon(w, t);
							graph.setLabel(w, taxaBlock.getLabel(t));
							graph.setWeight(graph.newEdge(w, v), 0);
						}
					}
				}
			}
		};
		TriConsumer<Integer, Integer, Integer> newEdgeInteger = (u, v, w) -> {
			var e = graph.newEdge(nodeMap.get(u), nodeMap.get(v));
			graph.setWeight(e, quantization.mapDistanceBack().applyAsDouble(w));
		};
		TriConsumer<Integer, Integer, Double> newEdgeDouble = (u, v, w) -> {
			var e = graph.newEdge(nodeMap.get(u), nodeMap.get(v));
			graph.setWeight(e, w);
		};

		var verbose = true;

		try {
			RunRazorNetIntGraph.run(ensureNode, newEdgeInteger, quantization.matrix(), isOptionPolish(), getOptionRemoveRedundant(), getOptionMaxRounds(), verbose, progress, NotificationManager::showWarning);
		} catch (CanceledException ex) {
			System.err.println("RazorNet canceled");
			throw ex;
		}

		for (var e : graph.edges()) {
			networkBlock.getEdgeData(e).put("weight", StringUtils.removeTrailingZerosAfterDot(graph.getWeight(e)));
		}

		CheckPairwiseDistances.apply(graph, distancesBlock.getDistances(), 0.000001);

	}

	public boolean isOptionPolish() {
		return optionPolish.get();
	}

	public BooleanProperty optionPolishProperty() {
		return optionPolish;
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

	public int getOptionSignificantDigits() {
		return optionSignificantDigits.get();
	}

	public IntegerProperty optionSignificantDigitsProperty() {
		return optionSignificantDigits;
	}

	public boolean getOptionRemoveRedundant() {
		return optionRemoveRedundant.get();
	}

	public BooleanProperty optionRemoveRedundantProperty() {
		return optionRemoveRedundant;
	}
}
