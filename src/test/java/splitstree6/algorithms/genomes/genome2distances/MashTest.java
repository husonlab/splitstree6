/*
 *  MashTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.genomes.genome2distances;

import jloda.kmers.GenomeDistanceType;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.algorithms.genomes.genomes2genomes.GenomesTaxaFilter;
import splitstree6.data.DistancesBlock;
import splitstree6.data.GenomesBlock;
import splitstree6.data.GenomesFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Genome;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * Mash distances (Ondov et al 2016) between genomes with a known proportion of changed positions
 */
public class MashTest {
	private static Genome genome(String name, byte[] sequence) {
		var genome = new Genome();
		genome.setName(name);
		genome.getParts().add(new Genome.GenomePart(name, sequence, null, 0, sequence.length));
		genome.setLength(sequence.length);
		return genome;
	}

	private static byte[] random(int length, Random random) {
		var sequence = new byte[length];
		for (var i = 0; i < length; i++) sequence[i] = (byte) "ACGT".charAt(random.nextInt(4));
		return sequence;
	}

	private static byte[] mutate(byte[] sequence, double proportion, Random random) {
		var result = sequence.clone();
		for (var i = 0; i < result.length; i++)
			if (random.nextDouble() < proportion) {
				byte other;
				do other = (byte) "ACGT".charAt(random.nextInt(4)); while (other == result[i]);
				result[i] = other;
			}
		return result;
	}

	/**
	 * four genomes: a random one, a copy, and copies in which 2 and 6 percent of the positions are changed
	 */
	private static GenomesBlock genomes() {
		var random = new Random(111);
		var first = random(200000, random);
		var block = new GenomesBlock();
		block.getFormat().setCharactersType(GenomesFormat.CharactersType.dna);
		block.getGenomes().add(genome("t1", first));
		block.getGenomes().add(genome("t2", first.clone()));
		block.getGenomes().add(genome("t3", mutate(first, 0.02, random)));
		block.getGenomes().add(genome("t4", mutate(first, 0.06, random)));
		return block;
	}

	/**
	 * the Mash distance estimates the number of changes per position: -ln(1-p) for a proportion p of changed positions
	 */
	@Test
	void mashDistanceEstimatesTheProportionOfChanges() throws Exception {
		var algorithm = new Mash();
		var distances = new DistancesBlock();
		algorithm.compute(new ProgressSilent(), taxa(4), genomes(), distances);
		assertEquals(4, distances.getNtax());
		assertEquals(0, distances.get(1, 2), 1e-12);
		assertEquals(-Math.log(1 - 0.02), distances.get(1, 3), 0.004);
		assertEquals(-Math.log(1 - 0.06), distances.get(1, 4), 0.008);
		assertEquals(distances.get(1, 3), distances.get(2, 3), 1e-12);
		assertEquals(-Math.log(1 - 0.02) - Math.log(1 - 0.06), distances.get(3, 4), 0.012);
		for (var i = 1; i <= 4; i++) {
			assertEquals(0, distances.get(i, i));
			for (var j = 1; j <= 4; j++) assertEquals(distances.get(i, j), distances.get(j, i));
		}
		// the same again gives the same
		var again = new DistancesBlock();
		new Mash().compute(new ProgressSilent(), taxa(4), genomes(), again);
		for (var i = 1; i <= 4; i++) for (var j = 1; j <= 4; j++) assertEquals(distances.get(i, j), again.get(i, j));
	}

	/**
	 * one minus the Jaccard index of the two sets of k-mers; for a proportion p of changes about a fraction w = (1-p)^k of
	 * the k-mers of each genome is shared, which gives the index w/(2-w)
	 */
	@Test
	void jaccardDistance() throws Exception {
		var algorithm = new Mash();
		algorithm.setOptionDistances(GenomeDistanceType.JaccardIndex);
		var distances = new DistancesBlock();
		algorithm.compute(new ProgressSilent(), taxa(4), genomes(), distances);
		assertEquals(0, distances.get(1, 2), 1e-12);
		var w = Math.pow(1 - 0.02, algorithm.getOptionKMerSize());
		assertEquals(1 - w / (2 - w), distances.get(1, 3), 0.03);
	}

	@Test
	void unrelatedGenomes() throws Exception {
		var random = new Random(112);
		var block = new GenomesBlock();
		block.getFormat().setCharactersType(GenomesFormat.CharactersType.dna);
		block.getGenomes().add(genome("t1", random(100000, random)));
		block.getGenomes().add(genome("t2", random(100000, random)));
		var distances = new DistancesBlock();
		new Mash().compute(new ProgressSilent(), taxa(2), block, distances);
		assertTrue(distances.get(1, 2) >= 0.3 && Double.isFinite(distances.get(1, 2)), "distance " + distances.get(1, 2));
	}

	@Test
	void taxaFilter() throws Exception {
		var genomes = genomes();
		var modified = new TaxaBlock();
		modified.addTaxaByNames(List.of("t4", "t2"));
		var child = new GenomesBlock();
		new GenomesTaxaFilter().filter(new ProgressSilent(), taxa(4), modified, genomes, child);
		assertEquals(2, child.getNGenomes());
		assertEquals("t4", child.getGenome(1).getName());
		assertEquals("t2", child.getGenome(2).getName());
		var all = new GenomesBlock();
		new GenomesTaxaFilter().filter(new ProgressSilent(), taxa(4), taxa(4), genomes, all);
		assertEquals(4, all.getNGenomes());
	}
}
