import javafx.geometry.Point2D;
import jloda.graph.Node;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.data.*;
import splitstree6.layout.splits.algorithms.PhylogeneticOutline;
import splitstree6.options.Option;
import splitstree6.workflow.Workflow;

import java.util.*;

/**
 * Asks one question: do the SAME jars - including a macOS-classified javafx-graphics -
 * work on this machine, given that we never start a JavaFX toolkit?
 *
 * If this prints ALL PASS on Linux, Windows and macOS, then splitstree.py can ship a
 * single platform-independent wheel instead of five per-platform ones.
 */
public class PortabilityProbe {
	private static int pass = 0, fail = 0;

	public static void main(String[] args) {
		System.out.printf("os.name    = %s%nos.arch    = %s%njava       = %s (%s)%n%n",
				System.getProperty("os.name"), System.getProperty("os.arch"),
				System.getProperty("java.version"), System.getProperty("java.vendor"));

		var taxa = new TaxaBlock();
		taxa.addTaxaByNames(List.of("a", "b", "c", "d", "e"));
		var dist = new DistancesBlock();
		dist.setNtax(5);
		double[][] d = {{0,2,5,6,6},{2,0,5,6,6},{5,5,0,3,3},{6,6,3,0,2},{6,6,3,2,0}};
		for (var i = 1; i <= 5; i++) for (var j = 1; j <= 5; j++) dist.set(i, j, d[i-1][j-1]);

		check("1. algorithm (neighbor-net)", () -> {
			var splits = new SplitsBlock();
			new NeighborNet().compute(new ProgressSilent(), taxa, dist, splits);
			return splits.getNsplits() == 7 && Math.abs(splits.getFit() - 100.0) < 0.05
					? "7 splits, fit 100.0" : "GOT nsplits=" + splits.getNsplits() + " fit=" + splits.getFit();
		}, "7 splits, fit 100.0");

		check("2. algorithm (neighbor-joining)", () -> {
			var trees = new TreesBlock();
			new NeighborJoining().compute(new ProgressSilent(), taxa, dist, trees);
			return trees.getTree(1).toBracketString(true);
		}, "(((a:1,b:1):3,c:1):1,d:1,e:1)");

		check("3. options reflection", () -> {
			var opts = Option.getAllOptions(new NeighborNet());
			return opts.size() + " option(s), first=" + (opts.isEmpty() ? "-" : opts.get(0).getName());
		}, "1 option(s), first=InferenceAlgorithm");

		check("4. whole workflow, no toolkit", () -> {
			var wf = new Workflow(null);
			wf.setupInputAndWorkingNodes(taxa, dist);
			var splitsNode = wf.newDataNode(new SplitsBlock());
			wf.newAlgorithmNode(new NeighborNet(), wf.getWorkingTaxaNode(), wf.getWorkingDataNode(), splitsNode);
			wf.getInputTaxaFilterNode().restart();
			return "valid=" + wf.isValid() + ", nsplits=" + splitsNode.getDataBlock().getNsplits();
		}, "valid=true, nsplits=7");

		check("5. layout coordinates", () -> {
			var splits = new SplitsBlock();
			new NeighborNet().compute(new ProgressSilent(), taxa, dist, splits);
			var graph = new PhyloSplitsGraph();
			try (var pts = graph.<Point2D>newNodeArray()) {
				var loops = new ArrayList<ArrayList<Node>>();
				PhylogeneticOutline.apply(new ProgressSilent(), true, taxa, splits, graph, pts, new BitSet(), loops, 0, 160.0);
				var a = pts.get(graph.nodeStream().filter(v -> "a".equals(graph.getLabel(v))).findFirst().orElseThrow());
				return String.format("%d nodes, a=(%.3f,%.3f)", graph.getNumberOfNodes(), a.getX(), a.getY());
			}
		}, "8 nodes, a=(0.000,0.000)");

		System.out.printf("%n==== %s ====   (%d passed, %d failed)%n",
				fail == 0 ? "ALL PASS - one wheel can serve every platform" : "FAILURES - per-platform wheels needed",
				pass, fail);
		System.exit(fail == 0 ? 0 : 1);
	}

	interface Probe { String run() throws Exception; }

	private static void check(String name, Probe p, String expected) {
		try {
			var got = p.run();
			var ok = got.equals(expected);
			System.out.printf("%-32s %s%n", name, ok ? "PASS  " + got : "FAIL  expected [" + expected + "] got [" + got + "]");
			if (ok) pass++; else fail++;
		} catch (Throwable ex) {
			System.out.printf("%-32s FAIL  %s: %s%n", name, ex.getClass().getSimpleName(), ex.getMessage());
			fail++;
		}
	}
}
