/*
 * ComputeMashSketches.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tools;

import jloda.fx.util.ArgsOptions;
import jloda.fx.util.ProgramExecutorService;
import jloda.kmers.mash.MashSketch;
import jloda.thirdparty.HexUtils;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;
import jloda.util.progress.ProgressSilent;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * compute mash sketches
 * Daniel Huson, 7.2020
 */
public class ComputeMashSketches {
	/**
	 * main
	 */
	public static void main(String[] args) {
		try {
			ProgramProperties.setProgramName("ComputeMashSketches");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);
			PeakMemoryUsageMonitor.start();
			(new ComputeMashSketches()).run(args);
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
		final ArgsOptions options = new ArgsOptions(args, this.getClass(), "Computes mash sketches for FastA files");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson");

		options.comment("Input and output");
		final String[] input = options.getOptionMandatory("-i", "input", "Input fastA files (directory or .gz ok)", new String[0]);
		final String[] output = options.getOptionMandatory("-o", "output", "Output mash sketch files (directory or .gz ok, use suffix .msketch for files)", new String[0]);
		final String outputFormat = options.getOption("-f", "format", "Sketch output format", new String[]{"hex", "binary", "text"}, "hex");
		final boolean createKMerFiles = options.getOption("-ok", "kMerFiles", "Create k-mer files, too", false);

		options.comment("Mash parameters");

		final int kParameter = options.getOption("-k", "kmerSize", "Word size k", 21);
		final int sParameter = options.getOption("-s", "sketchSize", "Sketch size", 1000);
		final int randomSeed = options.getOption("-rs", "randomSeed", "Hashing random seed", 42);
		final boolean filterUnique = options.getOption("-fu", "filterUnique", "Filter unique k-mers (use only for error-prone reads)", false);

		final boolean isNucleotideData = options.getOption("-st", "sequenceType", "Sequence type", new String[]{"dna", "protein"}, "dna").equalsIgnoreCase("dna");

		options.comment(ArgsOptions.OTHER);
		// add number of cores option
		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-t", "threads", "Number of threads", 8));

		options.done();

		final ArrayList<String> inputFiles = new ArrayList<>();
		for (String name : input) {
			if (FileUtils.fileExistsAndIsNonEmpty(name))
				inputFiles.add(name);
			else if (FileUtils.isDirectory(name)) {
				inputFiles.addAll(FileUtils.getAllFilesInDirectory(name, true, ".fasta", ".fna", ".faa", ".fasta.gz", ".fna.gz", ".faa.gz"));
			}
		}

		if (inputFiles.isEmpty())
			throw new UsageException("No input files");

		for (String name : inputFiles) {
			FileUtils.checkFileReadableNonEmpty(name);
		}

		final ArrayList<String> outputFiles = new ArrayList<>();
		if (output.length == 0) {
			for (String file : inputFiles) {
				outputFiles.add(FileUtils.replaceFileSuffix(file, ".msketch"));
			}
		} else if (output.length == 1) {
			if (output[0].equals("stdout")) {
				outputFiles.add("stdout");
			} else if (FileUtils.isDirectory(output[0])) {
				for (String file : inputFiles) {
					outputFiles.add(new File(output[0], FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(file), ".msketch")).getPath());
				}
			} else if (inputFiles.size() == 1) {
				outputFiles.add(output[0]);
			} else
				throw new UsageException("Input and output files don't match");
		} else if (output.length == inputFiles.size()) {
			outputFiles.addAll(Arrays.asList(output));
		} else
			throw new UsageException("Input and output files don't match");

		final ArrayList<Pair<String, String>> inputOutputPairs = new ArrayList<>();

		for (int i = 0; i < inputFiles.size(); i++) {
			inputOutputPairs.add(new Pair<>(inputFiles.get(i), outputFiles.get(outputFiles.size() == 1 ? 0 : i)));
		}

		if (FileUtils.isDirectory(output[0]))
			System.err.println("Writing to directory: " + output[0]);

		try (final var progress = new ProgressPercentage("Sketching...", inputOutputPairs.size())) {
			final Single<IOException> exception = new Single<>();
			final ExecutorService executor = Executors.newFixedThreadPool(ProgramExecutorService.getNumberOfCoresToUse());
			try {
				inputOutputPairs.forEach(inputOutputPair -> executor.submit(() -> {
					if (exception.isNull()) {
						try {
							final String inputFile = inputOutputPair.getFirst();
							final byte[] sequence = readSequences(inputFile);
							final MashSketch sketch = MashSketch.compute(inputFile, Collections.singleton(sequence), isNucleotideData, sParameter, kParameter, randomSeed, filterUnique, true, new ProgressSilent());
							saveSketch(inputOutputPair.getSecond(), sketch, outputFormat);

							if (createKMerFiles) {
								try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(FileUtils.replaceFileSuffixKeepGZ(inputOutputPair.getSecond(), ".kmers"))))) {
									w.write(sketch.getKMersString());
								}
							}
							synchronized (progress) {
								progress.incrementProgress();
							}
						} catch (IOException ex) {
							exception.setIfCurrentValueIsNull(ex);
						}
					}
				}));
			} finally {
				executor.shutdown();
				executor.awaitTermination(1000, TimeUnit.DAYS);
			}
			if (exception.get() != null)
				throw exception.get();
		}
		System.err.printf("Wrote %,d files%n", inputOutputPairs.size());
	}

	private byte[] readSequences(String fileName) throws IOException {
		try (FileLineIterator it = new FileLineIterator(fileName)) {
			return it.stream().filter(line -> !line.startsWith(">")).map(line -> line.replaceAll("\\s+", "")).collect(Collectors.joining()).getBytes();
		}
	}

	private void saveSketch(String outputFile, MashSketch sketch, String outputFormat) throws IOException {
		try (OutputStream outs = FileUtils.getOutputStreamPossiblyZIPorGZIP(outputFile)) {
			switch (outputFormat) {
				case "text":
					try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(outs))) {
						w.write(sketch.getString() + "\n");
					}
					break;
				case "hex":
					try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(outs))) {
						w.write(HexUtils.encodeHexString(sketch.getBytes()) + "\n");
					}
					break;
				case "binary":
					try (BufferedOutputStream w = new BufferedOutputStream(outs)) {
						w.write(sketch.getBytes());
					}
					break;
			}
		}
	}
}
