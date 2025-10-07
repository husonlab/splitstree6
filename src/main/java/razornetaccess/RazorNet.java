/*
 * RazorNet.java Copyright (C) 2025 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.graph.Node;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import razornet.RazorNetAlgorithm;
import razornet.utils.Progress;
import splitstree6.algorithms.distances.distances2network.CheckPairwiseDistances;
import splitstree6.algorithms.distances.distances2network.Distances2Network;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/**
 * implementation of the  Razor-net algorithm
 * Momomoko Hayamizu and Daniel Huson, 10.2025
 */
public class RazorNet extends Distances2Network {
	private final BooleanProperty optionPolish = new SimpleBooleanProperty(this, "optionPolish", true);
	private final BooleanProperty optionLocalPruning = new SimpleBooleanProperty(this, "optionLocalPruning", false);
	private final IntegerProperty optionMaxRounds = new SimpleIntegerProperty(this, "optionMaxRounds", 100);

	@Override
	public List<String> listOptions() {
		return List.of(optionPolish.getName(), optionLocalPruning.getName(), optionMaxRounds.getName());
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
		var distances = distancesBlock.getDistances();
		var progress = new Progress() {
			@Override
			public void setProgress(long progress, long maximum) throws IOException {
				progressListener.setMaximum(maximum);
				progressListener.setProgress(progress);
			}
		};
		var computedEdges = RazorNetAlgorithm.run(distances, isOptionPolish(), isOptionLocalPruning(), getOptionMaxRounds(), progress);
		var computeNodes = new TreeSet<Integer>();
		for (var e : computedEdges) {
			computeNodes.add(e.i());
			computeNodes.add(e.j());
		}

		var graph = networkBlock.getGraph();

		var nodes = new HashMap<Integer, Node>();
		for (var i : computeNodes) {
			var v = nodes.computeIfAbsent(i, k -> graph.newNode());
			if (i < taxaBlock.getNtax()) {
				var t = (i + 1);
				graph.addTaxon(v, t);
				graph.setLabel(v, taxaBlock.getLabel(t));
			}
		}
		for (var computedEdge : computedEdges) {
			var u = nodes.get(computedEdge.i());
			var v = nodes.get(computedEdge.j());
			var e = graph.newEdge(u, v);
			graph.setWeight(e, computedEdge.weight());
			networkBlock.getEdgeData(e).put("weight", StringUtils.removeTrailingZerosAfterDot(graph.getWeight(e)));
		}

		CheckPairwiseDistances.apply(graph, distances, 0.0000001);
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
}
