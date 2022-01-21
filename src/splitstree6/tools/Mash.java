/*
 *  Mash.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tools;

import jloda.kmers.mash.MashDistance;
import jloda.kmers.mash.MashSketch;
import jloda.seq.FastAFileIterator;
import jloda.swing.util.ArgsOptions;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.stream.Collectors;

public class Mash {
	/**
	 * runs the mash algorithm
	 *
	 * @param args
	 * @throws UsageException
	 * @throws IOException
	 */
	public static void main(String[] args) {
		try {
			ProgramProperties.setProgramName("Mash");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);

			PeakMemoryUsageMonitor.start();
			(new Mash()).run(args);
			System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
			System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
			System.exit(0);
		} catch (Exception ex) {
			Basic.caught(ex);
			System.exit(1);
		}
	}

	/**
	 * run
	 *
	 * @param args
	 * @throws UsageException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void run(String[] args) throws Exception {
		final ArgsOptions options = new ArgsOptions(args, this, "Computes Mash sketches, Jaccard index and distances");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("Copyright (C) 2022 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
		options.setAuthors("Daniel H. Huson");

		var command = options.getCommand(ArgsOptions.createCommand("sketch", "Compute sketches"),
				ArgsOptions.createCommand("jaccard", "Compute Jaccard indices"),
				ArgsOptions.createCommand("distances", "Compute Mash distances"));

		options.comment("Input and output");
		var inputFiles = options.getOptionMandatory("-i", "input", "Input files in FastA format (stdin, *.gz ok)", new String[0]);
		var output = options.getOption("-o", "output", "Output file (stdout, *.gz ok)", "stdout");

		options.comment("Options");
		var kmerSize = options.getOption("-k", "kmer", "kmer size", 21);
		var sketchSize = options.getOption("-s", "sketch", "Sketch size", 1000);

		options.done();

		var sketches = new MashSketch[inputFiles.length];
		for (int i = 0; i < inputFiles.length; i++) {
			var inputFile = inputFiles[i];
			var name = FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(inputFile), "");
			try (var iterator = new FastAFileIterator(inputFile)) {
				var sequences = IteratorUtils.asStream(iterator.records()).map(p -> p.getSecond().getBytes()).collect(Collectors.toList());
				sketches[i] = MashSketch.compute(name, sequences, true, sketchSize, kmerSize, 666, false, new ProgressPercentage(name));

			}
		}

		switch (command) {
			case "sketch" -> {
				try (var w = new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(output))) {
					for (var sketch : sketches) {
						w.write(sketch.toString() + "\n");
					}
				}
			}
			case "jaccard" -> {
				try (var w = new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(output))) {
					w.write(sketches.length + "\n");
					for (MashSketch iSketch : sketches) {
						w.write(iSketch.getName());
						for (var j = 0; j < sketches.length; j++) {
							var jSketch = sketches[j];
							w.write(j == 0 ? "\t" : " ");
							w.write(String.format("%.8f", MashDistance.computeJaccardIndex(iSketch, jSketch)));
						}
						w.write("\n");
					}
				}
			}
			case "distances" -> {
				try (var w = new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(output))) {
					w.write(sketches.length + "\n");
					for (MashSketch iSketch : sketches) {
						w.write(iSketch.getName());
						for (var j = 0; j < sketches.length; j++) {
							var jSketch = sketches[j];
							w.write(j == 0 ? "\t" : " ");
							w.write(String.format("%.8f", MashDistance.compute(iSketch, jSketch)));
						}
						w.write("\n");
					}
				}
			}
		}
	}
}
