/*
 * TN93Distance.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances.nucleotide;

import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.Characters2Distances;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

/**
 * simple implementation of TN93 (Tamura–Nei 1993).
 * It is a nucleotide substitution model that estimates the expected number of substitutions per site between two aligned DNA sequences. It improves on simpler models by:
 * allowing two different transition rates (A↔G vs C↔T),
 * a single transversion rate (purine↔pyrimidine),
 * and unequal base frequencies (π_A, π_C, π_G, π_T)
 * Daniel Huson, 9.2025, using ChatGPT5
 */
public class TN93Distance extends Characters2Distances {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock characters, DistancesBlock distancesBlock) throws IOException {
		final int ntax = characters.getNtax();
		distancesBlock.setNtax(ntax);
		progress.setMaximum(ntax);

		var sequences = new String[ntax];
		for (int s = 0; s < ntax; s++) {
			sequences[s] = String.valueOf(characters.getRow0(s));
		}
		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {
				var dist = distance(sequences[s - 1], sequences[t - 1]);
				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);
			}
			progress.incrementProgress();
		}
		FixUndefinedDistances.apply(distancesBlock);
		progress.reportTaskCompleted();

	}

	// Compute TN93 distance between two aligned DNA strings.
	// Returns -1 if not enough comparable sites or if logs become invalid.
	public static double distance(String s1, String s2) {
		if (s1.length() != s2.length())
			throw new IllegalArgumentException("Sequences must have the same length (aligned).");

		// counts for base frequencies per sequence on usable sites
		int a1 = 0, c1 = 0, g1 = 0, t1 = 0, a2 = 0, c2 = 0, g2 = 0, t2 = 0;

		// mismatch category counts
		int ag = 0, ct = 0, tv = 0;  // P1 = ag/L, P2 = ct/L, Q = tv/L
		var compatibleSites = 0;             // number of comparable sites

		for (var i = 0; i < s1.length(); i++) {
			var x = Character.toUpperCase(s1.charAt(i));
			var y = Character.toUpperCase(s2.charAt(i));

			if (!isACGT(x) || !isACGT(y)) continue; // pairwise deletion

			// tally base freqs
			switch (x) {
				case 'A':
					a1++;
					break;
				case 'C':
					c1++;
					break;
				case 'G':
					g1++;
					break;
				case 'T':
					t1++;
					break;
			}
			switch (y) {
				case 'A':
					a2++;
					break;
				case 'C':
					c2++;
					break;
				case 'G':
					g2++;
					break;
				case 'T':
					t2++;
					break;
			}

			if (x != y) {
				if (isPurine(x) && isPurine(y)) ag++;
				else if (isPyrimidine(x) && isPyrimidine(y)) ct++;
				else tv++;
			}
			compatibleSites++;
		}

		if (compatibleSites == 0) return Double.NaN; // nothing comparable

		// average base frequencies over the two sequences
		var piA = (a1 + a2) / (2.0 * compatibleSites);
		var piC = (c1 + c2) / (2.0 * compatibleSites);
		var piG = (g1 + g2) / (2.0 * compatibleSites);
		var piT = (t1 + t2) / (2.0 * compatibleSites);

		var piR = piA + piG;
		var piY = piC + piT;

		// proportions
		var P1 = ag / (double) compatibleSites;  // A<->G transitions
		var P2 = ct / (double) compatibleSites;  // C<->T transitions
		var Q = tv / (double) compatibleSites;  // transversions

		// guard against zero frequencies that would break the formula
		if (piA == 0 || piG == 0 || piC == 0 || piT == 0 || piR == 0 || piY == 0)
			return -1.0;

		// compute the three log terms
		var term1 = 1.0 - (P1 / (piA * piG)) - (Q / (2.0 * piR));
		var term2 = 1.0 - (P2 / (piC * piT)) - (Q / (2.0 * piY));
		var term3 = 1.0 - 2.0 * Q;

		// if divergence is too high, these can go <= 0
		if (term1 <= 0 || term2 <= 0 || term3 <= 0) return Double.NaN;

		return -(piA * piG / piR) * Math.log(term1)
			   - (piC * piT / piY) * Math.log(term2)
			   - 0.5 * Math.log(term3);
	}

	private static boolean isACGT(char b) {
		return b == 'A' || b == 'C' || b == 'G' || b == 'T';
	}

	private static boolean isPurine(char b) {
		return b == 'A' || b == 'G';
	}

	private static boolean isPyrimidine(char b) {
		return b == 'C' || b == 'T';
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return super.isApplicable(taxa, datablock) && datablock.getDataType().isNucleotides();
	}

	@Override
	public String getCitation() {
		return "Tamura & Nei, 1993;K Tamura and M Nei. Estimation of the number of nucleotide substitutions in the control region of mitochondrial DNA in humans and chimpanzees. Molecular Biology and Evolution, 10(3), 512–526, 1993";
	}

	@Override
	public String getShortDescription() {
		return "Calculates distances under the Tamura & Nei 1993 model.";
	}


}
