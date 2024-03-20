/*
 *  GenomeContext.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.fx.util.ArgsOptions;
import jloda.kmers.mash.MashDistance;
import jloda.seq.FastAFileIterator;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;
import jloda.util.progress.ProgressSilent;
import splitstree6.dialog.analyzegenomes.AccessReferenceDatabase;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * compute the genome context of a set of sequences
 * Daniel Huson, 9.2020
 */
public class GenomeContext {
	/**
	 * main
	 */
	public static void main(String[] args) {
		try {
			ProgramProperties.setProgramName("GenomeContext");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);

			PeakMemoryUsageMonitor.start();
			(new GenomeContext()).run(args);
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
		final var options = new ArgsOptions(args, this.getClass(), "Compute the genome context for sequences");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson");

		options.comment("Input and output:");
		final var inputFiles = options.getOptionMandatory("-i", "input", "Input query FastA files (directory, stdin, .gz ok)", Collections.emptyList());
		final var perFastARecord = options.getOption("-p", "perFastaRecord", "Process each FastA record as a separate sequence", false);
		final var databaseFile = options.getOptionMandatory("-d", "database", "Database file", "");
		final var fileCacheDirectory = new File(options.getOption("-c", "cache", "File cache directory for storing downloaded genomes", new File(databaseFile).getParent()));
		final var outputFile = options.getOption("-o", "output", "Output file (stdout, .gz ok)", "stdout");

		options.comment("Filtering");
		var minSketchIntersection = options.getOption("-ms", "minSketchIntersect", "Minimum sketch intersection size", 1);
		final var maxDistance = options.getOption("-md", "maxDistance", "Max mash distance (if set, overrides --minSketchIntersect)", 1d);
		final var best = options.getOption("-ub", "useBest", "Use best distance only", false);
		final var maxCount = options.getOption("-m", "max", "Max number of genomes to return", 25);

		options.comment("Reporting:");
		final var useFastAHeaders = options.getOption("-fh", "useFastaHeader", "Use FastA headers for query sequences", false);
		final var reportName = options.getOption("-rn", "reportNames", "Report reference names", true);
		final var reportId = options.getOption("-ri", "reportIds", "Report reference ids", false);
		final var reportFile = options.getOption("-rf", "reportFiles", "Report reference files", false);
		final var reportDistance = options.getOption("-rd", "reportMashDistances", "Report mash distances", true);
		final var reportLCA = options.getOption("-rlca", "reportLCA", "Report LCA of references", true);
		final var includeStrains = options.getOption("-is", "includeStrains", "Include the genomes of strains for the detected species", false);

		options.comment(ArgsOptions.OTHER);
		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-t", "threads", "Number of threads to use", Runtime.getRuntime().availableProcessors()));
		options.done();

		FileUtils.checkFileReadableNonEmpty(databaseFile);
		FileUtils.checkFileWritable(outputFile, true);

		if (inputFiles.size() == 1) {
			final String name = inputFiles.get(0);
			if (!name.equals("stdin") && !FileUtils.fileExistsAndIsNonEmpty(name)) {
				inputFiles.clear();
				inputFiles.addAll(FileUtils.getAllFilesInDirectory(name, true, ".fa", ".fna", ".fasta", ".fa.gz", ".fna.gz", ".fasta.gz"));
				if (inputFiles.isEmpty())
					throw new IOException("No FastA files found in directory: " + name);
			}
		}

		try (Writer w = new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(outputFile))) {
			final AccessReferenceDatabase database = new AccessReferenceDatabase(databaseFile, () -> fileCacheDirectory, 2 * ProgramExecutorService.getNumberOfCoresToUse());
			// todo: update minSketchIntersection from maxDistance
			if (maxDistance < 1)
				minSketchIntersection = Math.max(minSketchIntersection, computeMinSketchIntersection(maxDistance, database.getMashK(), database.getMashS()));


			try (final ProgressPercentage progress = new ProgressPercentage("Processing input files (" + inputFiles.size() + "):", inputFiles.size())) {
				for (var fileName : inputFiles) {
					final List<Pair<String, String>> pairs = new ArrayList<>();
					try (var it = new FastAFileIterator(fileName)) {
						while (it.hasNext()) {
							pairs.add(it.next());
						}
					}

					if (perFastARecord) {
						if (!useFastAHeaders) {
							final var name = FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(fileName), "");
							int count = 0;
							for (var pair : pairs) {
								pair.setFirst(name + (++count));
							}
						}
					} else { // per file
						final List<String> sequences = pairs.stream().map(Pair::getSecond).collect(Collectors.toList());
						final var name = (useFastAHeaders ? pairs.get(0).getFirst() : FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(fileName), ""));
						pairs.clear();
						pairs.add(new Pair<>(name, StringUtils.toString(sequences, "").replaceAll("\\s", "")));
					}

					for (var pair : pairs) {
						final Collection<Map.Entry<Integer, Double>> list = database.findSimilar(new ProgressSilent(), minSketchIntersection, includeStrains, Collections.singleton(pair.getSecond().getBytes()), false);

						final Map<Integer, String> id2name = new HashMap<>();
						if (reportName) {
							id2name.putAll(database.getNames(list.stream().map(Map.Entry::getKey).collect(Collectors.toList())));
						}
						final Map<Integer, String> id2file = new HashMap<>();
						if (reportFile) {
							id2file.putAll(database.getFiles(list.stream().map(Map.Entry::getKey).collect(Collectors.toList())));
						}

						int count = 0;
						final Set<Integer> taxa = new HashSet<>();

						w.write("Query: " + pair.getFirst() + "\n");
						w.write("Results: " + Math.min(list.size(), maxCount) + "\n");

						double smallestDistance = 1.0;

						final StringBuilder buf = new StringBuilder();
						for (var result : list) {
							if (++count >= maxCount)
								break;
							if (count == 1)
								smallestDistance = result.getValue();
							else if (best && result.getValue() > smallestDistance)
								break;

							taxa.add(result.getKey());
							buf.append(count);
							if (reportName) {
								buf.append("\t").append(id2name.get(result.getKey()));
							}
							if (reportId) {
								buf.append("\t").append(result.getKey());
							}
							if (reportFile) {
								buf.append("\t").append(id2file.get(result.getKey()));
							}
							if (reportDistance) {
								buf.append("\t").append(result.getValue());
							}
							if (!buf.isEmpty())
								buf.append("\n");
						}

						if (reportLCA && !taxa.isEmpty()) {
							final int lca = computeLCA(database, taxa);
							buf.append("LCA: ").append(lca).append(" ").append(database.getNames(Collections.singleton(lca)).get(lca));
						}
						if (!buf.isEmpty()) {
							w.write(buf + "\n");
						}
						w.write("\n");
						w.flush();
					}
					progress.incrementProgress();
				}
			}
		}
	}

	public static int computeMinSketchIntersection(double maxDistance, int mashK, int mashS) {
		for (int i = mashS; i > 1; i--) {
			final double distance = MashDistance.compute((double) (i - 1) / (double) mashS, mashK);
			if (distance > maxDistance)
				return i;
		}
		return 1;
	}

	private static int computeLCA(AccessReferenceDatabase database, Collection<Integer> taxonIds) throws SQLException {
		if (taxonIds.isEmpty())
			return 0;
		else if (taxonIds.size() == 1)
			return taxonIds.iterator().next();

		final Collection<List<Integer>> list = database.getAncestors(taxonIds).values();
		if (list.isEmpty()) {
			return 0;
		} else {
			int prev = 0;
			for (int depth = 0; ; depth++) {
				int current = 0;
				for (var ancestors : list) {
					if (ancestors.size() <= depth)
						return prev;
					if (current == 0)
						current = ancestors.get(depth);
					else if (current != ancestors.get(depth))
						return prev;
				}
				prev = current;
			}
		}
	}
}
