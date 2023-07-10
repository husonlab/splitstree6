/*
 * BloomFilterTool.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.kmers.bloomfilter.BloomFilter;
import jloda.thirdparty.HexUtils;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * compute bloom filter for k-mers
 * Daniel Huson, 7.2020
 */
public class BloomFilterTool {
	/**
	 * main
	 */
	public static void main(String[] args) {
		try {
			ProgramProperties.setProgramName("ComputeBloomFilter");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);

			PeakMemoryUsageMonitor.start();
			(new BloomFilterTool()).run(args);
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
		final ArgsOptions options = new ArgsOptions(args, this.getClass(), "Make a bloom filter or test for containment");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson");
		options.setCommandMandatory(true);

		final String command = options.getCommand(
				new ArgsOptions.Command("make", "Create a Bloom filter for a collection of k-mers."),
				new ArgsOptions.Command("contains", "Determine containment of k-mers in Bloom filter(s)."),
				new ArgsOptions.Command("help", "Show program usage and quit."));


		options.comment("Input and output");
		final String[] kmerInput = options.getOptionMandatory("-i", "input", "Input files containing k-mers, one per line (directories or .gz ok, use suffix .kmers)", new String[0]);
		final String output;
		if (options.isDoHelp() || command.equals("make"))
			output = options.getOptionMandatory("-o", "output", "Output file (stdout ok)", "");
		else
			output = options.getOption("-o", "output", "Output file (stdout ok)", "stdout");

		final boolean useHexEncoding = options.getOption("-f", "format", "Bloom filter output format", new String[]{"hex", "binary"}, "hex").equalsIgnoreCase("hex");

		options.comment("MAKE options");
		final double fpProbability;
		if (options.isDoHelp() || command.equals("make"))
			fpProbability = options.getOption("-fp", "fpProb", "Probability of false positive error in Bloom filter", 0.0001);
		else
			fpProbability = 0;
		final int maxBytes;
		if (options.isDoHelp() || command.equals("make"))
			maxBytes = (int) Basic.parseKiloMegaGiga(options.getOption("-mb", "maxBytes", "Maximum number of bytes for a Bloom filter", "1M"));
		else
			maxBytes = (int) Basic.parseKiloMegaGiga("1M");


		options.comment("CONTAINS options");
		final String[] bloomFilterInput;
		if (options.isDoHelp() || command.equals("contains"))
			bloomFilterInput = options.getOptionMandatory("-ib", "bloomFilterInput", "Input files bloom filters (directory ok, use suffix .bfilters)", new String[0]);
		else
			bloomFilterInput = null;

		options.comment(ArgsOptions.OTHER);
		// add number of cores option
		final int threads = options.getOption("-t", "threads", "Number of threads", 8);

		options.done();

		final ArrayList<String> inputFiles = getInputFiles(kmerInput, ".kmers", ".kmers.gz");

		if (command.equals("make")) {
			System.err.printf("Input files: %,d%n", inputFiles.size());

			final Counter numberOfLines = new Counter(0);

			try (ProgressPercentage progress = new ProgressPercentage("Counting input lines", inputFiles.size())) {
				final ExecutorService service = Executors.newFixedThreadPool(threads);
				final Single<IOException> exception = new Single<>(null);
				try {
					inputFiles.forEach(fileName -> {
						if (FileUtils.fileExistsAndIsNonEmpty(fileName)) {
							service.submit(() -> {
								if (exception.isNull()) {
									try (FileLineIterator it = new FileLineIterator(fileName)) {
										while (it.hasNext()) {
											final String line = it.next();
											if (line.trim().length() > 0)
												numberOfLines.increment();
										}
									} catch (IOException e) {
										exception.setIfCurrentValueIsNull(e);
									} finally {
										synchronized (progress) {
											progress.incrementProgress();
										}
									}
								}
							});
						}
					});
				} finally {
					service.shutdown();
					//noinspection ResultOfMethodCallIgnored
					service.awaitTermination(1000, TimeUnit.DAYS);
				}
				if (exception.get() != null)
					throw exception.get();
			}
			System.err.printf("Input lines: %,d%n", numberOfLines.get());

			final BloomFilter allKMersBloomFilter = new BloomFilter((int) numberOfLines.get(), fpProbability, maxBytes);
			try (ProgressPercentage progress = new ProgressPercentage("Processing input lines", inputFiles.size())) {
				final ExecutorService service = Executors.newFixedThreadPool(threads);
				final Single<IOException> exception = new Single<>(null);
				try {
					inputFiles.forEach(fileName -> {
						if (FileUtils.fileExistsAndIsNonEmpty(fileName)) {
							service.submit(() -> {
								if (exception.isNull()) {
									try (FileLineIterator it = new FileLineIterator(fileName)) {
										final ArrayList<byte[]> list = new ArrayList<>();
										while (it.hasNext()) {
											final byte[] bytes = it.next().trim().getBytes();
											if (bytes.length > 0) {
												list.add(bytes);
											}
										}
										synchronized (allKMersBloomFilter) {
											allKMersBloomFilter.addAll(list);
										}
									} catch (IOException e) {
										exception.setIfCurrentValueIsNull(e);
									} finally {
										synchronized (progress) {
											progress.incrementProgress();
										}
									}
								}
							});
						}
					});
				} finally {
					service.shutdown();
					//noinspection ResultOfMethodCallIgnored
					service.awaitTermination(1000, TimeUnit.DAYS);
				}
				if (exception.get() != null)
					throw exception.get();
			}

			System.err.println("Writing Bloom filter to file: " + output);
			if (useHexEncoding) {
				try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(output)))) {
					w.write(HexUtils.encodeHexString(allKMersBloomFilter.getBytes()) + "\n");
				}
			} else {
				try (OutputStream outs = FileUtils.getOutputStreamPossiblyZIPorGZIP(output)) {
					outs.write(allKMersBloomFilter.getBytes());
				}
			}
			System.err.println("Total file size: " + Basic.getMemorySizeString((new File(output)).length()));
		} else if (command.equals("contains")) {
			final ArrayList<String> bloomFilterFiles = getInputFiles(bloomFilterInput, ".bfilter", ".bfilter.gz");
			final Map<String, BloomFilter> bloomFilters = new HashMap<>();

			try (ProgressPercentage progress = new ProgressPercentage("Reading bloom filters", bloomFilterFiles.size())) {
				final ExecutorService service = Executors.newFixedThreadPool(threads);
				final Single<IOException> exception = new Single<>(null);
				try {
					bloomFilterFiles.forEach(fileName -> {
						if (FileUtils.fileExistsAndIsNonEmpty(fileName)) {
							service.submit(() -> {
								if (exception.isNull()) {
									try {
										final byte[] bytes;
										if (useHexEncoding)
											bytes = HexUtils.decodeHexString(Files.readString((new File(fileName).toPath())).trim());
										else
											bytes = Files.readAllBytes((new File(fileName).toPath()));
										final BloomFilter bloomFilter = BloomFilter.parseBytes(bytes);
										synchronized (bloomFilters) {
											bloomFilters.put(fileName, bloomFilter);
										}
									} catch (IOException e) {
										exception.setIfCurrentValueIsNull(e);
									} finally {
										synchronized (progress) {
											progress.incrementProgress();
										}
									}
								}
							});
						}
					});
				} finally {
					service.shutdown();
					service.awaitTermination(1000, TimeUnit.DAYS);
				}
				if (exception.get() != null)
					throw exception.get();
			}

			try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(output)))) {
				if (bloomFilterFiles.size() > 1)
					w.write("#Table\t" + StringUtils.toString(bloomFilterFiles, "\t") + "\n");
				for (String inputFile : inputFiles) {
					final List<String> kmers = Files.lines((new File(inputFile)).toPath()).collect(Collectors.toList());
					w.write(inputFile);
					for (String bloomFilterFile : bloomFilterFiles) {
						final BloomFilter bloomFilter = bloomFilters.get(bloomFilterFile);
						w.write(" " + bloomFilter.countContainedProbably(kmers));
					}
					w.write("\n");
				}
			}
		}
	}

	public static ArrayList<String> getInputFiles(String[] input, String... suffixes) throws UsageException, IOException {
		final ArrayList<String> result = new ArrayList<>();
		for (String name : input) {
			if (FileUtils.fileExistsAndIsNonEmpty(name))
				result.add(name);
			else if (FileUtils.isDirectory(name)) {
				result.addAll(FileUtils.getAllFilesInDirectory(name, true, suffixes));
			}
		}
		if (result.size() == 0)
			throw new UsageException("No input files");

		for (String name : result) {
			FileUtils.checkFileReadableNonEmpty(name);
		}
		return result;
	}
}
