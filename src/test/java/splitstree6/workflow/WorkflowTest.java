/*
 *  WorkflowTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.workflow;

import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.algorithms.characters.characters2distances.HammingDistance;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.*;
import splitstree6.data.parts.CharactersType;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.nexus.workflow.WorkflowNexusOutput;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the workflow: running it, changing an option, deselecting taxa, saving and reading it back. No user interface: a
 * workflow then computes on the calling thread, and is finished when restart returns
 */
public class WorkflowTest {
	private static final AtomicInteger distanceRuns = new AtomicInteger();
	private static final AtomicInteger treeRuns = new AtomicInteger();

	/**
	 * counts how often it is run
	 */
	public static class CountingHammingDistance extends HammingDistance {
		@Override
		public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock characters, DistancesBlock distances) throws IOException {
			distanceRuns.incrementAndGet();
			super.compute(progress, taxaBlock, characters, distances);
		}
	}

	public static class CountingNeighborJoining extends NeighborJoining {
		@Override
		public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances, TreesBlock trees) throws jloda.util.CanceledException {
			treeRuns.incrementAndGet();
			super.compute(progress, taxaBlock, distances, trees);
		}
	}

	private record Pipeline(Workflow workflow, DataNode<DistancesBlock> distances, AlgorithmNode<?, ?> distanceNode, DataNode<TreesBlock> trees,
							AlgorithmNode<?, ?> treeNode, DataNode<SplitsBlock> splits, AlgorithmNode<?, ?> splitsNode) {
	}

	/**
	 * characters -> Hamming distances -> neighbor-joining tree, and -> neighbor net
	 */
	private static Pipeline pipeline(String[] rows) {
		return pipeline(rows, new CountingHammingDistance(), new CountingNeighborJoining());
	}

	private static Pipeline pipeline(String[] rows, HammingDistance hamming, NeighborJoining neighborJoining) {
		var workflow = new Workflow(null);
		workflow.setupInputAndWorkingNodes(taxa(rows.length), characters(CharactersType.DNA, "acgt", rows));
		var distances = workflow.newDataNode(new DistancesBlock());
		var distanceNode = workflow.newAlgorithmNode(hamming, workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distances);
		var trees = workflow.newDataNode(new TreesBlock());
		var treeNode = workflow.newAlgorithmNode(neighborJoining, workflow.getWorkingTaxaNode(), distances, trees);
		var splits = workflow.newDataNode(new SplitsBlock());
		var splitsNode = workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), distances, splits);
		return new Pipeline(workflow, distances, distanceNode, trees, treeNode, splits, splitsNode);
	}

	private static String[] rows(int n, int seed) {
		return simulateSequences(n, 200, 0.05, 0, new Random(seed));
	}

	private static double[][] matrix(DistancesBlock distances) {
		var n = distances.getNtax();
		var d = new double[n][n];
		for (var i = 1; i <= n; i++) for (var j = 1; j <= n; j++) d[i - 1][j - 1] = distances.get(i, j);
		return d;
	}

	@Test
	void aWorkflowComputesWhatTheAlgorithmsComputeDirectly() throws Exception {
		var rows = rows(10, 401);
		var p = pipeline(rows);
		p.workflow().getInputTaxaFilterNode().restart();
		assertTrue(p.workflow().isValid(), "the workflow is finished when restart returns");
		var expected = run(new HammingDistance(), characters(CharactersType.DNA, "acgt", rows));
		assertArrayEquals(matrix(expected), matrix(p.distances().getDataBlock()));
		var tree = weightedSplits(tree(new NeighborJoining(), matrix(expected)), 10);
		assertEquals(tree, weightedSplits(p.trees().getDataBlock().getTree(1), 10));
		assertEquals(weightedSplits(splits(new NeighborNet(), matrix(expected)), 10, 0), weightedSplits(p.splits().getDataBlock(), 10, 0));
		assertEquals(10, p.workflow().getWorkingTaxaBlock().getNtax());
	}

	/**
	 * running one step again runs what depends on it, and nothing else
	 */
	@Test
	void rerunningAStepRecomputesOnlyWhatDependsOnIt() {
		var p = pipeline(rows(8, 402));
		p.workflow().getInputTaxaFilterNode().restart();
		distanceRuns.set(0);
		treeRuns.set(0);
		p.treeNode().restart();
		assertEquals(0, distanceRuns.get());
		assertEquals(1, treeRuns.get());
		p.distanceNode().restart();
		assertEquals(1, distanceRuns.get());
		assertEquals(2, treeRuns.get(), "the tree depends on the distances");
		assertTrue(p.workflow().isValid());
	}

	/**
	 * deselecting a taxon: every result is then that of the remaining sequences, and selecting it again brings back
	 * the first result
	 */
	@Test
	void deselectingATaxon() throws Exception {
		var rows = rows(9, 403);
		var p = pipeline(rows);
		p.workflow().getInputTaxaFilterNode().restart();
		var all = weightedSplits(p.trees().getDataBlock().getTree(1), 9);

		var filter = (TaxaFilter) p.workflow().getInputTaxaFilterNode().getAlgorithm();
		filter.setDisabled(List.of("t3", "t7"), true);
		p.workflow().getInputTaxaFilterNode().restart();
		assertTrue(p.workflow().isValid());
		assertEquals(List.of("t1", "t2", "t4", "t5", "t6", "t8", "t9"), p.workflow().getWorkingTaxaBlock().getLabels());
		assertEquals(9, p.workflow().getInputTaxaBlock().getNtax(), "the input is not changed");
		var remaining = new String[]{rows[0], rows[1], rows[3], rows[4], rows[5], rows[7], rows[8]};
		var expected = run(new HammingDistance(), characters(CharactersType.DNA, "acgt", remaining));
		assertEquals(7, p.distances().getDataBlock().getNtax());
		assertArrayEquals(matrix(expected), matrix(p.distances().getDataBlock()));
		assertEquals(weightedSplits(tree(new NeighborJoining(), matrix(expected)), 7), weightedSplits(p.trees().getDataBlock().getTree(1), 7));
		assertEquals(weightedSplits(splits(new NeighborNet(), matrix(expected)), 7, 0), weightedSplits(p.splits().getDataBlock(), 7, 0));

		filter.setDisabled(List.of("t3", "t7"), false);
		p.workflow().getInputTaxaFilterNode().restart();
		assertEquals(all, weightedSplits(p.trees().getDataBlock().getTree(1), 9));
	}

	private static String save(Workflow workflow, boolean workflowOnly) throws IOException {
		var w = new StringWriter();
		new WorkflowNexusOutput().save(workflow, w, workflowOnly);
		return w.toString();
	}

	private static Workflow read(String text) throws IOException {
		var workflow = new Workflow(null);
		WorkflowNexusInput.input(new ProgressSilent(), workflow, new StringReader(text));
		return workflow;
	}

	/**
	 * a saved workflow that is read back has the same steps with the same options and the same data, and saving it
	 * again gives the same text
	 */
	@Test
	void savingAndReadingBack() throws Exception {
		var p = pipeline(rows(8, 404), new HammingDistance(), new NeighborJoining());
		((NeighborNet) p.splitsNode().getAlgorithm()).setOptionInferenceAlgorithm(NeighborNet.InferenceAlgorithm.SplitsTree4);
		((HammingDistance) p.distanceNode().getAlgorithm()).optionMatchGapToGapProperty().set(true);
		((TaxaFilter) p.workflow().getInputTaxaFilterNode().getAlgorithm()).setDisabled("t5", true);
		p.workflow().getInputTaxaFilterNode().restart();
		var text = save(p.workflow(), false);

		var copy = read(text);
		assertEquals(p.workflow().getNumberOfDataNodes(), copy.getNumberOfDataNodes());
		assertEquals(p.workflow().getNumberOfAlgorithmNodes(), copy.getNumberOfAlgorithmNodes());
		assertEquals(List.of("t1", "t2", "t3", "t4", "t6", "t7", "t8"), copy.getWorkingTaxaBlock().getLabels());
		var options = new TreeMap<String, Map<String, String>>();
		for (var node : p.workflow().algorithmNodes()) options.put(node.getAlgorithm().getClass().getSimpleName(), optionValues(node.getAlgorithm()));
		var read = new TreeMap<String, Map<String, String>>();
		for (var node : copy.algorithmNodes()) read.put(node.getAlgorithm().getClass().getSimpleName(), optionValues(node.getAlgorithm()));
		assertEquals(options, read, "the steps and their options");
		assertEquals("true", read.get("HammingDistance").get("optionMatchGapToGapProperty"));
		assertEquals("SplitsTree4", read.get("NeighborNet").get("optionInferenceAlgorithmProperty"));
		var before = weightedSplits(p.splits().getDataBlock(), 7, 0);
		var after = weightedSplits((SplitsBlock) copy.dataNodesStream().filter(d -> d.getDataBlock() instanceof SplitsBlock).findFirst().orElseThrow().getDataBlock(), 7, 0);
		assertEquals(before.keySet(), after.keySet());
		System.err.println("WorkflowTest: largest change of a split weight by saving and reading: " + maxDifference(before, after));
		assertTrue(maxDifference(before, after) < 1e-6, "split weights change by " + maxDifference(before, after));
		// apart from the line with the time of saving
		assertEquals(text.replaceAll("creationDate='[0-9]+'; \\[[^\\]]*\\]", ""), save(copy, false).replaceAll("creationDate='[0-9]+'; \\[[^\\]]*\\]", ""), "saving what was read gives another text");
		assertTrue(text.contains("The original input consisted of 8 taxa and 8 DNA sequences of length 200."), "the methods text describes the input");
	}

	/**
	 * a workflow saved without data, read back and given the data again, computes the same results
	 */
	@Test
	void aWorkflowWithoutDataIsRunOnData() throws Exception {
		var rows = rows(8, 405);
		var p = pipeline(rows, new HammingDistance(), new NeighborJoining());
		p.workflow().getInputTaxaFilterNode().restart();
		var copy = read(save(p.workflow(), true));
		assertEquals(0, copy.getInputTaxaBlock().getNtax(), "saved without data");
		var file = java.nio.file.Files.createTempFile("workflow-test", ".fasta");
		try {
			var fasta = new StringBuilder();
			for (var t = 0; t < rows.length; t++) fasta.append(">t").append(t + 1).append("\n").append(rows[t]).append("\n");
			java.nio.file.Files.writeString(file, fasta);
			WorkflowDataLoader.load(copy, file.toString(), "Unknown");
			copy.getInputTaxaFilterNode().restart();
		} finally {
			java.nio.file.Files.deleteIfExists(file);
		}
		assertTrue(copy.isValid());
		var trees = (TreesBlock) copy.dataNodesStream().filter(d -> d.getDataBlock() instanceof TreesBlock).findFirst().orElseThrow().getDataBlock();
		assertEquals(weightedSplits(p.trees().getDataBlock().getTree(1), 8), weightedSplits(trees.getTree(1), 8));
	}

	/**
	 * a step whose algorithm fails does not leave the workflow looking finished
	 */
	@Test
	void aFailingStepLeavesTheWorkflowInvalid() {
		var workflow = new Workflow(null);
		workflow.setupInputAndWorkingNodes(taxa(5), characters(CharactersType.DNA, "acgt", rows(5, 406)));
		var distances = workflow.newDataNode(new DistancesBlock());
		workflow.newAlgorithmNode(new HammingDistance() {
			@Override
			public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock characters, DistancesBlock distancesBlock) throws IOException {
				throw new IOException("fails");
			}
		}, workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), distances);
		var trees = workflow.newDataNode(new TreesBlock());
		workflow.newAlgorithmNode(new CountingNeighborJoining(), workflow.getWorkingTaxaNode(), distances, trees);
		treeRuns.set(0);
		try {
			workflow.getInputTaxaFilterNode().restart();
		} catch (RuntimeException ignored) { // a run without user interface may pass the failure on
		}
		assertFalse(workflow.isValid());
		assertEquals(0, treeRuns.get(), "a step was run on the output of a step that failed");
		assertEquals(0, trees.getDataBlock().getNTrees());
	}

	/**
	 * data loaded into a workflow keep the traits of their taxa. They were once left out, so that a workflow run on
	 * a file from the command line had no traits where the same file opened in the window had them
	 */
	@Test
	void loadingDataKeepsTheTraits() throws Exception {
		var file = new java.io.File("examples/programs/popart/popart-example.nex");
		org.junit.jupiter.api.Assumptions.assumeTrue(file.exists());
		var p = pipeline(rows(8, 407), new HammingDistance(), new NeighborJoining());
		p.workflow().getInputTaxaFilterNode().restart();
		var copy = read(save(p.workflow(), true));
		WorkflowDataLoader.load(copy, file.getPath(), "Unknown");
		assertTrue(copy.getInputTaxaBlock().getNtax() > 0);
		assertNotNull(copy.getInputTaxaBlock().getTraitsBlock(), "no traits");
		assertTrue(copy.getInputTaxaBlock().getTraitsBlock().getNTraits() > 0);
		copy.getInputTaxaFilterNode().restart();
		assertTrue(copy.isValid());
		assertNotNull(copy.getWorkingTaxaBlock().getTraitsBlock(), "the traits do not reach the working taxa");
		assertEquals(copy.getInputTaxaBlock().getTraitsBlock().getNTraits(), copy.getWorkingTaxaBlock().getTraitsBlock().getNTraits());
	}

	/**
	 * the output streams of the program are the same after cycles have been computed on several threads. The cycle
	 * computation once replaced them while it ran, and two threads together could leave them replaced for good
	 */
	@Test
	void computingCyclesInParallelLeavesTheOutputStreamsAlone() throws Exception {
		var out = System.out;
		var err = System.err;
		var n = 12;
		var block = splitsBlock(randomCircularSplits(n, new Random(408)), n);
		var threads = new ArrayList<Thread>();
		var written = new java.io.ByteArrayOutputStream();
		var capture = new java.io.PrintStream(written);
		System.setOut(capture);
		System.setErr(capture);
		try {
			for (var t = 0; t < 8; t++) {
				var thread = new Thread(() -> {
					for (var r = 0; r < 200; r++) splitstree6.algorithms.utils.SplitsBlockUtilities.computeCycle(n, block.getSplits());
				});
				threads.add(thread);
				thread.start();
			}
			for (var thread : threads) thread.join();
			assertSame(capture, System.out);
			assertSame(capture, System.err);
		} finally {
			System.setOut(out);
			System.setErr(err);
		}
		assertEquals("", written.toString(), "the cycle computation writes to the output");
	}

	/**
	 * a Nexus export with the taxa block does not make later exports write the taxa block as well
	 */
	@Test
	void exportWithTaxaDoesNotStick() throws Exception {
		var distances = distances(new double[][]{{0, 1, 2}, {1, 0, 3}, {2, 3, 0}});
		var manager = splitstree6.io.writers.ExportManager.getInstance();
		var plain = new StringWriter();
		manager.write(taxa(3), distances, "Nexus", plain);
		var withTaxa = new StringWriter();
		manager.write(taxa(3), distances, "NexusWithTaxa", withTaxa);
		var again = new StringWriter();
		manager.write(taxa(3), distances, "Nexus", again);
		assertTrue(withTaxa.toString().toLowerCase().contains("begin taxa"));
		assertFalse(plain.toString().toLowerCase().contains("begin taxa"));
		assertEquals(plain.toString(), again.toString());
	}
}
