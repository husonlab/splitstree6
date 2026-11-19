/*
 *  NeighborNetAlgorithmTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import jloda.util.parse.NexusStreamParser;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.algorithms.characters.characters2distances.HammingDistance;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.characters.FastAReader;
import splitstree6.options.OptionIO;

import java.io.File;
import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static splitstree6.testing.TestUtils.*;

/**
 * NeighborNet: the split weights are the non-negative least-squares fit of the splits of the circular ordering to the
 * distances. That fit is unique, so it can be compared with one computed independently
 */
public class NeighborNetAlgorithmTest {
	static SplitsBlock run(NeighborNet.InferenceAlgorithm inference, double[][] d) throws Exception {
		var algorithm = new NeighborNet();
		algorithm.setOptionInferenceAlgorithm(inference);
		var splits = new SplitsBlock();
		algorithm.compute(new ProgressSilent(), taxa(d.length), distances(d), splits);
		return splits;
	}

	static double sumOfSquares(double[][] d) {
		var sum = 0.0;
		for (var i = 0; i < d.length; i++) for (var j = i + 1; j < d.length; j++) sum += d[i][j] * d[i][j];
		return sum;
	}

	/**
	 * ten tree metrics and ten circular metrics, with noise
	 */
	static List<double[][]> noisyInputs(int n, double sigma) {
		var random = new Random(n);
		var result = new ArrayList<double[][]>();
		for (var r = 0; r < 20; r++)
			result.add(r < 10 ? noisy(metric(randomTree(n, random), n), sigma, random) : noisyCircular(n, sigma, random));
		return result;
	}

	@Test
	void theDefaultIsActiveSet() {
		assertEquals(NeighborNet.InferenceAlgorithm.ActiveSet, new NeighborNet().getOptionInferenceAlgorithm());
	}

	/**
	 * distances that are the sum of circular splits are fitted exactly by those splits and by no others
	 */
	@Test
	void theSplitsOfACircularMetricAreRecovered() throws Exception {
		var n = 12;
		for (var inference : NeighborNet.InferenceAlgorithm.values()) {
			var random = new Random(17);
			for (var r = 0; r < 20; r++) {
				var truth = randomCircularSplits(n, random);
				var splits = run(inference, metric(truth, n));
				var found = weightedSplits(splits, n, 1e-6);
				assertEquals(truth.keySet(), found.keySet(), inference + ", input " + r);
				assertTrue(maxDifference(truth, found) < 1e-6, inference + ", input " + r + ": weights differ by " + maxDifference(truth, found));
				assertEquals(100, splits.getFit(), 1e-3, inference + ", input " + r);
			}
		}
	}

	/**
	 * on distances that no set of circular splits fits exactly, against non-negative least squares computed by coordinate
	 * descent for the same ordering. The cut-off drops splits lighter than 0.0001 of the largest distance, which costs a
	 * little of the fit; the bound allows for that. Before the stopping rule of ActiveSet was tightened, its objective
	 * was above the optimum by up to 2e-5 of the sum of squared distances and 102 splits of the optimum were missing
	 */
	@Test
	void activeSetReturnsTheLeastSquaresWeights() throws Exception {
		for (var n : new int[]{10, 15, 20}) {
			var r = 0;
			for (var d : noisyInputs(n, 0.2)) {
				var name = "n=" + n + ", input " + (r++);
				var splits = run(NeighborNet.InferenceAlgorithm.ActiveSet, d);
				var found = weightedSplits(splits, n, 0);
				var reference = referenceCircularLeastSquares(d, splits.getCycle());
				assertNotNull(reference, name + ": the reference did not converge");
				var gap = (leastSquaresObjective(d, found) - leastSquaresObjective(d, reference)) / sumOfSquares(d);
				assertTrue(gap > -1e-9, name + ": below the optimum of the reference by " + gap);
				assertTrue(gap < 1e-7, name + ": objective above the optimum by " + gap + " of the sum of squares");
				var largest = Collections.max(reference.values());
				for (var entry : reference.entrySet())
					if (entry.getValue() > 1e-3 * largest) assertTrue(found.containsKey(entry.getKey()), name + ": a split of the optimum is missing, weight " + entry.getValue());
				for (var entry : found.entrySet())
					if (entry.getValue() > 1e-3 * largest)
						assertTrue(reference.getOrDefault(entry.getKey(), 0.0) > 1e-9 * largest, name + ": a split that is zero at the optimum has weight " + entry.getValue());
				assertTrue(maxDifference(found, reference) < 1e-2 * largest, name + ": weights differ by " + maxDifference(found, reference) + ", largest " + largest);
			}
		}
	}

	/**
	 * SplitsTree4 is the method of the earlier program and stops early. It is kept for reproducing old results; this
	 * records how close it comes, not that it is right
	 */
	@Test
	void splitsTree4IsNearTheLeastSquaresWeights() throws Exception {
		var n = 20;
		var r = 0;
		for (var d : noisyInputs(n, 0.2)) {
			var name = "input " + (r++);
			var splits = run(NeighborNet.InferenceAlgorithm.SplitsTree4, d);
			var reference = referenceCircularLeastSquares(d, splits.getCycle());
			assertNotNull(reference, name);
			var gap = (leastSquaresObjective(d, weightedSplits(splits, n, 0)) - leastSquaresObjective(d, reference)) / sumOfSquares(d);
			assertTrue(gap > -1e-9 && gap < 1e-3, name + ": objective above the optimum by " + gap + " of the sum of squares");
		}
	}

	/**
	 * the ordering is a permutation of the taxa and every split is an interval of it
	 */
	@Test
	void theSplitsAreCircular() throws Exception {
		var n = 20;
		for (var inference : NeighborNet.InferenceAlgorithm.values())
			for (var d : noisyInputs(n, 0.2)) {
				var splits = run(inference, d);
				var cycle = splits.getCycle();
				var position = new int[n + 1];
				var seen = new BitSet();
				for (var p = 1; p <= n; p++) {
					seen.set(cycle[p]);
					position[cycle[p]] = p;
				}
				assertEquals(n, seen.cardinality(), "the ordering is not a permutation: " + Arrays.toString(cycle));
				for (var s : splits.getSplits()) {
					var changes = 0;
					for (var p = 1; p <= n; p++) if (s.getA().get(cycle[p]) != s.getA().get(cycle[p % n + 1])) changes++;
					assertEquals(2, changes, inference + ": split " + s.getA() + " is not an interval of the ordering");
					assertTrue(s.getWeight() >= 0, inference + ": weight " + s.getWeight());
				}
			}
	}

	@Test
	void theSameInputGivesTheSameResult() throws Exception {
		for (var inference : NeighborNet.InferenceAlgorithm.values())
			for (var d : noisyInputs(20, 0.2)) {
				var a = run(inference, d);
				var b = run(inference, d);
				assertArrayEquals(a.getCycle(), b.getCycle(), inference.toString());
				assertEquals(weightedSplits(a, 20, -1), weightedSplits(b, 20, -1), inference.toString());
			}
	}

	/**
	 * multiplying all distances by a constant must multiply all weights by it and change nothing else. The cut-off was
	 * once a fixed 0.0001, so that distances of the size of 0.0001 lost all their splits
	 */
	@Test
	void theSplitsDoNotDependOnTheUnitOfTheDistances() throws Exception {
		var n = 20;
		var random = new Random(1);
		for (var r = 0; r < 10; r++) {
			var d = noisyCircular(n, 0.1, random);
			var plain = run(NeighborNet.InferenceAlgorithm.ActiveSet, d);
			for (var scale : new double[]{1e-6, 1e-2, 1e4}) {
				var name = "input " + r + ", scale " + scale;
				var scaled = run(NeighborNet.InferenceAlgorithm.ActiveSet, scaled(d, scale));
				assertArrayEquals(plain.getCycle(), scaled.getCycle(), name);
				assertEquals(plain.getFit(), scaled.getFit(), 1e-6, name + ": fit");
				var a = weightedSplits(plain, n, 0);
				var b = weightedSplits(scaled, n, 0);
				var largest = Collections.max(a.values());
				// a split whose weight is at the cut-off may be on either side of it
				for (var entry : a.entrySet())
					assertEquals(entry.getValue(), b.getOrDefault(entry.getKey(), 0.0) / scale, 1e-3 * largest, name);
				for (var entry : b.entrySet())
					assertEquals(entry.getValue() / scale, a.getOrDefault(entry.getKey(), 0.0), 1e-3 * largest, name);
			}
		}
	}

	/**
	 * a distance that is not a number once ran without end or gave weights that are not numbers
	 */
	@Test
	void distancesThatAreNotFiniteAreRejected() {
		var base = noisy(metric(randomTree(8, new Random(8)), 8), 0.1, new Random(8));
		for (var value : new double[]{Double.NaN, Double.POSITIVE_INFINITY})
			for (var inference : NeighborNet.InferenceAlgorithm.values()) {
				var d = scaled(base, 1);
				d[2][5] = d[5][2] = value;
				assertThrows(Exception.class, () -> run(inference, d), inference + ", " + value);
			}
	}

	@Test
	void smallAndDegenerateInput() throws Exception {
		for (var entry : degenerateDistances().entrySet())
			for (var inference : NeighborNet.InferenceAlgorithm.values()) {
				var name = inference + ", " + entry.getKey();
				var splits = run(inference, entry.getValue());
				for (var s : splits.getSplits()) assertTrue(Double.isFinite(s.getWeight()) && s.getWeight() >= 0, name + ": weight " + s.getWeight());
				assertTrue(Double.isFinite(splits.getFit()), name + ": fit " + splits.getFit());
			}
	}

	/**
	 * a file may name a method that is no longer offered (GradientProjection, APGD, BlockPivot): the default is used
	 */
	@Test
	void anInferenceAlgorithmThatIsNoLongerOfferedFallsBackToTheDefault() throws Exception {
		for (var value : new String[]{"ActiveSet", "SplitsTree4", "GradientProjection", "APGD", "BlockPivot"}) {
			var algorithm = new NeighborNet();
			try (var np = new NexusStreamParser(new StringReader("OPTIONS\n\tInferenceAlgorithm = '" + value + "';\n"))) {
				OptionIO.parseOptions(np, algorithm);
			}
			var expected = value.equals("SplitsTree4") ? NeighborNet.InferenceAlgorithm.SplitsTree4 : NeighborNet.InferenceAlgorithm.ActiveSet;
			assertEquals(expected, algorithm.getOptionInferenceAlgorithm(), value);
		}
	}

	/**
	 * CHARACTERIZATION, recorded 2026-11-19 at c951483c: the buttercups example of Bryant and Huson 2023, 100 taxa,
	 * Hamming distances, default options. The numbers are what the program gave then, checked against the reference to
	 * be within 1e-8 of the optimum for its ordering; they are not derived independently. A difference is a question
	 * ("did we mean to change this?"), to be answered before the numbers are replaced
	 */
	@Test
	void buttercupsExample() throws Exception {
		var file = new File("examples/publications/BryantHuson2023/buttercups-cytochromeC.fasta");
		assumeTrue(file.exists(), "example file not present");
		var taxa = new TaxaBlock();
		var chars = new CharactersBlock();
		new FastAReader().read(new ProgressSilent(), file.getPath(), taxa, chars);
		var distances = new DistancesBlock();
		new HammingDistance().compute(new ProgressSilent(), taxa, chars, distances);
		var n = taxa.getNtax();
		assertEquals(100, n);
		var d = new double[n][n];
		for (var i = 0; i < n; i++) for (var j = 0; j < n; j++) d[i][j] = distances.get(i + 1, j + 1);
		var splits = new SplitsBlock();
		new NeighborNet().compute(new ProgressSilent(), taxa, distances, splits);
		var found = weightedSplits(splits, n, 0);
		assertEquals(10136.125837, leastSquaresObjective(d, found), 1e-3, "sum of squared residuals");
		assertEquals(223, found.keySet().stream().filter(s -> !isTrivial(s, n)).count(), "non-trivial splits");
	}
}
