/*
 * HammingDistances.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.ProgramExecutorService;
import jloda.util.Single;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * hamming distances
 *
 * @author Daniel Huson, 2003, 2017
 */
public class HammingDistances extends Characters2Distances {
	private final BooleanProperty optionNormalize = new SimpleBooleanProperty(this, "optionNormalize", true);

	public List<String> listOptions() {
		return List.of(optionNormalize.getName());
	}

	@Override
	public String getCitation() {
		return "Hamming 1950; Hamming, Richard W. Error detecting and error correcting codes. Bell System Technical Journal. 29 (2): 147–160. MR 0035935, 1950.";
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionNormalize.getName()))
			return "Normalize distances";
		else
			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, CharactersBlock characters, DistancesBlock distancesBlock) throws IOException {
		progress.setMaximum(((long) taxa.getNtax() * taxa.getNtax()) / 2 - taxa.getNtax());

		distancesBlock.setNtax(characters.getNtax());

		var service = Executors.newFixedThreadPool(ProgramExecutorService.getNumberOfCoresToUse());

		var exception = new Single<IOException>(null);
		final int ntax = taxa.getNtax();

		try {
			for (int s0 = 1; s0 <= ntax; s0++) {
				for (int t0 = s0 + 1; t0 <= ntax; t0++) {
					final var s = s0;
					final var t = t0;
					service.submit(() -> {
						if (exception.isNull()) {
							try {
								final PairwiseCompare seqPair = new PairwiseCompare(characters, s, t);
								var dist = -1.0;

								final var F = seqPair.getF();

								if (F != null) {
									var p = 1.0;
									for (var x = 0; x < seqPair.getNumStates(); x++) {
										p = p - F[x][x];
									}
									if (!isOptionNormalize())
										p = Math.round(p * seqPair.getNumNotMissing());
									dist = p;
								}
								distancesBlock.set(s, t, dist);
								distancesBlock.set(t, s, dist);
								progress.incrementProgress();
							} catch (IOException ex) {
								exception.setIfCurrentValueIsNull(ex);
							}
						}
					});
				}
			}
		} finally {
			service.shutdown();
			try {
				service.awaitTermination(1000, TimeUnit.DAYS);
			} catch (InterruptedException ignored) {
			}
		}

		if (exception.isNotNull())
			throw exception.get();

		FixUndefinedDistances.apply(distancesBlock);
		progress.reportTaskCompleted();
	}

	public boolean isOptionNormalize() {
		return optionNormalize.getValue();
	}

	public BooleanProperty optionNormalizeProperty() {
		return optionNormalize;
	}

	public void setOptionNormalize(boolean optionNormalize) {
		this.optionNormalize.setValue(optionNormalize);
	}
}
