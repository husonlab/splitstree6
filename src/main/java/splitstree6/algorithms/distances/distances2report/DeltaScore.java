/*
 *  DeltaScore.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2report;

import jloda.util.NumberUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.Collection;

/**
 * computes the delta score for a distance matrix
 * Daniel Huson, 2.2023
 */
public class DeltaScore extends Distances2ReportBase {

	@Override
	public String getCitation() {
		return "Holland et al 2002;" +
			   "BR Holland, KT Huber, AWM Dress and V. Moulton, Delta Plots: A tool for analyzing phylogenetic distance data, Molecular Biology and Evolution, 19(12):2051–2059, 2002.";
	}

	@Override
	public String getShortDescription() {
		return "Calculates the delta score.";
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock dist, Collection<Taxon> selectedTaxaList) {

		var DEBUG_DELTA = false;

		var selectedTaxa = (selectedTaxaList.size() > 0 ? taxaBlock.getTaxa().stream().filter(selectedTaxaList::contains).mapToInt(taxaBlock::indexOf).toArray() :
				taxaBlock.getTaxa().stream().mapToInt(taxaBlock::indexOf).toArray());

		if (selectedTaxa.length < 4)
			return "Delta score=0 (because fewer than 4 taxa selected)";

		var taxonAverages = new double[2][selectedTaxa.length];
		var totalAverage = new double[]{0.0, 0.0};

		var numericalProblems = false;
		// double avDistance = 0.0;

		//Loop over all quartets
		for (var i4 = 0; i4 < selectedTaxa.length; i4++) {
			var t4 = selectedTaxa[i4];
			for (var i3 = 0; i3 < i4; i3++) {
				var t3 = selectedTaxa[i3];
				for (var i2 = 0; i2 < i3; i2++) {
					var t2 = selectedTaxa[i2];
					for (var i1 = 0; i1 < i2; i1++) {
						int t1 = selectedTaxa[i1];
						var d_12_34 = dist.get(t1, t2) + dist.get(t3, t4);
						var d_13_24 = dist.get(t1, t3) + dist.get(t2, t4);
						var d_14_23 = dist.get(t1, t4) + dist.get(t2, t3);

						double[] qs = {d_12_34, d_13_24, d_14_23};
						//manual bubble sort
						if (qs[0] > qs[1]) {
							var tmp = qs[0];
							qs[0] = qs[1];
							qs[1] = tmp;
						}
						if (qs[1] > qs[2]) {
							var tmp = qs[1];
							qs[1] = qs[2];
							qs[2] = tmp;
						}
						if (qs[0] > qs[1]) {
							var tmp = qs[0];
							qs[0] = qs[1];
							qs[1] = tmp;
						}
						//evaluate score
						double[] delta = {0, 0};
						if (qs[2] > qs[0] + 1e-7) {
							delta[0] = (qs[2] - qs[1]) / (qs[2] - qs[0]);
							delta[1] = (qs[2] - qs[1]) * (qs[2] - qs[1]);
						} else {
							if (qs[2] != qs[0])   //Flag that there where quartets where delta is unstable.
								numericalProblems = true;
						}


						if (DEBUG_DELTA) {
							System.out.println("" + t1 + ", " + t2 + ", " + t3 + ", " + t4 + ", " + "[" + d_12_34 + ", " + d_13_24 + ", " + d_14_23 + "], " + delta[0]);
						}

						for (int k = 0; k < 2; k++) {
							taxonAverages[k][i1] += delta[k];
							taxonAverages[k][i2] += delta[k];
							taxonAverages[k][i3] += delta[k];
							taxonAverages[k][i4] += delta[k];
							totalAverage[k] += delta[k];
						}

					}
				}
			}
		}
		var n = selectedTaxa.length;
		var ntriples = (n - 1) * (n - 2) * (n - 3) / 6;     //Number of triples containing a given taxon
		var nquads = ntriples * n / 4;     //Number of 4-sets

		for (var i = 0; i < n; i++) {
			taxonAverages[0][i] /= ntriples;
			taxonAverages[1][i] /= ntriples;
		}
		totalAverage[0] /= nquads;
		totalAverage[1] /= nquads;


		var avDistance = computeAverageDistance(dist, selectedTaxa);
		var scale = avDistance * avDistance;
		totalAverage[1] /= scale;


		//Print out the individual taxon scores
		if (numericalProblems) {
			System.err.println("WARNING: Some quartets were close to 'star-like' so set to zero for delta score calculation\n");
		}

		//   System.out.println("Average delta score for selection = " + Basic.roundSigFig(totalAverage[0], 5) + "\nAverage Q-residual = " + Basic.roundSigFig(totalAverage[1], 5) + "\n");
		//  System.out.println("Average distance = " + (avDistance));


		// double[] pvals = computeParametricPval(doc, 100, selectedTaxa, totalAverage);
		var buf = new StringBuilder();
		buf.append("Delta score: %f%n".formatted(NumberUtils.roundSigFig(totalAverage[0], 4)));//+ " (p-val = " + Basic.roundSigFig(pvals[0], 4)+")";
		buf.append("Q-residual score: %f%n".formatted(NumberUtils.roundSigFig(totalAverage[1], 4)));// + " (p-val = " + Basic.roundSigFig(pvals[1], 4)+")";
		if (selectedTaxa.length == taxaBlock.getNtax()) {
			buf.append("Computed on %d taxa%n".formatted(taxaBlock.getNtax()));
		} else {
			buf.append("Computed on %d (of %d) selected taxa%n".formatted(selectedTaxa.length, taxaBlock.getNtax()));
		}

		buf.append("\nDelta scores for individual taxa:\n");
		buf.append("Id\tname\tdelta score \tQ-residual\n");
		for (var i = 0; i < selectedTaxa.length; i++) {
			buf.append(String.format("%d\t%s\t%f\t%f%n", selectedTaxa[i], taxaBlock.getLabel(selectedTaxa[i]), NumberUtils.roundSigFig(taxonAverages[0][i], 5), NumberUtils.roundSigFig(taxonAverages[1][i] / scale, 5)));
		}
		buf.append("\n");


		// String result = "\nDelta score = " + Basic.roundSigFig(totalAverage[0], 4) + " (p-val = " + Basic.roundSigFig(pvals[0], 4)+")";
		//result += "\nQ-residual score = " + Basic.roundSigFig(totalAverage[1], 4) + " (p-val = " + Basic.roundSigFig(pvals[1], 4)+")";

		return buf.toString();
	}

	private double computeAverageDistance(DistancesBlock dist, int[] selectedTaxa) {
		var sum = 0.0;
		var npairs = 0;
		if (selectedTaxa != null) {
			for (var i = 0; i < selectedTaxa.length; i++)
				for (var j = i + 1; j < selectedTaxa.length; j++) {
					sum += dist.get(selectedTaxa[i], selectedTaxa[j]);
					npairs++;
				}
		} else {
			for (var i = 1; i <= dist.getNtax(); i++)
				for (var j = 2; j <= dist.getNtax(); j++) {
					sum += dist.get(i, j);
					npairs++;
				}
		}

		return sum / npairs;
	}
}
