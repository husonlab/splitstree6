/*
 *  ExtractSmallerMatrices.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra;

import jloda.fx.util.ArgsOptions;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2distances.DistancesTaxaFilter;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.distances.DistancesReader;
import splitstree6.io.writers.distances.PhylipWriter;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class ExtractSmallerMatrices {
	/**
	 * main
	 */
	public static void main(String[] args) {
		try {
			ProgramProperties.setProgramName("ExtractSmallerMatrices");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);

			PeakMemoryUsageMonitor.start();
			(new ExtractSmallerMatrices()).run(args);
			PeakMemoryUsageMonitor.report();
			System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
			System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
			System.exit(0);
		} catch (Exception ex) {
			Basic.caught(ex);
			System.exit(1);
		}
	}

	/**
	 * run the program
	 */
	public void run(String[] args) throws Exception {
		final ArgsOptions options = new ArgsOptions(args, this.getClass(), "Extract smaller matrices from a large distance matrix");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson");

		options.comment("Input and output");
		var input = options.getOptionMandatory("-i", "input", "Input distance file (.gz ok)", "");
		var output = options.getOption("-o", "output", "Output file name (directory or .gz ok, use %s for size and %t for replicate)",
				FileUtils.replaceFileSuffixKeepGZ(input, "-%s-%r" + FileUtils.getFileSuffix(input)));

		options.comment("Parameters");
		var sizeSpecification = options.getOption("-s", "sizes", "Output sizes", "5,10,15,20-100/10");
		var replicates = options.getOption("-r", "replicates", "Number of replicates per size", 10);
		var random = new Random(options.getOption("-d", "randomSeed", "Random generator seed", 42));
		options.done();

		FileUtils.fileExistsAndIsNonEmpty(input);
		if (replicates <= 0)
			throw new IOException("Number of replications must be positive, got: " + replicates);

		var sizes = BitSetUtils.valueOf(sizeSpecification);
		System.err.println("Sizes: " + StringUtils.toString(sizes, " "));

		var importer = (DistancesReader) ImportManager.getInstance().getImporterByDataTypeAndFileFormat(DistancesBlock.class, ImportManager.getInstance().getFileFormat(input));
		var taxaBlock = new TaxaBlock();
		var distancesBlock = new DistancesBlock();
		importer.read(new ProgressPercentage(), input, taxaBlock, distancesBlock);

		if (FileUtils.isDirectory(output)) {
			output = new File(output, FileUtils.replaceFileSuffixKeepGZ(FileUtils.getFileNameWithoutPath(input), "-%s-%r" + FileUtils.getFileSuffix(input))).getPath();
		}

		var created = 0;
		try (var progress = new ProgressPercentage("Creating output files:", (long) sizes.cardinality() * replicates)) {
			for (var size : BitSetUtils.members(sizes)) {
				if (size > taxaBlock.getNtax()) {
					System.err.printf("Finishing early: specified size (%d) exceeds input size (%d)%n", size, taxaBlock.getNtax());
				}
				for (var r = 1; r <= replicates; r++) {
					var outputFile = output.replaceAll("%s", String.format("s%04d", size)).replaceAll("%r", String.format("r%04d", r));
					var subTaxa = BitSetUtils.randomSubset(size, random, taxaBlock.getTaxaSet());
					var subTaxaBlock = new TaxaBlock();
					for (var t : BitSetUtils.members(subTaxa)) {
						subTaxaBlock.add(taxaBlock.get(t));
					}
					var subdistancesBlock = new DistancesBlock();
					new DistancesTaxaFilter().filter(new ProgressSilent(), taxaBlock, subTaxaBlock, distancesBlock, subdistancesBlock);
					try (var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(outputFile)) {
						(new PhylipWriter()).write(w, subTaxaBlock, subdistancesBlock);
						created++;
					}
					progress.incrementProgress();
				}
			}
		}
		System.err.printf("Files created: %,d%n", created);
	}

}
