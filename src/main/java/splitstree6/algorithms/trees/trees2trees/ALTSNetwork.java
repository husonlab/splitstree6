/*
 *  ALTSNetwork.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.phylo.LSAUtils;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IDesktopOnly;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * this runs the ALTSNetwork algorithm externally
 * Daniel Huson, 7.2023
 */
public class ALTSNetwork extends Trees2Trees implements IDesktopOnly {
	private final StringProperty optionALTSExecutableFile = new SimpleStringProperty(this, "optionALTSExecutableFile", "");

	{
		ProgramProperties.track(optionALTSExecutableFile, "");
	}

	@Override
	public String getCitation() {
		return "Zhang et al 2023; Louxin Zhang, Niloufar Niloufar Abhari, Caroline Colijn and Yufeng Wu3." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionALTSExecutableFile.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionALTSExecutableFile.getName())) {
			return "Download and compile ALTS program from https://github.com/LX-Zhang/AAST, then set this parameter to the executable.\n" +
				   "Note that the program requires fully resolved trees as input and any unresolved trees will be ignored";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		if (getOptionALTSExecutableFile().isBlank())
			throw new IOException("Please set executable file");
		var tmp = String.valueOf(System.currentTimeMillis() & ((1 << 20) - 1));
		var executable = new File(getOptionALTSExecutableFile());
		FileUtils.checkFileReadableNonEmpty(executable.getAbsolutePath());
		//var dir = executable.getParentFile();
		var dir = new File(System.getProperty("java.io.tmpdir"));
		var inputFile = dir.getAbsolutePath() + File.separator + "tmp" + tmp + "input.tre";
		FileUtils.checkFileWritable(inputFile, true);
		var outFile = (dir.getParent().isBlank() ? "" : dir.getAbsolutePath() + File.separator) + "tmp" + tmp + "output.txt";
		FileUtils.checkFileWritable(outFile, true);
		var newickIO = new NewickIO();

		try {
			var count = 0;
			try (var w = new BufferedWriter(new FileWriter(inputFile))) {
				for (var tree0 : treesBlock.getTrees()) {
					if (IteratorUtils.size(tree0.getTaxa()) == taxaBlock.getNtax() && !treesBlock.isReticulated() && tree0.isBifurcating()) {
						var tree = new PhyloTree(tree0);
						for (var v : tree.nodes()) {
							if (tree.getLabel(v) != null) {
								if (tree.getTaxon(v) > 0) {
									tree.setLabel(v, String.valueOf(tree.getTaxon(v)));
								} else
									tree.setLabel(v, "");
							}
						}
						w.write(newickIO.toBracketString(tree, false) + ";\n");
						count++;
					}
				}
			}
			if (count < 2)
				throw new IOException("Not enough full, non-reticulated, bifurcating trees in input: " + count);

			if (count < treesBlock.getNTrees())
				NotificationManager.showWarning("Ignoring input trees have missing taxa, reticulations or multi-furcations; using %,d of %d".formatted(count, treesBlock.getNTrees()));

			runExternalProgramAndWait(progress, executable.getAbsolutePath(), inputFile, String.valueOf(taxaBlock.getNtax()), outFile);

			var data = new ArrayList<ArrayList<String>>();
			var networkNumber = -1;
			try (var r = new BufferedReader(new FileReader(outFile))) {
				while (r.ready()) {
					var line = r.readLine();
					if (line.contains("==//")) {
						networkNumber++;
						data.add(new ArrayList<>());
					}
					if (!line.contains("=="))
						data.get(networkNumber).add(line);
				}
			}

			{
				var pattern = Pattern.compile("\\d+");
				for (ArrayList<String> list : data) {
					var network = new PhyloTree();
					var idNodeMap = new HashMap<Integer, Node>();
					for (var line : list) {
						var matcher = pattern.matcher(line);
						var a = -1;
						if (matcher.find()) {
							a = Integer.parseInt(matcher.group());
						}
						var b = -1;
						if (matcher.find()) {
							b = Integer.parseInt(matcher.group());
						}
						if (a >= 0 && b >= 0) {
							var v = idNodeMap.computeIfAbsent(a, k -> network.newNode());
							if (a == 0)
								network.setRoot(v);
							var w = idNodeMap.computeIfAbsent(b, k -> network.newNode());
							if (b >= 1 && b <= taxaBlock.getNtax()) {
								network.addTaxon(w, b);
								network.setLabel(w, taxaBlock.get(b).getName());
							}
							if (!v.isChild(w))
								network.newEdge(v, w);
						}
					}

					for (var e : network.edges()) {
						if (e.getTarget().getInDegree() > 1)
							network.setReticulate(e, true);
					}
					if (network.isReticulated())
						outputBlock.setReticulated(true);
					LSAUtils.computeLSAChildrenMap(network, network.newNodeArray());

					if (false)
						System.err.println(newickIO.toBracketString(network, false) + ";");

					outputBlock.getTrees().add(network);
				}
			}
			outputBlock.setPartial(false);
		} finally {
			FileUtils.deleteFileIfExists(inputFile);
			FileUtils.deleteFileIfExists(outFile);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock treesBlock) {
		return true;
	}

	public String getOptionALTSExecutableFile() {
		return optionALTSExecutableFile.get();
	}

	public StringProperty optionALTSExecutableFileProperty() {
		return optionALTSExecutableFile;
	}

	public void setOptionALTSExecutableFile(String optionALTSExecutableFile) {
		this.optionALTSExecutableFile.set(optionALTSExecutableFile);
	}

	public static void runExternalProgramAndWait(ProgressListener progressListener, String... command) throws IOException {
		System.err.println("Running: " + StringUtils.toString(command, " "));

		var processBuilder = new ProcessBuilder(command);
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
			if (exitCode != 0)
				System.err.println("alts '" + command[0] + "' exited with code: " + exitCode);
			else
				System.err.println("alts: done");
		} finally {
			errorService.shutdownNow();
			outputService.shutdownNow();
			service.shutdownNow();
		}
		progressListener.checkForCancel();
	}
}
