/*
 * HammingDistances.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.algorithms.characters.characters2distances;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.window.NotificationManager;
import jloda.util.Counter;
import jloda.util.progress.ProgressListener;
import jloda.util.Single;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * hamming distances
 *
 * @author Daniel Huson, 2003, 2017
 */
public class HammingDistances extends Characters2Distances {
	private final BooleanProperty optionNormalize = new SimpleBooleanProperty(true);

	public List<String> listOptions() {
		return Collections.singletonList("Normalize");
	}

	@Override
	public String getCitation() {
		return "Hamming 1950; Hamming, Richard W. Error detecting and error correcting codes. Bell System Technical Journal. 29 (2): 147–160. MR 0035935, 1950.";
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals("Normalize"))
			return "Normalize distances";
		else
			return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, CharactersBlock characters, DistancesBlock distances) throws IOException {
		progress.setMaximum(((long) taxa.getNtax() * taxa.getNtax()) / 2 - taxa.getNtax());

		distances.setNtax(characters.getNtax());

		var service = Executors.newFixedThreadPool(ProgramExecutorService.getNumberOfCoresToUse());

		var numMissing = new Counter();
		var exception = new Single<IOException>(null);

		try {
			final int ntax = taxa.getNtax();
			for (int s0 = 1; s0 <= ntax; s0++) {
				for (int t0 = s0 + 1; t0 <= ntax; t0++) {
					final var s = s0;
					final var t = t0;
					service.submit(() -> {
						if (exception.isNull()) {
							try {
								final PairwiseCompare seqPair = new PairwiseCompare(characters, s, t);
								double p = 1.0;

								final double[][] F = seqPair.getF();

								if (F == null) {
									numMissing.increment();
								} else {
									for (int x = 0; x < seqPair.getNumStates(); x++) {
										p = p - F[x][x];
									}

									if (!isOptionNormalize())
										p = Math.round(p * seqPair.getNumNotMissing());
								}
								distances.set(s, t, p);
								distances.set(t, s, p);
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
			//noinspection ResultOfMethodCallIgnored
			try {
				service.awaitTermination(1000, TimeUnit.DAYS);
			} catch (InterruptedException ignored) {
			}
		}

		if (exception.isNotNull())
			throw exception.get();

		if (numMissing.get() > 0)
			NotificationManager.showWarning("Proceed with caution: " + numMissing + " saturated or missing entries in the distance matrix");
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
