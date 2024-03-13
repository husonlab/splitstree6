/*
 * EstimateInvariableSites.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.characters.characters2report;


import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.fx.util.ProgramProperties;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

/**
 * Estimates the proportion of invariant sites using capture-recapture
 * Daniel Huson, 2005
 */
public class EstimateInvariableSites extends AnalyzeCharactersBase {
	private final IntegerProperty optionFullTaxaCutoff = new SimpleIntegerProperty(this, "optionFullTaxaCutoff");

	{
		ProgramProperties.track(optionFullTaxaCutoff, 20);
	}

	@Override
	public String getCitation() {
		return "Steel et al 2000; " +
			   "MA Steel, DH Huson, and PJ Lockhart. Invariable site models and their use in phylogeny reconstruction. Sys. Biol. 49(2):225-232, 2000";
	}

	@Override
	public String getShortDescription() {
		return "Estimates the proportion of invariant sites using capture-recapture.";
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var proportion = estimatePropInvariableSites(progress, charactersBlock);
		return "Invariable sites: %,d of %,d (%.1f%%)%n".formatted(Math.round(charactersBlock.getNchar() * proportion), charactersBlock.getNchar(), 100 * proportion);
	}

	/**
	 * Chooses a random (small) subset of size elements in [1...n]
	 *
	 * @return array of size with numbers from [1...n]
	 */
	private static int[] randomSubset(int size, int n, Random random) {
		var s = new int[size];
		for (var i = 0; i < size; i++) {
			var x = random.nextInt(n - i) + 1; //random integer from 1 to n-i
			for (var j = 0; j < i; j++) {      //Make sure that its unique
				if (x >= s[j])
					x++;
			}
			s[i] = x;
		}
		Arrays.sort(s);
		return s;
	}

	/**
	 * Checks to see that, for site m, the taxa in q are not missing, gaps, etc.
	 *
	 * @param q array of taxa ids
	 * @param m site
	 * @return true iff all not missing, not gaps, and site not masked
	 */
	private static boolean goodSite(CharactersBlock block, int[] q, int m) {
		for (var aQ : q) {
			var ch = block.get(aQ, m);
			if (ch == block.getMissingCharacter())
				return false;
			if (ch == block.getGapCharacter())
				return false;
		}
		return true;
	}

	/**
	 * Computes v statistic (Steel etal) for the quartet q
	 *
	 * @return v score
	 */

	private static double vscore(int[] q, CharactersBlock block) {
		final var nsites = block.getNchar();
		var ngood = 0; //Number of sites without gaps in all four

		int f_ij_kl = 0, f_ik_jl = 0, f_il_jk = 0, f_ij = 0, f_ik = 0, f_il = 0, f_jk = 0, f_jl = 0, f_kl = 0;

		int nconst = 0;

		final char[] s = new char[4];

		for (int m = 1; m <= nsites; m++) {
			if (!goodSite(block, q, m))
				continue;
			ngood++;

			for (int a = 0; a < 4; a++)
				s[a] = block.get(q[a], m);


			if (s[0] != s[1])
				f_ij++;
			if (s[0] != s[2])
				f_ik++;
			if (s[0] != s[3])
				f_il++;
			if (s[1] != s[2])
				f_jk++;
			if (s[1] != s[3])
				f_jl++;
			if (s[2] != s[3])
				f_kl++;
			if ((s[0] != s[1]) && (s[2] != s[3]))
				f_ij_kl++;
			if ((s[0] != s[2]) && (s[1] != s[3]))
				f_ik_jl++;
			if ((s[0] != s[3]) && (s[1] != s[2]))
				f_il_jk++;
			if (s[0] == s[1] && s[0] == s[2] && s[0] == s[3])
				nconst++;
		}

		if (ngood == 0)
			return 100.0;   //Returns an impossible amount - says choose another.

		double v = 1.0 - (double) nconst / ngood;
		if (f_ij_kl > 0)
			v = Math.max(v, (double) f_ij * f_kl / f_ij_kl / ngood);
		if (f_ik_jl > 0)
			v = Math.max(v, (double) f_ik * f_jl / f_ik_jl / ngood);
		if (f_il_jk > 0)
			v = Math.max(v, (double) f_il * f_jk / f_il_jk / ngood);

		v = Math.min(v, 1.0);
		//System.err.println(q+"\tv = "+ v);
		return v;
	}

	/**
	 * Computes the proportion of Invariance sites using Steel et al.'s method
	 *
	 * @return proportion assumed invariant
	 */
	public double estimatePropInvariableSites(ProgressListener progress, CharactersBlock chars) throws CanceledException {
		final int nchar = chars.getNtax();
		final int maxsample = (nchar * (nchar - 1) * (nchar - 2) * (nchar - 3)) / 24;

		double vsum = 0.0;
		int count = 0;

		if (nchar > getOptionFullTaxaCutoff()) {
			//Sampling          - we do a minimum of 1000, and stop once |sd| is less than 0.05 |mean|
			progress.setMaximum(2000);
			progress.setProgress(0);

			final Random random = new Random();
			int[] q;
			double sum2 = 0.0;
			boolean done = false;
			int iter = 0;

			while (!done) {
				iter++;
				q = randomSubset(4, nchar, random);
				double v = vscore(q, chars);
				if (v > 1.0)
					continue; //Invalid quartet.
				vsum += v;
				sum2 += v * v;
				count++;
				if (count > 1000) {
					//Evaluate how good the stdev is.
					double mean = vsum / count;
					double var = sum2 / count - mean * mean;
					double sd = Math.sqrt(var);
					if (Math.abs(sd / mean) < 0.05)
						done = true;
					// System.err.println("Mean = " + mean + " sd = " + sd);
				}
				if (iter > maxsample) {
					done = true; //Safety check to prevent infinite loop
				}
				progress.incrementProgress();
			}
		} else { //Exact count
			progress.setMaximum(nchar);
			progress.setProgress(0);
			for (int i = 1; i <= nchar; i++) {
				for (int j = i + 1; j <= nchar; j++) {
					for (int k = j + 1; k <= nchar; k++) {
						for (int l = k + 1; l <= nchar; l++) {
							int[] q = new int[4];
							q[0] = i;
							q[1] = j;
							q[2] = k;
							q[3] = l;
							vsum += vscore(q, chars);
							count++;
						}
					}
				}
				progress.incrementProgress();
			}
		}

		return vsum / count;
	}

	public int getOptionFullTaxaCutoff() {
		return optionFullTaxaCutoff.get();
	}

	public IntegerProperty optionFullTaxaCutoffProperty() {
		return optionFullTaxaCutoff;
	}

	public void setOptionFullTaxaCutoff(int optionFullTaxaCutoff) {
		this.optionFullTaxaCutoff.set(optionFullTaxaCutoff);
	}
}
