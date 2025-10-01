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

package splitstree6.algorithms.distances.distances2network;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.fx.util.ProgramProperties;
import jloda.graph.Node;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.distances.distances2network.razor1.*;
import splitstree6.algorithms.distances.distances2network.razor2.RazorDistances;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import static splitstree6.algorithms.distances.distances2network.razor1.MatrixUtils.verifyTriangleInequalities;
import static splitstree6.algorithms.distances.distances2network.razor2.MatrixUtils.verifyIntegerTriangleInequalities;

/**
 * implementation of the first Razor-net algorithm
 * Momomoko Hayamizu and Daniel Huson, 9.2025
 */
public class RazorNet extends Distances2Network {
	private final DoubleProperty optionEpsilon = new SimpleDoubleProperty(this, "optionEpsilon");
	private final DoubleProperty optionMinDistance = new SimpleDoubleProperty(this, "optionMinDistance");
	private final BooleanProperty optionPolish = new SimpleBooleanProperty(this, "optionPolish", true);
	private final BooleanProperty optionLocalPruning = new SimpleBooleanProperty(this, "optionLocalPruning", true);
	private final BooleanProperty optionAlgorithm2 = new SimpleBooleanProperty(this, "optionAlgorithm2", false);

	private final DoubleProperty optionMinEdgeLength = new SimpleDoubleProperty(this, "optionMinEdgeLength", 0.0);

	{
		ProgramProperties.track(optionEpsilon, 0.0000001);
		ProgramProperties.track(optionMinDistance, 0.0000001);
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionPolish.getName(), optionLocalPruning.getName(), optionMinEdgeLength.getName(), optionEpsilon.getName(), optionMinDistance.getName(), optionAlgorithm2.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, NetworkBlock networkBlock) throws IOException {
		RazorExpand.EPS = getOptionEpsilon();

		var ntax = taxaBlock.getNtax();
		progress.setSubtask("expanding");
		var distances = distancesBlock.getDistances();

		var triangleInequalitiesHold = verifyTriangleInequalities(distances, getOptionEpsilon());
		System.err.println("Triangle inequalities hold: " + triangleInequalitiesHold);
		if (!triangleInequalitiesHold) {
			distances = MetricFix.enforceTriangleInequalities(distances);
		}


		var graph = networkBlock.getGraph();

		if (isOptionAlgorithm2()) {
			var conversion = RazorDistances.convertToEvenIntegerDistances(distances);

			var triangleInequalities = verifyIntegerTriangleInequalities(conversion.matrix());
			if (!triangleInequalities)
				throw new IOException("Integer triangle inequalities do not hold");

			var nodes = new HashMap<Integer, Node>();
			for (var i = 0; i < taxaBlock.getNtax(); i++) {
				var v = nodes.computeIfAbsent(i, k -> graph.newNode());
				if (i < taxaBlock.getNtax()) {
					var t = (i + 1);
					graph.addTaxon(v, t);
					graph.setLabel(v, taxaBlock.get(t).getDisplayLabelOrName());
				}
			}

			var outputEdges = splitstree6.algorithms.distances.distances2network.razor2.RazorExpand.expand(conversion.matrix());
			for (var outputEdge : outputEdges) {
				var u = nodes.computeIfAbsent(outputEdge.i(), k -> graph.newNode());
				var v = nodes.computeIfAbsent(outputEdge.j(), k -> graph.newNode());
				var weight = conversion.mapBack().applyAsDouble(outputEdge.w());
				var e = graph.newEdge(u, v);
				graph.setWeight(e, weight);
			}

			CheckPairwiseDistances.apply(graph, distances, getOptionEpsilon());

		} else {
			var D = RazorExpand.expand(distances, progress);
			System.err.printf("RazorNet expanded: %d -> %d%n", ntax, D.length);

			var max = 5;

			for (var i = 0; i < max; i++) {
				var originalSize = D.length;
				if (isOptionPolish()) {
					var rounds = 0;
					while (true) {
						progress.setSubtask("polishing");
						var startSize = D.length;
						D = RazorNetPolish.polishUsingUG(D, progress);
						if (D.length == startSize) break;
						D = MatrixCleaner.cleanAndSmooth(D, t -> t < ntax).D();
						System.err.printf("RazorNet polished: %d -> %d%n", startSize, D.length);
						if (++rounds == max || D.length == startSize) break;
					}
				}
				if (getOptionLocalPruning()) {
					var startSize = D.length;
					D = RazorLocalPruner.prune(D, t -> t < ntax, progress);
					if (D.length == startSize) break;
					System.err.printf("RazorNet pruned: %d -> %d%n", startSize, D.length);
				}
				if (D.length == originalSize)
					break;
			}


			var nodes = new HashMap<Integer, Node>();
			for (var i = 0; i < taxaBlock.getNtax(); i++) {
				var v = nodes.computeIfAbsent(i, k -> graph.newNode());
				if (i < taxaBlock.getNtax()) {
					var t = (i + 1);
					graph.addTaxon(v, t);
					graph.setLabel(v, taxaBlock.get(t).getDisplayLabelOrName());
				}
			}
			for (var i = 0; i < D.length; i++) {
				var v = nodes.computeIfAbsent(i, k -> graph.newNode());
				var row = D[i];
				for (var j = i + 1; j < D.length; j++) {
					var w = nodes.computeIfAbsent(j, k -> graph.newNode());
					var dij = row[j];
					if (RazorMath.isEssentialEdge(D, i, j, getOptionMinDistance(), true)) {
						var e = graph.newEdge(v, w);
						graph.setWeight(e, dij);
					}
				}
			}
			CheckPairwiseDistances.apply(graph, distances, getOptionEpsilon());
		}

		if (getOptionMinEdgeLength() > 0) {
			while (true) {
				var optionalEdge = graph.edgeStream().filter(f -> graph.getWeight(f) < getOptionMinEdgeLength() && (f.getSource().getDegree() > 1 || f.getTarget().getDegree() > 1) && (!graph.hasTaxa(f.getSource()) || !graph.hasTaxa(f.getTarget()))).findAny();
				if (optionalEdge.isPresent()) {
					var e = optionalEdge.get();
					var s = e.getSource();
					var t = e.getTarget();
					var taxon = -1;
					var keep = s;
					var other = t;
					if (graph.hasTaxa(s)) {
						taxon = graph.getTaxon(s);
					} else if (graph.hasTaxa(t)) {
						taxon = graph.getTaxon(t);
						keep = t;
						other = s;
					}
					for (var f : other.adjacentEdges()) {
						if (f != e) {
							var v = f.getOpposite(other);
							var h = keep.getCommonEdge(v);
							if (h == null) {
								var g = graph.newEdge(keep, v);
								graph.setWeight(g, graph.getWeight(f));
							} else {
								graph.setWeight(h, 0.5 * (graph.getWeight(h) + graph.getWeight(h)));
							}
						}
					}
					graph.deleteNode(other);
				} else break;
			}
		}

		for (var e : graph.edges()) {
			networkBlock.getEdgeData(e).put("weight", StringUtils.removeTrailingZerosAfterDot(graph.getWeight(e)));
		}
	}


	public double getOptionMinDistance() {
		return optionMinDistance.get();
	}

	public DoubleProperty optionMinDistanceProperty() {
		return optionMinDistance;
	}

	public double getOptionEpsilon() {
		return optionEpsilon.get();
	}

	public DoubleProperty optionEpsilonProperty() {
		return optionEpsilon;
	}

	public boolean isOptionPolish() {
		return optionPolish.get();
	}

	public BooleanProperty optionPolishProperty() {
		return optionPolish;
	}

	public boolean getOptionLocalPruning() {
		return optionLocalPruning.get();
	}

	public BooleanProperty optionLocalPruningProperty() {
		return optionLocalPruning;
	}

	public boolean isOptionAlgorithm2() {
		return optionAlgorithm2.get();
	}

	public BooleanProperty optionAlgorithm2Property() {
		return optionAlgorithm2;
	}

	public double getOptionMinEdgeLength() {
		return optionMinEdgeLength.get();
	}

	public DoubleProperty optionMinEdgeLengthProperty() {
		return optionMinEdgeLength;
	}
}
