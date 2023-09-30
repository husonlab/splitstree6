/*
 * NeiMiller.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;

import java.io.IOException;

/**
 * Computes the Nei and Miller (1990) distance from a set of characters
 *
 * @author David Bryant, 2008
 */
public class NeiMiller extends Characters2Distances {
	@Override
	public String getCitation() {
		return "Nei and Miller 1990; M. Nei and J.C. Miller. " +
			   "A simple method for estimating average number of nucleotide substitutions within and between populations from restriction data. " +
			   "Genetics, 125:873–879, 1990.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		int nchar = charactersBlock.getNchar();
		int ntax = charactersBlock.getNtax();

		distancesBlock.setNtax(ntax);


		//distancesBlock.setNtax(ntax);

		boolean warned_sij = false, warned_dhij = false, warned_dist = false;

		// Determine enzyme classes etc:

		double[] class_value = new double[nchar + 1];     // Value for given enzyme class
		int[] class_size = new int[nchar + 1];                  // number of positions for class
		int[] char2class = new int[nchar + 1];        // Maps characters to enzyme classes
		int num_classes = 0;                    // Number of different classes

		final int maxProgress = 5 * taxaBlock.getNtax() + charactersBlock.getNchar();

		progress.setTasks("NeiMiller distance", "Init.");
		progress.setMaximum(maxProgress);

		for (int c = 1; c <= nchar; c++) {
			//if (!characters.isMasked(c)) {
			boolean found = false;
			for (int k = 1; k <= num_classes; k++) {
				if (class_value[k] == charactersBlock.getCharacterWeight(c)) {// belongs to class already encountered
					char2class[c] = k;
					class_size[k]++;
					found = true;
					break;
				}
			}
			if (!found) // new class
			{
				++num_classes;
				char2class[c] = num_classes;
				class_value[num_classes] = charactersBlock.getCharacterWeight(c);
				class_size[num_classes] = 1;
			}
			//}

			//doc.notifySetProgress(100 * c / maxProgress);
			progress.incrementProgress();
		}

		// Compute mij_k:

		final int[][][] mij_k = new int[ntax + 1][ntax + 1][num_classes + 1];

		for (int i = 1; i <= ntax; i++) {
			for (int j = i; j <= ntax; j++) {
				for (int c = 1; c <= nchar; c++) {
					//if (!characters.isMasked(c)) {
					if (charactersBlock.get(i, c) == '1' && charactersBlock.get(j, c) == '1') {
						mij_k[i][j][char2class[c]]++;
					}
					//}
				}
			}

			//doc.notifySetProgress((characters.getNchar() + i) * 100 / maxProgress);
			progress.incrementProgress();
		}

		// Compute sij_k  (equation 2):

		final double[][][] sij_k = new double[ntax + 1][ntax + 1][num_classes + 1];
		for (int i = 1; i <= ntax; i++) {
			for (int j = i + 1; j <= ntax; j++) {
				for (int k = 1; k <= num_classes; k++) {
					double bot = mij_k[i][i][k] + mij_k[j][j][k];

					if (bot != 0)
						sij_k[i][j][k] = (2 * mij_k[i][j][k]) / bot;
					else {
						if (!warned_sij) {
							System.err.println("Nei_Miller: denominator zero in equation (2)");
							warned_sij = true;
						}
						sij_k[i][j][k] = 100000;
					}
				}
			}

			//doc.notifySetProgress((characters.getNchar() + ntax + i) * 100 / maxProgress);
			progress.incrementProgress();
		}

		// Compute dhij_k (i.e. dij_k_hat in equation (3)):

		final double[][][] dhij_k = new double[ntax + 1][ntax + 1][num_classes + 1];

		for (int i = 1; i <= ntax; i++) {
			for (int j = i + 1; j <= ntax; j++) {
				for (int k = 1; k <= num_classes; k++) {
					if (class_value[k] == 0) {
						dhij_k[i][j][k] = 100000;
						if (!warned_dhij) {
							System.err.println("Nei_Miller: denominator zero in equation (3)");
							warned_dhij = true;
						}
					} else
						dhij_k[i][j][k]
								= (-Math.log(sij_k[i][j][k])) / class_value[k]; // equation (3)
				}
			}

			//doc.notifySetProgress(100 * (characters.getNchar() + 2 * ntax + i) / maxProgress);
			progress.incrementProgress();
		}

		// Compute mk_k (mk_bar=(mii_k+mjj_k)/2):

		final double[][][] mk_k = new double[ntax + 1][ntax + 1][num_classes + 1];

		for (int i = 1; i <= ntax; i++) {
			for (int j = i; j <= ntax; j++) {
				for (int k = 1; k <= num_classes; k++) {
					mk_k[i][j][k] = (mij_k[i][i][k] + mij_k[j][j][k]) / 2.0;
				}
			}

			//doc.notifySetProgress((100 * characters.getNchar() + 3 * ntax + i) / maxProgress);
			progress.incrementProgress();
		}

		// Computes the distances as described in equation (4):

		for (int i = 1; i <= ntax; i++) {
			for (int j = i + 1; j <= ntax; j++) {
				// Computes the bottom of equation 4:
				double bottom = 0;
				for (int k = 1; k <= num_classes; k++)
					bottom += mk_k[i][j][k] * class_value[k];

				// Computes the top of equation 4:
				double top = 0;
				for (int k = 1; k <= num_classes; k++)
					top += mk_k[i][j][k] * class_value[k] * dhij_k[i][j][k];

				if (bottom != 0)
					distancesBlock.set(i, j, top / bottom);
				else {
					if (!warned_dist) {
						System.err.println("nei_miller: denominator zero in equation (4)");
						warned_dist = true;
					}
					distancesBlock.set(i, j, 1);
				}
			}

			//doc.notifySetProgress(100 * (characters.getNchar() + 4 * ntax + i) / maxProgress);
			progress.incrementProgress();
		}
		progress.close();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return super.isApplicable(taxa, datablock) && datablock.getDataType() == CharactersType.Standard && datablock.getCharacterWeights() != null;
	}
}
