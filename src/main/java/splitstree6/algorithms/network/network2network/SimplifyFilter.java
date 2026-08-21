/*
 *  SimplifyFilter.java Copyright (C) 2026 Daniel H. Huson
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
 */

package splitstree6.algorithms.network.network2network;

import javafx.beans.property.*;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.EdgeArray;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloGraph;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.view.network.NetworkSequencesAnalyzer;

import java.io.IOException;
import java.util.*;

/**
 * Simplify filter: lossy sparsification of a network towards a goal the user states in the units they care
 * about -- a distortion or excess they are willing to tolerate, a number of edges, a number of cycles -- rather
 * than by hunting for a stretch percentage.
 * <p>
 * <b>One chain, computed once.</b> The filter removes edges one at a time, always the one that costs the least,
 * skipping only a removal that would disconnect two taxa, and records what each removal cost. The result is a
 * nested chain of networks running from the input down to a taxon-spanning tree, and every goal below is a
 * threshold on that one chain. So the goals are mutually consistent, the whole trade-off table comes for free,
 * and asking for more simplification can only ever remove more. (Contrast {@link StretchFilter}, which decides
 * edge by edge against a fixed tolerance and removes edges heaviest-first: raising its tolerance changes
 * <em>which</em> edges go, not just how many.)
 * <p>
 * <b>Why least-cost-first, and why the curve can be trusted.</b> Removing edges can only lengthen shortest
 * paths, so for a fixed edge the cost of removing it is non-decreasing as other edges go. Hence the achieved
 * damage T(k) after k removals is non-decreasing in k: at each step we take the minimum over a subset of edges
 * whose costs have only risen, with the previous minimum gone. T(k) is therefore a genuine monotone cost curve,
 * a large jump in it means the edge just removed was carrying geodesics nothing else can carry -- real
 * structure, not redundancy -- and the knee of the curve is a meaningful default. That monotonicity is also
 * what makes the search affordable: a stale cost is a lower bound, so a lazy greedy over a priority queue
 * usually needs one evaluation per step instead of one per edge per step.
 * <p>
 * <b>Cost.</b> Each evaluation is an all-pairs-over-taxa Dijkstra, so this is markedly more expensive than
 * {@link StretchFilter}; it is meant for networks of the size one actually reads, not for thousands of taxa.
 * <p>
 * Plan: {@code razornet/ai/plans/2026-08-21-simplify-filter.md}.
 * <p>
 * Daniel Huson, 2026
 */
public class SimplifyFilter extends Network2Network {
	/**
	 * what the user is asking for. Every one of these is a threshold on the same chain of removals.
	 */
	public enum Target {
		/**
		 * report the trade-off and change nothing. The default, because adding this node to a workflow should
		 * not silently throw away a quarter of the network -- it should show what the choices cost, so the user
		 * picks a target once and informed, which is the whole point of the filter
		 */
		None,
		/** no number to choose: stop where the cost curve turns up */
		Knee,
		/** stop before the damage exceeds this percent: distortion - 1, or excess relative to the input total */
		MaxDamagePercent,
		/** stop at this many drawn edges */
		MaxEdges,
		/** stop at this many independent cycles; 0 gives a tree */
		MaxCycles,
		/** remove this percent of the drawn edges */
		EdgeReductionPercent
	}

	/**
	 * what "damage" means. Auto picks Excess for a haplotype network and Distortion for anything else.
	 */
	public enum Damage {Auto, Distortion, Excess}

	// a removal costs this many times the median cost so far before it counts as a jump, i.e. as having cut
	// into structure rather than redundancy. 5 is deliberately loose: a false alarm would send the user to a
	// smaller network than they asked for, and the flag is advisory in any case.
	// how much more damage than the knee before the report says so. Advisory: the target is still honoured.
	private static final double PAST_KNEE_FACTOR = 2.0;
	private static final int TABLE_ROWS = 12;

	private final ObjectProperty<Target> optionTarget = new SimpleObjectProperty<>(this, "optionTarget", Target.None);
	private final DoubleProperty optionTargetValue = new SimpleDoubleProperty(this, "optionTargetValue", 0.0);
	private final ObjectProperty<Damage> optionDamage = new SimpleObjectProperty<>(this, "optionDamage", Damage.Auto);
	private final DoubleProperty optionDamageQuantilePercent = new SimpleDoubleProperty(this, "optionDamageQuantilePercent", 100.0);
	private final BooleanProperty optionReportTable = new SimpleBooleanProperty(this, "optionReportTable", true);

	/** one link of the chain: the state after removing the first {@code index} edges, cheapest first */
	private record Step(int index, Edge removed, Measures measures, int cycles, int edges, int nodes, double length) {
		double damage() {
			return measures.statistic();
		}
	}

	/**
	 * Everything worth reporting about one state, from a single all-pairs pass.
	 * <p>
	 * {@code statistic} is what drives the ordering and the targets -- the requested percentile of the stretches,
	 * or the excess. The other two are what the user is told, so that a run bounded by one measure still says
	 * where it left the others; at a percentile below 100 the statistic and the worst-pair distortion are
	 * genuinely different numbers, and reporting the first under the second's name would be a lie.
	 *
	 * @param excess NaN when the network carries no sequences to compare against
	 */
	private record Measures(double statistic, double distortion, double excess) {
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionTarget.getName(), optionTargetValue.getName(), optionDamage.getName(),
				optionDamageQuantilePercent.getName(), optionReportTable.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionTarget.getName().equals(optionName))
			return "what to aim for: None (the default) reports the trade-off and leaves the network alone, so you can read off what each choice would cost; Knee stops automatically where the cost curve turns up; MaxDamagePercent, MaxEdges, MaxCycles and EdgeReductionPercent stop at the target value below";
		else if (optionTargetValue.getName().equals(optionName))
			return "the value the target is aiming at: a percent for MaxDamagePercent and EdgeReductionPercent, a count of drawn edges for MaxEdges, a count of independent cycles for MaxCycles (0 = a tree). Ignored for Knee";
		else if (optionDamage.getName().equals(optionName))
			return "what simplification costs: Distortion = how far pairwise distances are stretched; Excess = how far the mutations drawn exceed the sequence differences (haplotype networks); Auto = Excess for a haplotype network, Distortion otherwise";
		else if (optionDamageQuantilePercent.getName().equals(optionName))
			return "for Distortion: bound this percentile of the pairwise stretches rather than the worst pair (100 = the worst pair). One stubborn pair can otherwise hold the whole filter back; 95 typically removes many more edges at the same apparent faithfulness";
		else if (optionReportTable.getName().equals(optionName))
			return "print the whole trade-off table -- cycles, edges, length, distortion and excess along the chain -- so the target can be chosen once, informed, instead of by trial and error";
		return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, NetworkBlock inputData, NetworkBlock outputData) throws IOException {
		outputData.copy(inputData);

		try {
			var graph = outputData.getGraph();
			var taxa = graph.nodeStream().filter(graph::hasTaxa).toList();
			if (taxa.size() < 2) {
				setShortDescription("nothing to simplify (fewer than two taxa)");
				return;
			}
			var edges0 = graph.getNumberOfEdges();

			var measure = resolveDamage(outputData, taxa);
			var quantile = (measure == Damage.Excess ? 100.0 : Math.max(1.0, Math.min(100.0, getOptionDamageQuantilePercent())));

			var alive = new HashSet<Edge>();
			graph.edges().forEach(alive::add);

			// the reference every stretch is measured against: the INPUT network's own taxon-to-taxon distances.
			// Deliberately the network and not a distances block, so this works on any network, exactly as in
			// StretchFilter -- when the input realizes its metric the two coincide, which is what makes the
			// reported distortion match the number the network viewer prints.
			var reference = allPairs(graph, taxa, alive);
			if (reference == null)
				throw new IOException("Network does not connect all taxa");

			// the constant the excess is measured from: the Hamming distances between the taxon sequences, as
			// the viewer computes them, so the excess reported here is the excess reported there. Null when
			// there are no characters to ask (headless, or not a haplotype network) -- then the excess is
			// reported relative to the input network instead, and said to be.
			// asked of the INPUT block: it is the one with a workflow node, so it is the one that can find the
			// characters up the workflow. outputData is a fresh copy and its node is not set until this returns.
			// The taxon sequences are the same in both, so the total is the same.
			var inputMutations = (measure == Damage.Excess ? inputPairwiseMutations(inputData) : null);
			var baseline = measures(reference, reference, measure, quantile, inputMutations);
			damageScale = (measure == Damage.Excess ? pairwiseTotal(reference, inputMutations) : 0);

			// the input as it is DRAWN -- smoothed, so its edge and cycle counts are the ones a size target is
			// stated against, and the ones the table's first row has to be comparable with
			var inputStep = step(-1, null, baseline, graph, outputData, alive, taxa);

			var chain = buildChain(progress, graph, outputData, taxa, alive, reference, measure, quantile, inputMutations, baseline);

			var choice = chooseStep(chain, inputStep);
			var knee = knee(chain);

			for (var i = 0; i <= choice; i++)
				graph.deleteEdge(chain.get(i).removed());
			if (choice >= 0)
				NetworkSimplification.cleanAndSmooth(graph, outputData, progress);

			report(chain, inputStep, choice, knee, measure, quantile, inputMutations != null);
		} catch (IOException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}

	/**
	 * Builds the chain, cheapest removal first, by lazy greedy: a cost computed against a larger edge set is a
	 * lower bound on the cost now, so an entry popped with a current stamp is provably the cheapest and needs no
	 * re-evaluation. An edge whose removal disconnects two taxa is dropped from the queue for good -- removing
	 * further edges can never reconnect them.
	 */
	private List<Step> buildChain(ProgressListener progress, PhyloGraph graph, NetworkBlock block, List<Node> taxa,
								  Set<Edge> alive, double[][] reference, Damage measure, double quantile,
								  Double inputMutations, Measures baseline) throws Exception {
		record Entry(Edge edge, double cost, Measures measures, int stamp) {
		}
		var chain = new ArrayList<Step>();
		var queue = new PriorityQueue<Entry>(Comparator.comparingDouble(Entry::cost));
		for (var e : alive)
			queue.add(new Entry(e, baseline.statistic(), baseline, -1)); // a valid lower bound for every edge

		progress.setSubtask("Simplify filter");
		progress.setMaximum(alive.size());
		progress.setProgress(0);

		var current = baseline.statistic();
		while (!queue.isEmpty()) {
			var entry = queue.poll();
			if (entry.stamp() == chain.size()) {   // evaluated against the current edge set: provably cheapest
				alive.remove(entry.edge());
				current = entry.cost();
				var chosen = chain.size();
				chain.add(step(chosen, entry.edge(), entry.measures(), graph, block, alive, taxa));
				if (chain.get(chosen).cycles() == 0)
					break; // a taxon-spanning tree: nothing drawn is left to remove
				progress.incrementProgress();
				continue;
			}
			alive.remove(entry.edge());
			var distances = allPairs(graph, taxa, alive);
			alive.add(entry.edge());
			if (distances == null)
				continue; // disconnects taxa: never a candidate again
			var m = measures(distances, reference, measure, quantile, inputMutations);
			queue.add(new Entry(entry.edge(), Math.max(current, m.statistic()), m, chain.size()));
			progress.checkForCancel();
		}
		return chain;
	}

	/** the chain entry for a state: its damage, and what the network would actually look like once smoothed */
	private static Step step(int index, Edge removed, Measures measures, PhyloGraph graph, NetworkBlock block,
							 Set<Edge> alive, List<Node> taxa) throws Exception {
		// cycles are invariant under smoothing (it removes a node and an edge at a time), but the edge and node
		// counts and the length are not, and "at most 60 edges" means 60 DRAWN edges -- so measure a copy that
		// has actually been smoothed. On the size of network this filter is for, that is a fraction of the cost
		// of one damage evaluation.
		var copy = new PhyloGraph();
		try (NodeArray<Node> oldNode2new = graph.newNodeArray(); EdgeArray<Edge> oldEdge2new = graph.newEdgeArray()) {
			copy.copy(graph, oldNode2new, oldEdge2new);
			for (var e : graph.edges()) {
				if (!alive.contains(e))
					copy.deleteEdge(oldEdge2new.get(e));
			}
		}
		NetworkSimplification.cleanAndSmooth(copy, null, null);
		var cycles = copy.getNumberOfEdges() - copy.getNumberOfNodes() + components(copy);
		var length = copy.edgeStream().mapToDouble(copy::getWeight).sum();
		return new Step(index, removed, measures, cycles, copy.getNumberOfEdges(), copy.getNumberOfNodes(), length);
	}

	private static int components(PhyloGraph graph) {
		var seen = new HashSet<Node>();
		var count = 0;
		for (var v : graph.nodes()) {
			if (seen.add(v)) {
				count++;
				var stack = new ArrayDeque<Node>();
				stack.push(v);
				while (!stack.isEmpty()) {
					for (var w : stack.pop().adjacentNodes()) {
						if (seen.add(w))
							stack.push(w);
					}
				}
			}
		}
		return count;
	}

	/**
	 * All taxon-to-taxon shortest path distances over {@code alive}, indexed by position in {@code taxa}, or null
	 * if some pair is unreachable.
	 */
	private static double[][] allPairs(PhyloGraph graph, List<Node> taxa, Set<Edge> alive) {
		var n = taxa.size();
		var result = new double[n][n];
		for (var i = 0; i < n; i++) {
			var dist = NetworkSimplification.singleSource(graph, taxa.get(i), alive);
			for (var j = 0; j < n; j++) {
				if (i != j) {
					var d = dist.get(taxa.get(j));
					if (d == null)
						return null;
					result[i][j] = d;
				}
			}
		}
		return result;
	}

	/**
	 * Every measure of a state, from one pass over the taxon pairs: the statistic the target is stated in, the
	 * worst-pair distortion, and the excess in mutations.
	 */
	private static Measures measures(double[][] distances, double[][] reference, Damage measure, double quantile, Double inputMutations) {
		var n = distances.length;

		// UNORDERED pairs, because that is what NetworkSequencesAnalyzer sums over -- its loop runs
		// graph.nodes(v), i.e. the nodes AFTER v -- and the whole point of taking the sequence total from it is
		// that the excess reported here is the excess the network viewer prints. Counting ordered pairs here
		// doubled the first term and left the second alone, which made an exact network report an excess equal
		// to its own total pairwise distance instead of 0.
		var sum = 0.0;
		var base = 0.0;
		var ratios = new double[Math.max(1, n * (n - 1) / 2)];
		var k = 0;
		for (var i = 0; i < n; i++) {
			for (var j = i + 1; j < n; j++) {
				sum += distances[i][j];
				base += reference[i][j];
				ratios[k++] = (reference[i][j] > 0 ? distances[i][j] / reference[i][j] : 1.0);
			}
		}
		var excess = sum - (inputMutations != null ? inputMutations : base);

		var worst = 1.0;
		for (var i = 0; i < k; i++)
			worst = Math.max(worst, ratios[i]);

		double statistic;
		if (measure == Damage.Excess) {
			statistic = excess;
		} else if (quantile >= 100.0 || k == 0) {
			statistic = worst;
		} else {
			Arrays.sort(ratios, 0, k);
			statistic = ratios[Math.min(k - 1, Math.max(0, (int) Math.ceil(quantile / 100.0 * k) - 1))];
		}
		return new Measures(statistic, worst, inputMutations != null ? excess : Double.NaN);
	}

	/**
	 * What an excess is a percentage of: the sequence differences when we have them, else the input network's own
	 * pairwise total. Unordered pairs, to match {@link #measures}.
	 */
	private static double pairwiseTotal(double[][] reference, Double inputMutations) {
		if (inputMutations != null)
			return inputMutations;
		var sum = 0.0;
		for (var i = 0; i < reference.length; i++)
			for (var j = i + 1; j < reference.length; j++)
				sum += reference[i][j];
		return sum;
	}

	/** the sum of Hamming distances over ordered taxon pairs, exactly as the network viewer computes it, or null */
	private static Double inputPairwiseMutations(NetworkBlock block) {
		try {
			if (!NetworkSequencesAnalyzer.isApplicable(block))
				return null;
			return (double) new NetworkSequencesAnalyzer(block).inputPairwiseDistances(block);
		} catch (Exception ignored) {
			return null; // no workflow, or no characters to ask: fall back to the input network's own distances
		}
	}

	/**
	 * Excess when the network really carries sequences, distortion otherwise.
	 * <p>
	 * Deliberately does NOT consult {@code getNetworkType()}: RazorNet marks even a purely distance-based network
	 * as {@code Type.HaplotypeNetwork} (bridge {@code RazorNet1.java:357}), so trusting the label would measure a
	 * distance network in mutations it does not have. Having a sequence on every taxon node is the structural
	 * test, and it works headless as well as in a workflow.
	 */
	private Damage resolveDamage(NetworkBlock block, List<Node> taxa) {
		if (getOptionDamage() != Damage.Auto)
			return getOptionDamage();
		return taxa.stream().allMatch(v -> block.getNodeData(v).containsKey(NetworkBlock.NODE_STATES_KEY))
				? Damage.Excess : Damage.Distortion;
	}

	/**
	 * The step the target selects, or -1 to keep the network unchanged. Every target is a threshold on the same
	 * chain, so they cannot disagree with one another -- but the two families of target read the chain from
	 * opposite ends.
	 * <p>
	 * A <b>damage bound</b> asks how far we may go: damage only rises along the chain, so it wants the LARGEST
	 * prefix still within the bound. A <b>size target</b> asks for a network of a given shape: edges and cycles
	 * only fall along the chain, so it wants the SMALLEST prefix that reaches it -- going further would throw
	 * away structure the target never asked to lose. Conflating the two (the first version did) turns
	 * "at most 4 cycles" into "as few cycles as possible", i.e. a tree.
	 */
	private int chooseStep(List<Step> chain, Step inputStep) {
		targetReached = true;
		if (chain.isEmpty())
			return -1;
		var value = getOptionTargetValue();
		return switch (getOptionTarget()) {
			case None -> -1;
			case Knee -> knee(chain);
			case MaxDamagePercent -> largestPrefixWithin(chain, s -> damagePercent(s.damage()) <= value + 1e-9);
			case MaxEdges -> smallestPrefixReaching(chain, s -> s.edges() <= value + 1e-9, inputStep);
			case MaxCycles -> smallestPrefixReaching(chain, s -> s.cycles() <= value + 1e-9, inputStep);
			case EdgeReductionPercent -> smallestPrefixReaching(chain,
					s -> 100.0 * (inputStep.edges() - s.edges()) / Math.max(1, inputStep.edges()) >= value - 1e-9, inputStep);
		};
	}

	/** the largest prefix still within a bound that only tightens as the chain goes on */
	private static int largestPrefixWithin(List<Step> chain, java.util.function.Predicate<Step> ok) {
		var last = -1;
		for (var s : chain) {
			if (ok.test(s))
				last = s.index();
			else
				break;
		}
		return last;
	}

	/** the first step to reach a size target -- or -1 if the input already meets it, or the whole chain if
	 * nothing does, in which case the report says the target was out of reach rather than pretending */
	private int smallestPrefixReaching(List<Step> chain, java.util.function.Predicate<Step> ok, Step inputStep) {
		if (ok.test(inputStep))
			return -1;
		for (var s : chain) {
			if (ok.test(s))
				return s.index();
		}
		targetReached = false;
		return chain.size() - 1;
	}

	// set by chooseStep: false when a size target could not be reached even by simplifying all the way
	private boolean targetReached = true;

	// what MaxDamagePercent is a percent OF. For distortion the scale is implicit (the percent is simply the
	// inflation, distortion - 1); for excess it is the input pairwise total, so the target reads "the distances
	// drawn may exceed the sequence differences by at most this percent". Set before the target is applied.
	private double damageScale = 0;

	/** the damage of a state in the percent the targets speak in */
	private double damagePercent(double damage) {
		return (damageScale > 0 ? 100.0 * damage / damageScale : 100.0 * (damage - 1.0));
	}

	/**
	 * Where the cost curve turns up: the point furthest below the chord joining its two ends -- the classical
	 * elbow.
	 * <p>
	 * An earlier version instead took the step before the first removal costing more than a few times the median
	 * cost so far. That fires far too early, and for a reason inherent to removing edges cheapest-first: most
	 * early removals are free, so the median positive cost is tiny and the first merely-ordinary removal trips
	 * it. On PAegRV-characters it stopped at 31 cycles over a step costing 24 mutations, while the actual
	 * collapse -- excess 1,975 to 10,651 in one step -- came fifty removals later. The chord is scale-free and
	 * needs no threshold.
	 */
	private static int knee(List<Step> chain) {
		if (chain.size() < 3)
			return chain.size() - 1;
		var first = chain.get(0).damage();
		var last = chain.get(chain.size() - 1).damage();
		if (last - first < 1e-12)
			return chain.size() - 1; // nothing costs anything: simplify all the way
		var best = 0;
		var bestGap = -1.0;
		for (var i = 0; i < chain.size(); i++) {
			var t = (double) i / (chain.size() - 1);
			var gap = (first + t * (last - first)) - chain.get(i).damage();
			if (gap > bestGap) {
				bestGap = gap;
				best = i;
			}
		}
		return best;
	}

	/** the single most expensive removal in the chain: worth naming in the report, not worth thresholding on */
	private static int steepest(List<Step> chain) {
		var best = -1;
		var bestDelta = 0.0;
		for (var i = 1; i < chain.size(); i++) {
			var delta = chain.get(i).damage() - chain.get(i - 1).damage();
			if (delta > bestDelta) {
				bestDelta = delta;
				best = i;
			}
		}
		return best;
	}

	private void report(List<Step> chain, Step inputStep, int choice, int knee, Damage measure, double quantile,
						boolean excessAgainstSequences) {
		var jump = steepest(chain);
		var chosen = (choice >= 0 ? chain.get(choice) : inputStep);

		if (isOptionReportTable() && !chain.isEmpty()) {
			System.err.printf("Simplify filter: %s damage%s; %,d removals available%n",
					measure == Damage.Excess ? (excessAgainstSequences ? "excess (against the sequences)" : "excess (against the input network)")
							: (quantile >= 100 ? "distortion (worst pair)" : "distortion (%.0fth percentile)".formatted(quantile)),
					"", chain.size());
			System.err.printf("  %5s %7s %7s %10s %11s %9s %10s%n", "step", "cycles", "edges", "length",
					"distortion", "excess", quantile >= 100 ? "" : "q%.0f".formatted(quantile));
			printRow(inputStep, quantile, "");
			var previous = inputStep;
			for (var index : tableRows(chain, choice, knee, jump)) {
				var s = chain.get(index);
				var mark = (index == choice ? " <- chosen" : "") + (index == knee ? " <- knee" : "")
						   + (index == jump ? " <- jump" : "");
				// a removal of an edge that smoothing discards anyway changes nothing drawn; printing the same
				// row again would only make the table look like it is offering a choice that it is not
				if (mark.isEmpty() && s.edges() == previous.edges() && s.cycles() == previous.cycles()
					&& Math.abs(s.length() - previous.length()) < 1e-9)
					continue;
				printRow(s, quantile, mark);
				previous = s;
			}
		}

		var buf = new StringBuilder();
		buf.append(switch (getOptionTarget()) {
			case None -> "no target (reporting only)";
			case Knee -> "knee";
			case MaxDamagePercent -> "damage <= %s%%".formatted(StringUtils.trim(getOptionTargetValue()));
			case MaxEdges -> "at most %s edges".formatted(StringUtils.trim(getOptionTargetValue()));
			case MaxCycles -> "at most %s cycles".formatted(StringUtils.trim(getOptionTargetValue()));
			case EdgeReductionPercent -> "remove %s%% of edges".formatted(StringUtils.trim(getOptionTargetValue()));
		});
		if (choice < 0) {
			buf.append(switch (getOptionTarget()) {
				case None -> ": network unchanged; the knee is at %,d cycles, %,d edges, %s".formatted(
						chain.get(knee).cycles(), chain.get(knee).edges(), describe(chain.get(knee), measure));
				case Knee -> ": network unchanged (nothing worth removing)";
				default -> ": network unchanged (the input already meets it)";
			});
		} else {
			if (!targetReached)
				buf.append(" (OUT OF REACH, simplified as far as possible)");
			buf.append(": %,d of %,d edges removed, %,d cycles, length %s, distortion %.3f".formatted(
					inputStep.edges() - chosen.edges(), inputStep.edges(), chosen.cycles(),
					StringUtils.trim((float) chosen.length()), chosen.measures().distortion()));
			if (!Double.isNaN(chosen.measures().excess()))
				buf.append(", excess %s".formatted(StringUtils.trim((float) chosen.measures().excess())));
			if (quantile < 100)
				buf.append(", %.0fth-percentile stretch %.3f".formatted(quantile, chosen.damage()));
			// "have I gone too far?" answered against the knee rather than against a per-step threshold: the
			// question is not whether some removal was expensive but whether the network being drawn now costs
			// much more than the one at the elbow did.
			var kneeDamage = chain.get(knee).damage();
			var ratio = (Math.abs(kneeDamage) > 1e-9 ? chosen.damage() / kneeDamage : (chosen.damage() > 1e-9 ? Double.POSITIVE_INFINITY : 1.0));
			if (choice > knee && ratio > PAST_KNEE_FACTOR) {
				buf.append("; WARNING %.1f times the damage at the knee".formatted(ratio));
				System.err.printf("WARNING (Simplify filter): this target goes %d removals past the knee, and costs %.1f times as much.%n",
						choice - knee, ratio);
				System.err.printf("         chosen (step %d): %,d cycles, %,d edges, %s%n", choice, chosen.cycles(), chosen.edges(), describe(chosen, measure));
				System.err.printf("         knee   (step %d): %,d cycles, %,d edges, %s%n", knee, chain.get(knee).cycles(), chain.get(knee).edges(), describe(chain.get(knee), measure));
				if (jump > 0)
					System.err.printf("         The single steepest removal is step %d, which alone cost %s: that edge was carrying%n         geodesics nothing else can carry, so past it the network hides structure the data supports.%n",
							jump, measure == Damage.Excess
									? "%s extra mutations".formatted(StringUtils.trim((float) (chain.get(jump).damage() - chain.get(jump - 1).damage())))
									: "%.3f of stretch".formatted(chain.get(jump).damage() - chain.get(jump - 1).damage()));
			}
		}
		setShortDescription(buf.toString());
	}

	private static String describe(Step s, Damage measure) {
		return "distortion %.3f%s".formatted(s.measures().distortion(),
				Double.isNaN(s.measures().excess()) ? "" : ", excess %s".formatted(StringUtils.trim((float) s.measures().excess())));
	}

	private static void printRow(Step s, double quantile, String mark) {
		System.err.printf("  %5s %7d %7d %10s %11s %9s %10s%s%n", s.index() < 0 ? "input" : String.valueOf(s.index()),
				s.cycles(), s.edges(), StringUtils.trim((float) s.length()),
				"%.3f".formatted(s.measures().distortion()),
				Double.isNaN(s.measures().excess()) ? "-" : StringUtils.trim((float) s.measures().excess()),
				quantile >= 100 ? "" : "%.3f".formatted(s.damage()), mark);
	}

	/** the rows worth printing: evenly spread over the chain, plus the chosen step, the knee and the jump */
	private static List<Integer> tableRows(List<Step> chain, int choice, int knee, int jump) {
		var rows = new TreeSet<Integer>();
		for (var i = 0; i < TABLE_ROWS; i++)
			rows.add((int) Math.round((double) i * (chain.size() - 1) / (TABLE_ROWS - 1)));
		for (var index : List.of(choice, knee, jump)) {
			if (index >= 0 && index < chain.size())
				rows.add(index);
		}
		return new ArrayList<>(rows);
	}

	public Target getOptionTarget() {
		return optionTarget.get();
	}

	public ObjectProperty<Target> optionTargetProperty() {
		return optionTarget;
	}

	public double getOptionTargetValue() {
		return optionTargetValue.get();
	}

	public DoubleProperty optionTargetValueProperty() {
		return optionTargetValue;
	}

	public Damage getOptionDamage() {
		return optionDamage.get();
	}

	public ObjectProperty<Damage> optionDamageProperty() {
		return optionDamage;
	}

	public double getOptionDamageQuantilePercent() {
		return optionDamageQuantilePercent.get();
	}

	public DoubleProperty optionDamageQuantilePercentProperty() {
		return optionDamageQuantilePercent;
	}

	public boolean isOptionReportTable() {
		return optionReportTable.get();
	}

	public BooleanProperty optionReportTableProperty() {
		return optionReportTable;
	}
}
