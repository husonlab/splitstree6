/*
 * RazorHaplotypeNetwork.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2network;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.SetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.nucleotide.TN93Distance;
import splitstree6.algorithms.distances.distances2network.RazorNet;
import splitstree6.algorithms.distances.distances2network.razor1.InterfaceUtils;
import splitstree6.algorithms.distances.distances2network.razor1.ParsimonyLabeler;
import splitstree6.algorithms.distances.distances2network.razor1.SuperfluousEdge;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.AmbiguityCodes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * computes a haplotype network using the RazorNet method
 * Daniel Huson, 9.2025
 */
public class RazorHaplotypeNetwork extends Characters2Network {
	public enum AmbiguousOptions {Wildcard, State}

	public enum DistanceMethods {Hamming, TN93}

	private final ObjectProperty<AmbiguousOptions> optionAmbiguityHandling = new SimpleObjectProperty<>(this, "optionAmbiguityHandling", AmbiguousOptions.Wildcard);
	private final ObjectProperty<DistanceMethods> optionDistanceMethod = new SimpleObjectProperty<>(this, "optionDistanceMethod", DistanceMethods.Hamming);

	private final BooleanProperty optionContractEdges = new SimpleBooleanProperty(this, "optionContractEdges", false);
	private final BooleanProperty optionRemoveEdges = new SimpleBooleanProperty(this, "optionRemoveEdges", false);

	private final BooleanProperty optionPolish = new SimpleBooleanProperty(this, "optionPolish", true);
	private final BooleanProperty optionLocalPruning = new SimpleBooleanProperty(this, "optionLocalPruning", true);
	private final BooleanProperty optionAlgorithm2 = new SimpleBooleanProperty(this, "optionAlgorithm2", false);

	@Override
	public List<String> listOptions() {
		return List.of(optionDistanceMethod.getName(), optionContractEdges.getName(), optionRemoveEdges.getName(), optionPolish.getName(), optionLocalPruning.getName(), optionAlgorithm2.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, NetworkBlock networkBlock) throws IOException {
		var hammingOptions = new ComparisonOptions(charactersBlock.getGapCharacter(), charactersBlock.getMissingCharacter(), (getOptionAmbiguityHandling() == AmbiguousOptions.Wildcard && charactersBlock.isHasAmbiguityCodes()), getOptionAmbiguityHandling());

		var distancesBlock = new DistancesBlock();
		distancesBlock.setNtax(taxaBlock.getNtax());

		if (getOptionDistanceMethod().equals(DistanceMethods.TN93) && charactersBlock.getDataType().isNucleotides()) {
			System.err.println("Using TN93 distances");
			var tn93 = new TN93Distance();
			tn93.compute(progress, taxaBlock, charactersBlock, distancesBlock);
		} else {
			System.err.println("Using Hamming distances");
			computeHammingDistances(charactersBlock, distancesBlock.getDistances(), hammingOptions);
		}

		var razorNet = new RazorNet();
		razorNet.optionEpsilonProperty().set(0.00001);
		razorNet.optionMinDistanceProperty().set(0.00001);
		razorNet.optionPolishProperty().set(isOptionPolish());
		razorNet.optionLocalPruningProperty().set(isOptionLocalPruning());
		razorNet.optionAlgorithm2Property().set(isOptionAlgorithm2());

		razorNet.compute(progress, taxaBlock, distancesBlock, networkBlock);

		var graph = networkBlock.getGraph();

		progress.setSubtask("parsimony labeling");

		var parsimonyLabeler = new ParsimonyLabeler<>(InterfaceUtils.getGraphAdapter(graph));
		var inputSequences = new HashMap<Node, String>();
		for (var v : graph.nodes()) {
			if (graph.hasTaxa(v)) {
				var row = graph.getTaxon(v) - 1;
				var sequence = String.valueOf(charactersBlock.getRow0(row));
				inputSequences.put(v, sequence);
			}
		}
		var outputSequences = parsimonyLabeler.labelAllSites(inputSequences);
		for (var entry : outputSequences.entrySet()) {
			networkBlock.getNodeData(entry.getKey()).put(NetworkBlock.NODE_STATES_KEY, entry.getValue());
		}

		if (isOptionContractEdges()) {
			progress.setSubtask("contracting empty edges");

			var edgesContracted = 0;
			for (var i = 0; i < 100; i++) {
				var contracted = false;
				for (var e : graph.edges()) {
					if (isPartOfTriangle(e))
						continue;
					var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
					var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);

					if (sequence1 != null && sequence2 != null) {
						if ((!graph.hasTaxa(e.getSource()) || !graph.hasTaxa(e.getTarget())) && compareSequences(sequence1, sequence2, hammingOptions) == 0) {
							var keep = (graph.hasTaxa(e.getSource()) ? e.getSource() : e.getTarget());
							var other = e.getOpposite(keep);
							for (var f : other.adjacentEdges()) {
								if (f != e) {
									var u = f.getOpposite(other);
									var g = graph.newEdge(u, keep);
									graph.setWeight(g, graph.getWeight(e));
								}
							}

							networkBlock.removeNodeData(other);
							graph.deleteNode(other);

							edgesContracted++;
							contracted = true;
							break;
						}
					}
				}
				if (!contracted)
					break;
			}
			System.err.println("Empty edges contracted: " + edgesContracted);
		}

		if (isOptionRemoveEdges()) {
			progress.setSubtask("removing superfluous edges");

			var changes = new HashMap<Edge, Integer>();
			for (var h : graph.edges()) {
				var v = h.getSource();
				var w = h.getTarget();
				var a = networkBlock.getNodeData(v).get(NetworkBlock.NODE_STATES_KEY);
				var b = networkBlock.getNodeData(w).get(NetworkBlock.NODE_STATES_KEY);
				var diff = compareSequences(a, b, hammingOptions);
				changes.put(h, diff);
			}
			var edgesDeleted = 0;
			var edges = graph.getEdgesAsList();
			edges.sort((a, b) -> -Double.compare(graph.getWeight(a), graph.getWeight(b)));
			for (var f : edges) {
				if (SuperfluousEdge.isSuperfluous(f.getSource(), f.getTarget(), f, changes::get)) {
					graph.deleteEdge(f);
					edgesDeleted++;
				}
			}
			System.err.println("Superfluous edges removed: " + edgesDeleted);
		}

		for (var e : graph.edges()) {
			var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
			var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
			if (sequence1 != null && sequence2 != null) {
				networkBlock.getEdgeData(e).put(NetworkBlock.EDGE_SITES_KEY, computeEdgeLabel(sequence1, sequence2, hammingOptions));
			}
		}
	}

	private boolean isPartOfTriangle(Edge e) {
		var a = e.getSource().adjacentNodeStream(false).filter(v -> v != e.getTarget()).collect(Collectors.toSet());
		var b = e.getTarget().adjacentNodeStream(false).filter(v -> v != e.getSource()).collect(Collectors.toSet());
		return SetUtils.intersect(a, b);
	}

	private static void computeHammingDistances(CharactersBlock charactersBlock, double[][] distances, ComparisonOptions comparisonOptions) {
		var n = charactersBlock.getNtax();

		for (var i = 0; i < n; i++) {
			var a = String.valueOf(charactersBlock.getRow0(i));
			for (var j = i + 1; j < n; j++) {
				var b = String.valueOf(charactersBlock.getRow0(j));
				distances[i][j] = distances[j][i] = compareSequences(a, b, comparisonOptions);
			}
		}
	}

	private static int compareSequences(String a, String b, ComparisonOptions comparisonOptions) {
		var m = a.length();
		var count = 0;

		for (var pos = 0; pos < m; pos++) {
			var ci = Character.toLowerCase(a.charAt(pos));
			var cj = Character.toLowerCase(b.charAt(pos));
			if (differ(ci, cj, comparisonOptions)) {
				count++;
			}
		}
		return count;
	}

	private static boolean differ(char ci, char cj, ComparisonOptions comparisonOptions) {
		if (comparisonOptions.ambiguousOptions == AmbiguousOptions.State) {
			return ci != cj;
		} else if (ci != comparisonOptions.missing && ci != comparisonOptions.gap && cj != comparisonOptions.missing && cj != comparisonOptions.gap) {
			if (comparisonOptions.useAmbiguityCodes) {
				return !AmbiguityCodes.codesOverlap(ci, cj);
			} else {
				return ci != cj;
			}
		} else
			return false;
	}

	private static String computeEdgeLabel(String sequence1, String sequence2, ComparisonOptions comparisonOptions) {
		var buf = new StringBuilder();
		for (var i = 0; i < sequence1.length(); i++) {
			if (differ(Character.toLowerCase(sequence1.charAt(i)), Character.toLowerCase(sequence2.charAt(i)), comparisonOptions)) {
				if (!buf.isEmpty())
					buf.append(",");
				buf.append(i + 1);
			}
		}
		return buf.toString();
	}


	private record ComparisonOptions(char gap, char missing, boolean useAmbiguityCodes,
									 AmbiguousOptions ambiguousOptions) {
	}

	public boolean isOptionContractEdges() {
		return optionContractEdges.get();
	}

	public BooleanProperty optionContractEdgesProperty() {
		return optionContractEdges;
	}

	public boolean isOptionRemoveEdges() {
		return optionRemoveEdges.get();
	}

	public BooleanProperty optionRemoveEdgesProperty() {
		return optionRemoveEdges;
	}

	public AmbiguousOptions getOptionAmbiguityHandling() {
		return optionAmbiguityHandling.get();
	}

	public ObjectProperty<AmbiguousOptions> optionAmbiguityHandlingProperty() {
		return optionAmbiguityHandling;
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

	public DistanceMethods getOptionDistanceMethod() {
		return optionDistanceMethod.get();
	}

	public ObjectProperty<DistanceMethods> optionDistanceMethodProperty() {
		return optionDistanceMethod;
	}

	public boolean isOptionAlgorithm2() {
		return optionAlgorithm2.get();
	}

	public BooleanProperty optionAlgorithm2Property() {
		return optionAlgorithm2;
	}
}
