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
 * Daniel Huson, 9.2025, using ChatGPT5, 4.2026 corrected using Claude OPus 4.7
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

		int a1 = 0, c1 = 0, g1 = 0, t1 = 0;
		int a2 = 0, c2 = 0, g2 = 0, t2 = 0;
		int ag = 0, ct = 0, tv = 0;
		int compatibleSites = 0;

		for (int i = 0; i < s1.length(); i++) {
			char x = Character.toUpperCase(s1.charAt(i));
			char y = Character.toUpperCase(s2.charAt(i));
			if (!isACGT(x) || !isACGT(y)) continue;

			switch (x) {
				case 'A' -> a1++;
				case 'C' -> c1++;
				case 'G' -> g1++;
				case 'T' -> t1++;
			}
			switch (y) {
				case 'A' -> a2++;
				case 'C' -> c2++;
				case 'G' -> g2++;
				case 'T' -> t2++;
			}

			if (x != y) {
				if (isPurine(x) && isPurine(y)) ag++;
				else if (isPyrimidine(x) && isPyrimidine(y)) ct++;
				else tv++;
			}
			compatibleSites++;
		}

		if (compatibleSites == 0) return Double.NaN;

		double piA = (a1 + a2) / (2.0 * compatibleSites);
		double piC = (c1 + c2) / (2.0 * compatibleSites);
		double piG = (g1 + g2) / (2.0 * compatibleSites);
		double piT = (t1 + t2) / (2.0 * compatibleSites);
		double piR = piA + piG;
		double piY = piC + piT;

		if (piA == 0 || piC == 0 || piG == 0 || piT == 0 || piR == 0 || piY == 0)
			return Double.NaN;

		double P1 = ag / (double) compatibleSites;
		double P2 = ct / (double) compatibleSites;
		double Q = tv / (double) compatibleSites;

		// Standard TN93 (Tamura & Nei 1993)
		double w1 = 1.0 - piR * P1 / (2.0 * piA * piG) - Q / (2.0 * piR);
		double w2 = 1.0 - piY * P2 / (2.0 * piC * piT) - Q / (2.0 * piY);
		double w3 = 1.0 - Q / (2.0 * piR * piY);

		if (w1 <= 0 || w2 <= 0 || w3 <= 0) return Double.NaN;

		double k1 = 2.0 * piA * piG / piR;
		double k2 = 2.0 * piC * piT / piY;
		double k3 = 2.0 * (piR * piY
						   - piA * piG * piY / piR
						   - piC * piT * piR / piY);

		return -k1 * Math.log(w1) - k2 * Math.log(w2) - k3 * Math.log(w3);
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
