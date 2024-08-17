/*
 *  ALTSExternal.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.fx.util.ProgramProperties;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IExperimental;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.*;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * this runs an external trees to trees program
 * Daniel Huson, 8.2024
 */
public class ExternalTrees2Trees extends Trees2Trees implements IExperimental {
	private final StringProperty optionExecutable = new SimpleStringProperty(this, "optionExecutable", "");
	private final BooleanProperty optionWeights = new SimpleBooleanProperty(this, "optionWeights", true);


	{
		ProgramProperties.track(optionExecutable, "");
	}

	@Override
	public String getShortDescription() {
		return "Runs an external program.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionExecutable.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		if (optionName.equals(optionExecutable.getName())) {
			return "Path to program that reads a file of Newick strings and writes a file of Newick strings.";
		} else if (optionName.equals(optionWeights.getName())) {
			return "Provide tree edge weights";
		} else

			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		if (getOptionExecutable().isBlank())
			throw new IOException("Please set executable file");
		var tmp = String.valueOf(System.currentTimeMillis() & ((1 << 20) - 1));
		var executable = new File(getOptionExecutable());
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
					if (IteratorUtils.size(tree0.getTaxa()) == taxaBlock.getNtax() && !treesBlock.isReticulated()) {
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

			var start = System.currentTimeMillis();
			runExternalProgramAndWait(progress, executable.getAbsolutePath(), inputFile, outFile);
			System.err.printf("%.1fs%n", (System.currentTimeMillis() - start) / 1000.0);

			var number = 0;
			try (var r = new BufferedReader(new FileReader(outFile))) {
				while (r.ready()) {
					var line = r.readLine();
					var tree = new PhyloTree();
					tree.parseBracketNotation(line, true);
					tree.setName("in-%03d".formatted(++number));
					tree.edgeStream().filter(e -> e.getTarget().getInDegree() > 1).forEach(e -> tree.setReticulate(e, true));
					outputBlock.getTrees().add(tree);
					if (outputBlock.isReticulated() && tree.isReticulated())
						outputBlock.setReticulated(true);
				}
			}
		} finally {
			FileUtils.deleteFileIfExists(inputFile);
			FileUtils.deleteFileIfExists(outFile);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock treesBlock) {
		return true;
	}

	public String getOptionExecutable() {
		return optionExecutable.get();
	}

	public StringProperty optionExecutableProperty() {
		return optionExecutable;
	}

	public void setOptionExecutable(String optionExecutable) {
		this.optionExecutable.set(optionExecutable);
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
				System.err.println(command[0] + "' exited with code: " + exitCode);
			else
				System.err.println(command[0] + ": done");
		} finally {
			errorService.shutdownNow();
			outputService.shutdownNow();
			service.shutdownNow();
		}
		progressListener.checkForCancel();
	}
}
