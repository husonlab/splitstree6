/*
 * ExternalDistance2Network.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package splitstree6.algorithms.distances.distances2network;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

public class ExternalDistance2Network extends Distances2Network { // implements IExperimental {
	private final StringProperty optionExecutablePath = new SimpleStringProperty(this, "optionExecutablePath");
	private final StringProperty optionCallFormat = new SimpleStringProperty(this, "optionCallFormat");
	private final StringProperty optionTmpDirectory = new SimpleStringProperty(this, "optionTmpDirectory");
	private final BooleanProperty optionKeepTmpFiles = new SimpleBooleanProperty(this, "optionKeepTmpFiles");

	{
		ProgramProperties.track(optionExecutablePath, "");
		ProgramProperties.track(optionCallFormat, "cactus");
		ProgramProperties.track(optionTmpDirectory, System.getProperty("java.io.tmpdir"));
		ProgramProperties.track(optionKeepTmpFiles, false);
	}

	@Override
	public String getCitation() {
		return "";
	}

	@Override
	public String getShortDescription() {
		return "Runs an external distances-to-network algorithm";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionExecutablePath.getName(), optionCallFormat.getName(), optionTmpDirectory.getName(), optionKeepTmpFiles.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		if (optionName.equals(optionExecutablePath.getName())) {
			return "Runs an external distances-to-network algorithm";
		} else if (optionName.equals(optionTmpDirectory.getName())) {
			return "Directory location for temporary files";
		} else if (optionName.equals(optionCallFormat.getName())) {
			return "Input and output call format (currently: only 'cactus' supported, as a hack: put name of precomputed output file here)";
		} else if (optionName.equals(optionKeepTmpFiles.getName())) {
			return "Keep temporary files (otherwise delete them after use)";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, NetworkBlock networkBlock) throws IOException {
		if (getOptionExecutablePath().isBlank())
			throw new IOException("Please set executable file");

		if (!getOptionCallFormat().equals("cactus") && !FileUtils.fileExistsAndIsNonEmpty(getOptionCallFormat()))
			throw new IOException("Unknown call format (try 'cactus')");

		var tmp = String.valueOf(System.currentTimeMillis() & ((1 << 20) - 1));
		var executable = new File(getOptionExecutablePath());
		FileUtils.checkFileReadableNonEmpty(executable.getAbsolutePath());
		//var dir = executable.getParentFile();
		var dir = new File(getOptionTmpDirectory());
		if (!dir.exists() || !dir.isDirectory())
			throw new IOException("Directory does not exist or is not a directory: " + dir.getAbsolutePath());

		var inputFile = new File(dir.getAbsolutePath() + File.separator + "tmp" + tmp + "-input.txt");
		FileUtils.checkFileWritable(inputFile.getPath(), true);
		inputFile.deleteOnExit();
		var outputFile = new File(dir.getAbsolutePath() + File.separator + "tmp" + tmp + "-output.txt");
		FileUtils.checkFileWritable(outputFile.getPath(), true);
		outputFile.deleteOnExit();

		{
			var distances = distancesBlock.getDistances();
			try (var w = new FileWriter(inputFile)) {
				w.write(distances.length + "\n");
				for (var row : distances) {
					var first = true;
					for (var val : row) {
						if (first)
							first = false;
						else
							w.write(",");
						w.write(StringUtils.removeTrailingZerosAfterDot(val));
					}
					w.write("\n");
				}
			}
		}

		if (getOptionCallFormat().equals("cactus")) {
			var start = System.currentTimeMillis();
			runExternalProgramAndWait(progress, executable.getAbsolutePath(), inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
			System.err.printf("%.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
		} else {
			outputFile = new File(getOptionCallFormat());
		}

		final var graph = networkBlock.getGraph();
		var nodes = new HashMap<Integer, Node>();
		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			final var v = graph.newNode(t);
			nodes.put(t - 1, v);
			graph.addTaxon(v, t);
			graph.setLabel(v, taxaBlock.get(t).getDisplayLabelOrName());
		}

		try (var r = new BufferedReader(new FileReader(outputFile))) {
			while (r.ready()) {
				var line = r.readLine();
				if (!line.startsWith("#")) {
					var tokens = StringUtils.split(line, ',');
					if (tokens.length == 3) {
						var i = Integer.parseInt(tokens[0]);
						var j = Integer.parseInt(tokens[1]);
						var weight = Double.parseDouble(tokens[2]);
						var v = nodes.computeIfAbsent(i, k -> graph.newNode());
						var w = nodes.computeIfAbsent(j, k -> graph.newNode());
						var e = graph.newEdge(v, w);
						graph.setWeight(e, weight);
					}
				}
			}
		}
		if (!isOptionKeepTmpFiles()) {
			FileUtils.deleteFileIfExists(inputFile, outputFile);
		}

		var parent = distancesBlock.getNode().getPreferredParent();
		if (parent != null && parent.getPreferredParent() != null && parent.getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock) {
			graph.nodeStream().filter(v -> graph.getNumberOfTaxa(v) == 1).forEach(v -> {
				var row = graph.getTaxon(v) - 1;
				var sequence = String.valueOf(charactersBlock.getRow0(row));
				networkBlock.getNodeData(v).put(NetworkBlock.NODE_STATES_KEY, sequence);
			});

			for (var e : graph.edges()) {
				var sequence1 = networkBlock.getNodeData(e.getSource()).get(NetworkBlock.NODE_STATES_KEY);
				var sequence2 = networkBlock.getNodeData(e.getTarget()).get(NetworkBlock.NODE_STATES_KEY);
				if (sequence1 != null && sequence2 != null) {
					networkBlock.getEdgeData(e).put(NetworkBlock.EDGE_SITES_KEY, computeEdgeLabel(sequence1, sequence2));
				}
			}
		}

		for (var e : graph.edges()) {
			networkBlock.getEdgeData(e).put("weight", StringUtils.removeTrailingZerosAfterDot(graph.getWeight(e)));
		}

		CheckPairwiseDistances.apply(graph, distancesBlock, 0.000001);
	}

	private static String computeEdgeLabel(String sequence1, String sequence2) {
		var buf = new StringBuilder();
		for (var i = 0; i < sequence1.length(); i++) {
			if (Character.toLowerCase(sequence1.charAt(i)) != Character.toLowerCase(sequence2.charAt(i))) {
				if (!buf.isEmpty())
					buf.append(",");
				buf.append(i + 1);
			}
		}
		return buf.toString();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, DistancesBlock distancesBlock) {
		return true;
	}

	public String getOptionExecutablePath() {
		return optionExecutablePath.get();
	}

	public StringProperty optionExecutablePathProperty() {
		return optionExecutablePath;
	}

	public void setOptionExecutablePath(String optionExecutablePath) {
		this.optionExecutablePath.set(optionExecutablePath);
	}

	public String getOptionCallFormat() {
		return optionCallFormat.get();
	}

	public StringProperty optionCallFormatProperty() {
		return optionCallFormat;
	}

	public String getOptionTmpDirectory() {
		return optionTmpDirectory.get();
	}

	public StringProperty optionTmpDirectoryProperty() {
		return optionTmpDirectory;
	}

	public boolean isOptionKeepTmpFiles() {
		return optionKeepTmpFiles.get();
	}

	public BooleanProperty optionKeepTmpFilesProperty() {
		return optionKeepTmpFiles;
	}

	public static void runExternalProgramAndWait(ProgressListener progressListener, String... command) throws IOException {
		System.err.println("Running: " + StringUtils.toString(command, " "));

		var processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);
		var process = processBuilder.start();
		var errorService = Executors.newFixedThreadPool(1);
		errorService.submit(() -> {
			var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			try {
				while (reader.ready()) {
					System.err.println(reader.readLine());
				}
			} catch (IOException ignored) {
			}
		});
		var outputService = Executors.newFixedThreadPool(1);
		outputService.submit(() -> {
			var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			try {
				while (reader.ready()) {
					System.out.println(reader.readLine());
				}
			} catch (IOException ignored) {
			}
		});

		var service = Executors.newFixedThreadPool(1);
		if (true)
			service.submit(() -> {
				while (true) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException ignored) {
					}
					if (progressListener.isUserCancelled()) {
						process.destroyForcibly();
						return;
					}
				}
			});

		// Wait for the process to complete
		try {
			int exitCode = -1;
			try {
				exitCode = process.waitFor();
			} catch (InterruptedException ignored) {
			}
			if (exitCode != 0) {
				var error = "external '" + command[0] + "' exited with code: " + exitCode;
				NotificationManager.showError(error);
				System.err.println(error);
			} else {
				var message = "external '" + command[0] + ": completed successfully";
				NotificationManager.showInformation(message);
				System.err.println(message);
			}
		} finally {
			errorService.shutdownNow();
			outputService.shutdownNow();
			service.shutdownNow();
		}
		progressListener.checkForCancel();
	}
}
