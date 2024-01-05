/*
 *  Mash.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.*;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.window.NotificationManager;
import jloda.kmers.GenomeDistanceType;
import jloda.kmers.mash.MashDistance;
import jloda.kmers.mash.MashSketch;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.GenomesBlock;
import splitstree6.data.GenomesFormat;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * implements the Mash algorithm
 * Daniel Huson, 3.2020
 */
public class Mash extends Genomes2Distances {

	private final IntegerProperty optionKMerSize = new SimpleIntegerProperty(15);
	private final IntegerProperty optionSketchSize = new SimpleIntegerProperty(10000);
	private final ObjectProperty<GenomeDistanceType> optionDistances = new SimpleObjectProperty<>(GenomeDistanceType.Mash);

	private final BooleanProperty optionIgnoreUniqueKMers = new SimpleBooleanProperty(false);

	private final IntegerProperty optionHashSeed = new SimpleIntegerProperty(42);

	private final boolean verbose = false;

	@Override
	public List<String> listOptions() {
		return Arrays.asList("optionKMerSize", "optionSketchSize", "optionDistances", "optionHashSeed", "optionIgnoreUniqueKMers");
	}

	@Override
	public String getCitation() {
		return "Ondov et al 2016; Brian D. Ondov, Todd J. Treangen, Páll Melsted, Adam B. Mallonee, Nicholas H. Bergman, Sergey Koren & Adam M. Phillippy. Mash: fast genome and metagenome distance estimation using MinHash. Genome Biol 17, 132 (2016).";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, GenomesBlock genomesBlock, DistancesBlock distancesBlock) throws IOException {

		try {
			final var isNucleotideData = genomesBlock.getFormat().getCharactersType().equals(GenomesFormat.CharactersType.dna);

			progress.setSubtask("Sketching");
			progress.setMaximum(genomesBlock.getNGenomes());
			progress.setProgress(0);

			genomesBlock.checkGenomesPresent();

			final var sketches = new MashSketch[genomesBlock.size()];
			{
				final var service = Executors.newFixedThreadPool(ProgramExecutorService.getNumberOfCoresToUse());
				final var exception = new Single<Exception>(null);
				try {
					for (var g : BitSetUtils.range(0, genomesBlock.getNGenomes())) {
						service.submit(() -> {
							if (exception.get() == null) {
								try {
									progress.checkForCancel();
									final var genome = genomesBlock.getGenome(g + 1);
									sketches[g] = MashSketch.compute(genome.getName(), IteratorUtils.asList(genome.parts()), isNucleotideData, getOptionSketchSize(), getOptionKMerSize(), getOptionHashSeed(), isOptionIgnoreUniqueKMers(), progress);
								} catch (Exception e) {
									exception.setIfCurrentValueIsNull(e);
								}
							}
						});
					}
				} finally {
					service.shutdown();
					service.awaitTermination(1000, TimeUnit.DAYS);

				}
				if (exception.get() != null)
					throw exception.get();
			}

			// todo: warn when files not found

			var countTooSmall = 0;
			for (var sketch : sketches) {
				if (sketch.getValues().length < optionSketchSize.get())
					countTooSmall++;

			}
			if (countTooSmall > 0)
				NotificationManager.showWarning(String.format("Too few k-mers for %,d genomes- rerun with smaller sketch size", countTooSmall));

			var triplets = new ArrayList<Triplet<MashSketch, MashSketch, Double>>();

			for (var i = 0; i < sketches.length; i++) {
				for (var j = i + 1; j < sketches.length; j++) {
					triplets.add(new Triplet<>(sketches[i], sketches[j], 0.0));
				}
			}

			progress.setSubtask("distances");
			ExecuteInParallel.apply(triplets, t -> t.setThird(MashDistance.compute(t.getFirst(), t.getSecond(), getOptionDistances())), ProgramExecutorService.getNumberOfCoresToUse(), progress);
			progress.reportTaskCompleted();

			var name2rank = new HashMap<String, Integer>();

			for (var i = 0; i < sketches.length; i++) {
				final String name = sketches[i].getName();
				name2rank.put(name, i + 1);
			}

			distancesBlock.clear();

			int countUndefined = 0;

			distancesBlock.setNtax(taxaBlock.getNtax());
			for (var triplet : triplets) {
				final var t1 = name2rank.get(triplet.getFirst().getName());
				final var t2 = name2rank.get(triplet.getSecond().getName());
				final var dist = triplet.getThird();
				if (verbose) {
					System.out.println(triplet.getFirst().getName() + "\t" + triplet.getSecond().getName() + "\t" + triplet.getThird());
				}
				distancesBlock.set(t1, t2, dist);
				distancesBlock.set(t2, t1, dist);
				if (dist == 0.75)
					countUndefined++;
			}
			if (countUndefined > 0)
				NotificationManager.showWarning(String.format("Failed to estimate distance for %d pairs (distances set to 0.75) - increase sketch size or decrease k", countUndefined));
		} catch (IOException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.endsWith("IgnoreUniqueKMers"))
			return "Use this only when input data consists of unassembled reads";
		return super.getToolTip(optionName);
	}

	public int getOptionKMerSize() {
		return optionKMerSize.get();
	}

	public IntegerProperty optionKMerSizeProperty() {
		return optionKMerSize;
	}

	public void setOptionKMerSize(int optionKMerSize) {
		this.optionKMerSize.set(optionKMerSize);
	}

	public int getOptionSketchSize() {
		return optionSketchSize.get();
	}

	public IntegerProperty optionSketchSizeProperty() {
		return optionSketchSize;
	}

	public void setOptionSketchSize(int optionSketchSize) {
		this.optionSketchSize.set(optionSketchSize);
	}

	public GenomeDistanceType getOptionDistances() {
		return optionDistances.get();
	}

	public ObjectProperty<GenomeDistanceType> optionDistancesProperty() {
		return optionDistances;
	}

	public void setOptionDistances(GenomeDistanceType optionDistances) {
		this.optionDistances.set(optionDistances);
	}

	public boolean isOptionIgnoreUniqueKMers() {
		return optionIgnoreUniqueKMers.get();
	}

	public BooleanProperty optionIgnoreUniqueKMersProperty() {
		return optionIgnoreUniqueKMers;
	}

	public void setOptionIgnoreUniqueKMers(boolean optionIgnoreUniqueKMers) {
		this.optionIgnoreUniqueKMers.set(optionIgnoreUniqueKMers);
	}

	public int getOptionHashSeed() {
		return optionHashSeed.get();
	}

	public IntegerProperty optionHashSeedProperty() {
		return optionHashSeed;
	}

	public void setOptionHashSeed(int optionHashSeed) {
		this.optionHashSeed.set(optionHashSeed);
	}
}

