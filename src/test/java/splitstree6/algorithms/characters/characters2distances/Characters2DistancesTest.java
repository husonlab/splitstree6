/*
 *  Characters2DistancesTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import org.junit.jupiter.api.Test;
import splitstree6.algorithms.characters.characters2distances.nucleotide.*;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.parts.CharactersType;
import splitstree6.models.nucleotideModels.*;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the methods that compute distances from characters, on input whose distance is known.
 * Model data: two sequences whose site-pattern frequencies are exactly those of a model after time t, so that a
 * consistent method must return t, up to the rounding of the frequencies to counts.
 */
public class Characters2DistancesTest {
	static final int SITES = 200000;
	/**
	 * the counts of the 16 site patterns are rounded to whole sites, which moves a distance by about 1e-5
	 */
	static final double TOLERANCE = 5e-4;

	static final double[] SKEWED = {0.1, 0.2, 0.3, 0.4};
	static final double[] PURINES_BALANCED = {0.3, 0.2, 0.2, 0.3};
	static final double[] TIMES = {0.05, 0.3, 1.0};

	static CharactersBlock dna(double[][] divergence) {
		return characters(CharactersType.DNA, "acgt", sequences(divergence, SITES));
	}

	static double distance(Characters2Distances algorithm, CharactersBlock chars) throws Exception {
		return run(algorithm, chars).get(1, 2);
	}

	static <T extends Nucleotides2DistancesBase> T configured(T algorithm, boolean useML, double[] baseFrequencies) {
		algorithm.setOptionUseML_Distances(useML);
		if (baseFrequencies != null) algorithm.setOptionBaseFrequencies(baseFrequencies);
		return algorithm;
	}

	// ---------- each model against data generated from itself ----------

	@Test
	void jukesCantorReturnsTheTimeOfTheModel() throws Exception {
		for (var t : TIMES)
			for (var useML : new boolean[]{false, true})
				assertEquals(t, distance(configured(new JukesCantorDistance(), useML, null), dna(divergence(new JCmodel(), t))), TOLERANCE, "t=" + t + " ML=" + useML);
	}

	@Test
	void k2pReturnsTheTimeOfTheModel() throws Exception {
		for (var t : TIMES)
			for (var useML : new boolean[]{false, true})
				assertEquals(t, distance(configured(new K2PDistance(), useML, null), dna(divergence(new K2Pmodel(2.0), t))), TOLERANCE, "t=" + t + " ML=" + useML);
	}

	@Test
	void f81ReturnsTheTimeOfTheModel() throws Exception {
		for (var t : TIMES)
			for (var useML : new boolean[]{false, true})
				assertEquals(t, distance(configured(new F81Distance(), useML, SKEWED), dna(divergence(new F81model(SKEWED), t))), TOLERANCE, "t=" + t + " ML=" + useML);
	}

	@Test
	void f84ReturnsTheTimeOfTheModel() throws Exception {
		for (var t : TIMES)
			for (var useML : new boolean[]{false, true})
				assertEquals(t, distance(configured(new F84Distance(), useML, PURINES_BALANCED), dna(divergence(new F84Model(PURINES_BALANCED, 2.0), t))), TOLERANCE, "t=" + t + " ML=" + useML);
	}

	/**
	 * the purine and pyrimidine terms of the F84 rate matrix were once exchanged, which broke detailed balance whenever
	 * the two frequencies differ, so that the model could not be constructed
	 */
	@Test
	void f84AcceptsUnequalPurineAndPyrimidineFrequencies() throws Exception {
		for (var pi : new double[][]{{0.25, 0.25, 0.25, 0.25}, {0.3, 0.2, 0.3, 0.2}, SKEWED})
			assertDoesNotThrow(() -> new F84Model(pi, 2.0));
		for (var t : TIMES)
			assertEquals(t, distance(configured(new F84Distance(), true, SKEWED), dna(divergence(new F84Model(SKEWED, 2.0), t))), TOLERANCE, "t=" + t);
	}

	@Test
	void hky85ReturnsTheTimeOfTheModel() throws Exception {
		for (var t : TIMES)
			assertEquals(t, distance(configured(new HKY85Distance(), true, SKEWED), dna(divergence(new HKY85model(SKEWED, 2.0), t))), TOLERANCE, "t=" + t);
	}

	@Test
	void gtrReturnsTheTimeOfTheModel() throws Exception {
		var symmetric = new double[][]{{0, 1, 4, 2}, {1, 0, 3, 6}, {4, 3, 0, 1}, {2, 6, 1, 0}};
		var rates = new double[4][4];
		for (var a = 0; a < 4; a++) for (var b = 0; b < 4; b++) rates[a][b] = symmetric[a][b] * SKEWED[b];
		for (var t : TIMES)
			for (var useML : new boolean[]{false, true}) {
				var algorithm = configured(new GTRDistance(), useML, SKEWED);
				algorithm.setOptionRateMatrix(rates);
				assertEquals(t, distance(algorithm, dna(divergence(new GTRmodel(rates, SKEWED), t))), TOLERANCE, "t=" + t + " ML=" + useML);
			}
	}

	/**
	 * the exact GTR distance takes the base frequencies from the data. With frequencies fixed at 1/4 it returned
	 * 0.2323, 0.6742 and 1.2266 here, and 0.1218 for two identical sequences
	 */
	@Test
	void exactGtrDistanceNeedsNoBaseFrequencies() throws Exception {
		for (var t : new double[]{0.1, 0.5, 1.0})
			assertEquals(t, distance(new GTRDistance(), dna(divergence(new HKY85model(SKEWED, 2.0), t))), TOLERANCE, "t=" + t);
		var same = "a".repeat(100) + "c".repeat(200) + "g".repeat(300) + "t".repeat(400);
		assertEquals(0, distance(new GTRDistance(), characters(CharactersType.DNA, "acgt", same, same, same)), 1e-12);
	}

	@Test
	void tn93ReturnsTheTimeOfHky85Data() throws Exception {
		for (var t : TIMES)
			assertEquals(t, distance(new TN93Distance(), dna(divergence(new HKY85model(SKEWED, 2.0), t))), TOLERANCE, "t=" + t);
	}

	@Test
	void tn93ReadsRnaAsDna() throws Exception {
		var rows = sequences(divergence(new HKY85model(SKEWED, 2.0), 0.2), SITES);
		var rna = characters(CharactersType.RNA, "acgu", rows[0].replace('t', 'u'), rows[1].replace('t', 'u'), rows[2].replace('t', 'u'));
		assertTrue(new TN93Distance().isApplicable(taxa(3), rna));
		assertEquals(0.2, distance(new TN93Distance(), rna), TOLERANCE);
	}

	@Test
	void logDetReturnsTheTimeOfJukesCantorData() throws Exception {
		for (var t : TIMES)
			assertEquals(t, distance(new LogDet(), dna(divergence(new JCmodel(), t))), TOLERANCE, "t=" + t);
	}

	/**
	 * under Jukes-Cantor the proportion of differing sites after time t is 3/4 (1 - exp(-4t/3))
	 */
	@Test
	void pDistanceIsTheProportionOfDifferingSites() throws Exception {
		for (var t : TIMES)
			assertEquals(0.75 * (1 - Math.exp(-4 * t / 3)), distance(new PDistance(), dna(divergence(new JCmodel(), t))), 1e-5, "t=" + t);
	}

	@Test
	void hammingDistanceCountsDifferingSites() throws Exception {
		var chars = characters(CharactersType.DNA, "acgt", "acgtacgt", "acgtacga", "ccgtacga");
		var normalized = new HammingDistance();
		normalized.setOptionNormalize(true);
		var d = run(normalized, chars);
		assertEquals(1.0 / 8, d.get(1, 2), 1e-12);
		assertEquals(2.0 / 8, d.get(1, 3), 1e-12);
		assertEquals(1.0 / 8, d.get(2, 3), 1e-12);
		assertEquals(d.get(1, 3), d.get(3, 1), 0);
		assertEquals(0, d.get(2, 2), 0);
	}

	// ---------- rate variation across sites ----------

	/**
	 * Jukes-Cantor with gamma-distributed rates, shape alpha: the two states at a site are equal with probability
	 * 1/4 + 3/4 (1 + 4t/(3 alpha))^(-alpha). The ML distance once used the exponent -1-alpha and returned 0.1230 and
	 * 0.4896 for t = 0.1 and 0.5
	 */
	@Test
	void gammaDistributedRates() throws Exception {
		var alpha = 0.5;
		for (var t : new double[]{0.1, 0.5}) {
			var chars = dna(divergenceJukesCantor(0.25 + 0.75 * Math.pow(1.0 + 4.0 * t / (3.0 * alpha), -alpha)));
			for (var useML : new boolean[]{false, true}) {
				var algorithm = configured(new K2PDistance(), useML, null);
				algorithm.setOptionTsTvRatio(0.5); // which makes K2P the Jukes-Cantor model
				algorithm.setOptionGamma(alpha);
				assertEquals(t, distance(algorithm, chars), TOLERANCE, "t=" + t + " ML=" + useML);
			}
		}
	}

	/**
	 * Jukes-Cantor with a proportion p of invariable sites: the states are equal with probability
	 * p + (1-p)(1/4 + 3/4 exp(-4t/3)), and the expected number of changes per site is (1-p) t.
	 * The exact distance once returned t
	 */
	@Test
	void invariableSites() throws Exception {
		var p = 0.3;
		List<Supplier<Nucleotides2DistancesBase>> methods = List.of(JukesCantorDistance::new, K2PDistance::new, F81Distance::new, GTRDistance::new);
		for (var t : new double[]{0.1, 0.5}) {
			var chars = dna(divergenceJukesCantor(p + (1 - p) * (0.25 + 0.75 * Math.exp(-4.0 * t / 3.0))));
			for (var method : methods)
				for (var useML : new boolean[]{false, true}) {
					var algorithm = configured(method.get(), useML, null);
					if (algorithm instanceof K2PDistance k2p) k2p.setOptionTsTvRatio(0.5);
					algorithm.setOptionPropInvariableSites(p);
					assertEquals((1 - p) * t, distance(algorithm, chars), TOLERANCE, algorithm.getClass().getSimpleName() + " t=" + t + " ML=" + useML);
				}
		}
	}

	@Test
	void proteinMlWithLargeGammaApproachesEqualRates() throws Exception {
		var chars = randomProteins("arndcqeghilkmfpstwyv");
		var equalRates = run(new ProteinMLDistance(), chars);
		var largeGamma = new ProteinMLDistance();
		largeGamma.setOptionGamma(1000);
		var d = run(largeGamma, chars);
		for (var s = 1; s <= chars.getNtax(); s++)
			for (var t = s + 1; t <= chars.getNtax(); t++)
				assertEquals(equalRates.get(s, t), d.get(s, t), 0.01 * equalRates.get(s, t), s + "," + t);
	}

	// ---------- the order of the states in the block ----------

	/**
	 * six protein sequences of 300 sites, each a copy of the first with a different proportion of sites redrawn
	 */
	static CharactersBlock randomProteins(String symbols) {
		var acids = "arndcqeghilkmfpstwyv";
		var random = new Random(1);
		var base = new StringBuilder();
		for (var i = 0; i < 300; i++) base.append(acids.charAt(random.nextInt(20)));
		var rows = new String[6];
		for (var r = 0; r < rows.length; r++) {
			var buf = new StringBuilder(base);
			for (var i = 0; i < 300; i++) if (random.nextDouble() < 0.04 * (r + 1)) buf.setCharAt(i, acids.charAt(random.nextInt(20)));
			rows[r] = buf.toString();
		}
		return characters(CharactersType.Protein, symbols, rows);
	}

	/**
	 * the readers of FASTA, phylip, Clustal, MSF and Stockholm list the states in alphabetical order, the models index
	 * them in their own. The distances must not depend on the order in the block; Protein ML once did, on every pair
	 */
	@Test
	void proteinDistancesDoNotDependOnTheOrderOfTheStates() throws Exception {
		for (Supplier<Characters2Distances> method : List.<Supplier<Characters2Distances>>of(ProteinMLDistance::new, LogDet::new, HammingDistance::new))
			assertSameDistances(run(method.get(), randomProteins("acdefghiklmnpqrstvwy")), run(method.get(), randomProteins("arndcqeghilkmfpstwyv")), method.get().getClass().getSimpleName());
	}

	/**
	 * an alignment in which no g occurs is read from FASTA with the three states act. JC, K2P, F81, F84, HKY85 and GTR
	 * once failed on it with an index out of bounds
	 */
	@Test
	void nucleotideDistancesAcceptAnAlignmentThatLacksABase() throws Exception {
		var random = new Random(1);
		var base = new StringBuilder();
		for (var i = 0; i < 2000; i++) base.append("act".charAt(random.nextInt(3)));
		var rows = new String[4];
		for (var r = 0; r < 4; r++) {
			var buf = new StringBuilder(base);
			for (var i = 0; i < 2000; i++) if (random.nextDouble() < 0.05 * (r + 1)) buf.setCharAt(i, "act".charAt(random.nextInt(3)));
			rows[r] = buf.toString();
		}
		List<Supplier<Characters2Distances>> methods = List.of(JukesCantorDistance::new, K2PDistance::new, F81Distance::new, F84Distance::new,
				HKY85Distance::new, GTRDistance::new, TN93Distance::new, HammingDistance::new);
		for (var method : methods) {
			var name = method.get().getClass().getSimpleName();
			var threeStates = run(method.get(), characters(CharactersType.DNA, "act", rows));
			assertSameDistances(threeStates, run(method.get(), characters(CharactersType.DNA, "acgt", rows)), name);
			for (var s = 1; s <= 4; s++)
				for (var t = s + 1; t <= 4; t++)
					assertTrue(threeStates.get(s, t) > 0 && (threeStates.get(s, t) < 1 || name.equals("HammingDistance")), name + ": a pair has no defined distance");
		}
	}

	static void assertSameDistances(DistancesBlock a, DistancesBlock b, String message) {
		assertEquals(a.getNtax(), b.getNtax());
		for (var s = 1; s <= a.getNtax(); s++)
			for (var t = 1; t <= a.getNtax(); t++)
				assertEquals(a.get(s, t), b.get(s, t), 1e-9, message + " " + s + "," + t);
	}

	// ---------- base frequencies ----------

	/**
	 * the choice fromChars once acted only through a listener in the user interface, so that a run without one used
	 * frequencies of 1/4 and returned 0.4853 here
	 */
	@Test
	void baseFrequenciesAreEstimatedFromTheCharactersWhenAskedFor() throws Exception {
		var chars = dna(divergence(new F81model(SKEWED), 0.5));
		var fromChars = new F81Distance();
		fromChars.setOptionSetBaseFrequencies(Nucleotides2DistancesBase.SetParameters.fromChars);
		assertEquals(0.5, distance(fromChars, chars), TOLERANCE);
	}

	// ---------- presence and absence ----------

	/**
	 * 2 characters present in both, 1 in the first only, 1 in the second only, 3 in neither:
	 * Jaccard 1 - 2/4, Dice 1 - 4/6. Jaccard once returned the Dice value
	 */
	@Test
	void jaccardAndDice() throws Exception {
		var chars = characters(CharactersType.Standard, "01", "1110000", "1101000", "1110000");
		assertEquals(0.5, distance(new JaccardDistance(), chars), 1e-12);
		assertEquals(1.0 / 3, distance(new DiceDistance(), chars), 1e-12);
	}

	/**
	 * two genomes of 100 genes that share k: the distance grows as k falls, and sharing none is not closer than sharing one.
	 * Sharing none once gave 1.0, less than the 9.2 of sharing one
	 */
	@Test
	void geneContentDistanceGrowsAsFewerGenesAreShared() throws Exception {
		var shared = new int[]{50, 10, 1, 0};
		var rows = new String[shared.length + 1];
		rows[0] = "1".repeat(100) + "0".repeat(100);
		for (var i = 0; i < shared.length; i++) rows[i + 1] = "0".repeat(100 - shared[i]) + "1".repeat(100) + "0".repeat(shared[i]);
		var d = run(new GeneContentDistance(), characters(CharactersType.Standard, "01", rows));
		for (var i = 1; i < shared.length; i++)
			assertTrue(d.get(1, i + 2) > d.get(1, i + 1), "sharing " + shared[i] + " gives " + d.get(1, i + 2) + ", sharing " + shared[i - 1] + " gives " + d.get(1, i + 1));
		var identical = "1".repeat(100) + "0".repeat(100);
		assertEquals(0, distance(new GeneContentDistance(), characters(CharactersType.Standard, "01", identical, identical, identical)), 1e-12);
	}

	// ---------- base composition ----------

	/**
	 * ten sites, one of which differs: base frequencies (0.9, 0.1) against (1, 0), which differ by 0.1 + 0.1.
	 * The last site was once left out, giving 0 and 2/9
	 */
	@Test
	void baseFrequencyDistanceCountsEverySite() throws Exception {
		assertEquals(0.2, distance(new BaseFreqDistance(), characters(CharactersType.DNA, "acgt", "aaaaaaaaac", "aaaaaaaaaa", "aaaaaaaaaa")), 1e-12, "last site");
		assertEquals(0.2, distance(new BaseFreqDistance(), characters(CharactersType.DNA, "acgt", "caaaaaaaaa", "aaaaaaaaaa", "aaaaaaaaaa")), 1e-12, "first site");
	}

	// ---------- input for which a method has a special case ----------

	static final List<Supplier<Characters2Distances>> NUCLEOTIDE_METHODS = List.of(JukesCantorDistance::new, K2PDistance::new, F81Distance::new, F84Distance::new,
			HKY85Distance::new, GTRDistance::new, TN93Distance::new, LogDet::new, HammingDistance::new, PDistance::new, BaseFreqDistance::new);

	static final List<Supplier<Characters2Distances>> BINARY_METHODS = List.of(DiceDistance::new, JaccardDistance::new, GeneContentDistance::new, GeneSharingDistance::new,
			NeiLiRestrictionDistance::new, UpholtRestrictionDistance::new, HammingDistance::new);

	static void assertFiniteAndSymmetric(DistancesBlock d, String message) {
		for (var s = 1; s <= d.getNtax(); s++) {
			assertEquals(0, d.get(s, s), 0, message + ": diagonal");
			for (var t = 1; t <= d.getNtax(); t++) {
				assertTrue(Double.isFinite(d.get(s, t)) && d.get(s, t) >= 0, message + ": d(" + s + "," + t + ")=" + d.get(s, t));
				assertEquals(d.get(s, t), d.get(t, s), 0, message + ": symmetry");
			}
		}
	}

	@Test
	void identicalSequencesHaveDistanceZero() throws Exception {
		var seq = "acgtacgtacgtacgtacgtacgtaaccggtt";
		for (var method : NUCLEOTIDE_METHODS) {
			var name = method.get().getClass().getSimpleName();
			var d = run(method.get(), characters(CharactersType.DNA, "acgt", seq, seq, seq));
			assertFiniteAndSymmetric(d, name);
			assertEquals(0, d.get(1, 2), 1e-5, name); // HKY85 is found by numerical optimisation
			assertFalse(1 / d.get(1, 2) < 0, name + " returns -0.0");
		}
		for (var method : BINARY_METHODS) {
			var name = method.get().getClass().getSimpleName();
			var d = run(method.get(), characters(CharactersType.Standard, "01", "1100110011", "1100110011", "1100110011"));
			assertFiniteAndSymmetric(d, name);
			assertEquals(0, d.get(1, 2), 1e-9, name);
		}
	}

	/**
	 * a sequence of gaps, or a genome without genes, shares no site with any other: every method must return a matrix of
	 * finite numbers. BaseFreqDistance once returned NaN
	 */
	@Test
	void aSequenceWithoutDataGivesFiniteDistances() throws Exception {
		var seq = "acgtacgtacgtacgtacgtacgtaaccggtt";
		var other = "acgtacgaacgtacgtccgtacgtaaccggta";
		for (var method : NUCLEOTIDE_METHODS)
			assertFiniteAndSymmetric(run(method.get(), characters(CharactersType.DNA, "acgt", seq, "-".repeat(seq.length()), other)), method.get().getClass().getSimpleName());
		for (var method : BINARY_METHODS)
			assertFiniteAndSymmetric(run(method.get(), characters(CharactersType.Standard, "01", "1100110011", "0000000000", "1000110010")), method.get().getClass().getSimpleName());
	}

	@Test
	void upperAndLowerCaseGiveTheSameDistances() throws Exception {
		var seq = "acgtacgtacgtacgtacgtacgtaaccggtt";
		var other = "acgtacgaacgtacgtccgtacgtaaccggta";
		for (var method : NUCLEOTIDE_METHODS) {
			var upper = characters(CharactersType.DNA, "acgt", seq, other, seq);
			for (var t = 1; t <= 3; t++)
				for (var i = 1; i <= seq.length(); i++) upper.set(t, i, Character.toUpperCase(upper.get(t, i)));
			assertSameDistances(run(method.get(), characters(CharactersType.DNA, "acgt", seq, other, seq)), run(method.get(), upper), method.get().getClass().getSimpleName());
		}
	}
}
