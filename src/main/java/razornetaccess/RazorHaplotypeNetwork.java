package razornetaccess;

import javafx.beans.property.*;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.nucleotide.TN93Distance;
import splitstree6.algorithms.characters.characters2network.Characters2Network;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.AmbiguityCodes;

import java.io.IOException;
import java.util.*;
import java.util.function.ToIntFunction;

/**
 * computes a haplotype network using the RazorNet_old method
 * Daniel Huson, 10.2025
 */
public class RazorHaplotypeNetwork extends Characters2Network {
	public enum Algorithm {Tighten1Polish1, Tighten2Polish1, Tighten2Polish2, Algorithm1Double, Algorithm2Broken, CactusRealizer}
	public enum AmbiguousOptions {Wildcard, State}

	public enum DistanceMethods {Hamming, TN93}

	private final ObjectProperty<AmbiguousOptions> optionAmbiguityHandling = new SimpleObjectProperty<>(this, "optionAmbiguityHandling", AmbiguousOptions.Wildcard);
	private final ObjectProperty<DistanceMethods> optionDistanceMethod = new SimpleObjectProperty<>(this, "optionDistanceMethod", DistanceMethods.Hamming);

	private final BooleanProperty optionContractEdges = new SimpleBooleanProperty(this, "optionContractEdges", false);
	private final BooleanProperty optionRemoveEdges = new SimpleBooleanProperty(this, "optionRemoveEdges", false);

	private final ObjectProperty<Algorithm> optionAlgorithm = new SimpleObjectProperty<>(this, "optionAlgorithm", Algorithm.Tighten2Polish1);
	private final IntegerProperty optionSignificantDigits = new SimpleIntegerProperty(this, "optionSignificantDigits", 6);

	private final BooleanProperty optionPolish = new SimpleBooleanProperty(this, "optionPolish", true);
	private final BooleanProperty optionLocalPruning = new SimpleBooleanProperty(this, "optionLocalPruning", true);

	private final IntegerProperty optionMaxRounds = new SimpleIntegerProperty(this, "optionMaxRounds", 100);

	private final RazorNet razorNet;

	public RazorHaplotypeNetwork() {
		// get defaults from RazorNet_next:
		this.razorNet = new RazorNet();
		try {
			optionPolish.set(razorNet.isOptionPolish());
			optionLocalPruning.set(razorNet.isOptionLocalPruning());
			optionMaxRounds.set(razorNet.getOptionMaxRounds());
			optionAlgorithm.set(Algorithm.valueOf(razorNet.getOptionAlgorithm().name()));
			optionSignificantDigits.set(razorNet.getOptionSignificantDigits());
		} catch (Exception ignored) {
		}
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionDistanceMethod.getName(), optionRemoveEdges.getName(), optionContractEdges.getName(), optionAlgorithm.getName(), optionSignificantDigits.getName(), optionPolish.getName(), optionLocalPruning.getName(), optionMaxRounds.getName());
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

		try {
			razorNet.optionPolishProperty().set(isOptionPolish());
			razorNet.optionLocalPruningProperty().set(isOptionLocalPruning());
			razorNet.optionMaxRoundsProperty().set(getOptionMaxRounds());
			razorNet.optionSignificantDigitsProperty().set(getOptionSignificantDigits());
			razorNet.optionAlgorithmProperty().set(RazorNet.Algorithm.valueOf(razorNet.getOptionAlgorithm().name()));
		} catch (Exception ignored) {
		}

		razorNet.compute(progress, taxaBlock, distancesBlock, networkBlock);

		var graph = networkBlock.getGraph();

		progress.setSubtask("parsimony labeling");

		var parsimonyLabeler = new ParsimonyLabeler<>(graph.nodes(), graph.edges(), Edge::getSource, Edge::getTarget);
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
				if (isSuperfluous(f.getSource(), f.getTarget(), f, changes::get)) {
					graph.deleteEdge(f);
					edgesDeleted++;
				}
			}

			while (true) {
				var changed = false;
				var vOptional = graph.nodeStream().filter(u -> u.getDegree() == 2 && !graph.hasTaxa(u)).findAny();
				if (vOptional.isPresent()) {
					var v = vOptional.get();
					var e1 = v.getFirstAdjacentEdge();
					var e2 = v.getLastAdjacentEdge();
					var a = e1.getOpposite(v);
					var b = e2.getOpposite(v);
					var ab = a.getCommonEdge(b);
					if (ab == null) {
						var e = graph.newEdge(a, b);
						graph.setWeight(e, graph.getWeight(e1) + graph.getWeight(e2));
					} else {
						var w = 0.5 * (graph.getWeight(ab) + graph.getWeight(e1) + graph.getWeight(e2));
						graph.setWeight(ab, w);
					}
					graph.deleteNode(v);
					changed = true;
				}
				if (true) {
					var wOptional = graph.nodeStream().filter(u -> u.getDegree() == 1 && !graph.hasTaxa(u)).findAny();
					if (wOptional.isPresent()) {
						var w = wOptional.get();
						graph.deleteNode(w);
						changed = true;
					}
				}
				if (!changed)
					break;
			}
			System.err.println("Superfluous edges removed: " + edgesDeleted);
		}

		if (isOptionContractEdges()) {
			progress.setSubtask("contracting empty edges");

			var edgesContracted = 0;
			for (var i = 0; i < 10000; i++) {
				var contracted = false;
				for (var e : graph.edges()) {
					var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
					var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);

					if ((!graph.hasTaxa(e.getSource()) || !graph.hasTaxa(e.getTarget())) && (compareSequences(sequence1, sequence2, hammingOptions) == 0)) {
							var keep = (graph.hasTaxa(e.getSource()) ? e.getSource() : e.getTarget());
							var other = e.getOpposite(keep);
							for (var f : other.adjacentEdges()) {
								if (f != e) {
									var u = f.getOpposite(other);
									var g = u.getCommonEdge(keep);
									if (g != null) {
										var w = 0.5 * (graph.getWeight(f) + graph.getWeight(g));
										graph.setWeight(g, w);
									} else {
										var h = graph.newEdge(u, keep);
										graph.setWeight(h, graph.getWeight(e));
									}
								}
							}
							networkBlock.removeNodeData(other);
							graph.deleteNode(other);

							edgesContracted++;
							contracted = true;
							break;
						}
				}
				if (!contracted)
					break;
			}
			System.err.println("Empty edges contracted: " + edgesContracted);
		}

		for (var e : graph.edges()) {
			var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
			var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
			if (sequence1 != null && sequence2 != null) {
				networkBlock.getEdgeData(e).put(NetworkBlock.EDGE_SITES_KEY, computeEdgeLabel(sequence1, sequence2, hammingOptions));
			}
		}
		networkBlock.setNetworkType(NetworkBlock.Type.HaplotypeNetwork);
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

	/**
	 * Is e=(s,t) superfluous? i.e. is there an s->t path not using e with total weight == weight(e)?
	 * Runs a bounded Dijkstra that:
	 * - never traverses e
	 * - never explores paths with distance > w(e)
	 * - stops immediately if t is popped with distance == w(e)
	 */
	public static boolean isSuperfluous(Node s, Node t, Edge e, ToIntFunction<Edge> getWeight) {
		final var W = getWeight.applyAsInt(e);
		// Trivial quick reject: if s or t aren't endpoints, or W < 0 (shouldn't happen)
		if (!e.nodes().containsAll(List.of(s, t)) || W < 0)
			throw new IllegalArgumentException("Edge e must be exactly (s,t) and have non-negative weight");

		// Dijkstra (bounded by W)
		Map<Node, Integer> dist = new HashMap<>();

		record NodeDist(Node n, int d) {
		}

		PriorityQueue<NodeDist> pq = new PriorityQueue<>(Comparator.comparingInt(nd -> nd.d));
		dist.put(s, 0);
		pq.add(new NodeDist(s, 0));

		while (!pq.isEmpty()) {
			NodeDist cur = pq.poll();
			if (cur.d != dist.get(cur.n)) continue;     // stale
			if (cur.d > W) break;                       // past the limit -> cannot reach exactly W
			if (cur.n == t) {
				// First time we pop t is the shortest distance s->t without using e.
				return cur.d == W;                      // equal -> superfluous; smaller -> not (under simple paths)
			}
			for (var a : cur.n.adjacentEdges()) {
				// Forbid using e itself
				if (a == e) continue;
				var nb = a.getOpposite(cur.n);
				if (nb == null) continue;               // not incident
				int nd = cur.d + getWeight.applyAsInt(a);
				if (nd < 0) throw new ArithmeticException("Integer overflow or negative weight");
				if (nd > W) continue;                   // prune paths longer than W
				Integer best = dist.get(nb);
				if (best == null || nd < best) {
					dist.put(nb, nd);
					pq.add(new NodeDist(nb, nd));
				}
			}
		}
		return false;
	}
}
