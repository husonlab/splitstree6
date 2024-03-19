/*
 *  SplitFastA.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools.internal;

import jloda.fx.util.ArgsOptions;
import jloda.seq.FastAFileIterator;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;

import java.io.Writer;

public class SplitFastA {
	/**
	 * main
	 */
	public static void main(String[] args) {
		try {
			ProgramProperties.setProgramName("SplitFastA");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);

			PeakMemoryUsageMonitor.start();
			(new SplitFastA()).run(args);
			PeakMemoryUsageMonitor.report();
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
		final ArgsOptions options = new ArgsOptions(args, this.getClass(), "Split sequences in a FastA file");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson");

		options.comment("Input and output");
		var inputFile = options.getOptionMandatory("-i", "input", "Input FastA file (stdin, .gz ok)", "");
		var outputFile = options.getOption("-o", "output", "Output file name ", "out.fasta");
		var numFiles = options.getOption("-n", "num", "Number of files", 10);

		options.done();

		FileUtils.checkFileReadableNonEmpty(inputFile);

		var writers = new Writer[numFiles];
		var digits = (int) Math.ceil(Math.log10(numFiles + 1));
		var format = FileUtils.replaceFileSuffix(outputFile, "-%0" + digits + "d" + FileUtils.getFileSuffix(outputFile));
		for (var n = 0; n < numFiles; n++) {
			var fileName = format.formatted(n + 1);
			if (fileName.equals(inputFile))
				throw new UsageException("output file same name as input file: " + fileName);
			System.err.println("Output file: " + fileName);
			writers[n] = FileUtils.getOutputWriterPossiblyZIPorGZIP(fileName);
		}

		try (var it = new FastAFileIterator(inputFile);
			 var progress = new ProgressPercentage("Reading: " + inputFile, it.getMaximumProgress())) {

			while (it.hasNext()) {
				var pair = it.next();
				var header = pair.getFirst();
				var sequence = pair.getSecond();
				var partSize = (sequence.length() + 1) / numFiles;
				for (var n = 0; n < writers.length; n++) {
					var w = writers[n];
					w.write(header + "\n");
					var start = n * partSize;
					var end = Math.min((n + 1) * partSize, sequence.length());
					w.write(sequence.substring(start, end) + "\n");
				}
				progress.setProgress(it.getProgress());
			}
		}
		for (var w : writers) {
			w.close();
		}
	}
}
