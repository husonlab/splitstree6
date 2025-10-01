/*
 * NetworkPruning.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.fx.util.ProgramProperties;
import jloda.graph.Node;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class NetworkPruning extends Distances2Network {
	private final DoubleProperty optionEpsilon = new SimpleDoubleProperty(this, "optionEpsilon");
	private final DoubleProperty optionMinDistance = new SimpleDoubleProperty(this, "optionMinDistance");

	{
		ProgramProperties.track(optionEpsilon, 0.0000001);
		ProgramProperties.track(optionMinDistance, 0.001);

	}

	@Override
	public List<String> listOptions() {
		return List.of(optionEpsilon.getName(), optionMinDistance.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, NetworkBlock networkBlock) throws IOException {
		var ntax = taxaBlock.getNtax();
		var graph = networkBlock.getGraph();

		CactusRealizer.EPSILON = getOptionEpsilon();
		CactusRealizer.MIN_DISTANCE = getOptionMinDistance();


		var weightedGraph = CactusRealizer.run(distancesBlock.getDistances());


		var nodeMap = new HashMap<Integer, Node>();
		for (var directedEdge : weightedGraph.listEdges()) {
			var u = directedEdge.u();
			var uNode = nodeMap.get(u);
			if (uNode == null) {
				uNode = graph.newNode();
				nodeMap.put(u, uNode);
				if (u < ntax) {
					var t = u + 1;
					graph.addTaxon(uNode, t);
					graph.setLabel(uNode, taxaBlock.get(t).getDisplayLabelOrName());
				}
			}
			var v = directedEdge.v();
			var vNode = nodeMap.get(v);
			if (vNode == null) {
				vNode = graph.newNode();
				nodeMap.put(v, vNode);
				if (v < ntax) {
					var t = v + 1;
					graph.addTaxon(vNode, t);
					graph.setLabel(vNode, taxaBlock.get(t).getDisplayLabelOrName());
				}
			}
			var e = graph.newEdge(uNode, vNode);
			graph.setWeight(e, weightedGraph.getWeight(directedEdge));
		}

		var parent = distancesBlock.getNode().getPreferredParent();
		if (parent != null && parent.getPreferredParent() != null && parent.getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock) {
			graph.nodeStream().filter(v -> graph.getNumberOfTaxa(v) == 1).forEach(v -> {
				var row = graph.getTaxon(v) - 1;
				var sequence = String.valueOf(charactersBlock.getRow0(row));
				networkBlock.getNodeData(v).put(NetworkBlock.NODE_STATES_KEY, sequence);
			});

			for (var e : graph.edges()) {
				var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
				var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
				if (sequence1 != null && sequence2 != null) {
					networkBlock.getEdgeData(e).put(NetworkBlock.EDGE_SITES_KEY, computeEdgeLabel(sequence1, sequence2));
				}
			}
		}

		for (var e : graph.edges()) {
			networkBlock.getEdgeData(e).put("weight", StringUtils.removeTrailingZerosAfterDot(graph.getWeight(e)));
		}

		CheckPairwiseDistances.apply(graph, distancesBlock, getOptionEpsilon());
	}

	@Override
	public String getCitation() {
		return "Hayamisu et al 2025; M. Hayamisu et al, in preparation, 2025.";
	}

	public double getOptionEpsilon() {
		return optionEpsilon.get();
	}

	public DoubleProperty optionEpsilonProperty() {
		return optionEpsilon;
	}

	public double getOptionMinDistance() {
		return optionMinDistance.get();
	}

	public DoubleProperty optionMinDistanceProperty() {
		return optionMinDistance;
	}

	private static String computeEdgeLabel(String sequence1, String sequence2) {
		var buf = new StringBuilder();
		for (var i = 0; i < sequence1.length(); i++) {
			if (Character.toLowerCase(sequence1.charAt(i)) != Character.toLowerCase(sequence2.charAt(i))) {
				if (!buf.isEmpty())
					buf.append(",");
				buf.append(i + 1);
			}
		}
		return buf.toString();
	}
}
