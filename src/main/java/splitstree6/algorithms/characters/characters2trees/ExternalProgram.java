/*
 *  ExternalProgram.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2trees;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.fx.util.ProgramProperties;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.trees.TreesReader;
import splitstree6.io.writers.characters.FastAWriter;
import splitstree6.io.writers.characters.NexusWriter;
import splitstree6.io.writers.characters.PhylipWriter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExternalProgram extends Characters2Trees {

	public enum CharactersFormat {Phylip, Nexus, FastA}

	private final ObjectProperty<CharactersFormat> optionCharactersFormat = new SimpleObjectProperty<>(this, "optionCharactersFormat");
	private final StringProperty optionProgramCall = new SimpleStringProperty(this, "optionProgramCall");
	private final StringProperty optionName = new SimpleStringProperty(this, "optionName");

	{
		ProgramProperties.track(optionProgramCall, "path-to-program %i %o");
		ProgramProperties.track(optionCharactersFormat, CharactersFormat::valueOf, CharactersFormat.Phylip);
		ProgramProperties.track(optionName, "external");
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionName.getName(), optionProgramCall.getName(), optionCharactersFormat.getName());
	}

	@Override
	public String getShortDescription() {
		return "Runs an external program.";
	}

	@Override
	public String getToolTip(String option) {
		if (option.equals(optionProgramCall.getName()))
			return "Specification of external program: replace 'path-to-program' by path to program and\nuse '%i' and '%o' as place-holders for the program's input and output files";
		else if (option.equals(optionCharactersFormat.getName()))
			return "Specify the format to write out the current data in";
		else if (option.equals(optionName.getName()))
			return "Specify a name for this calculation";
		else
			return null;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, TreesBlock treesBlock) throws IOException {
		if (getOptionProgramCall().startsWith("path-to-program"))
			throw new IOException("No program set");

		var inFile = File.createTempFile("input", "chars");
		inFile.deleteOnExit();
		var outFile = File.createTempFile("output", "tre");
		outFile.deleteOnExit();

		progress.setTasks("Writing file", inFile.getName());
		try (var w = new FileWriter(inFile)) {
			var exporter = switch (getOptionCharactersFormat()) {
				case Phylip -> new PhylipWriter();
				case FastA -> new FastAWriter();
				case Nexus -> new NexusWriter();
			};
			exporter.write(w, taxaBlock, charactersBlock);
		}

		var command = getOptionProgramCall().replaceAll("%i", inFile.getPath()).replaceAll("%o", outFile.getPath());

		progress.setTasks("Running command", command);
		progress.setProgress(-1);


		var processBuilder = new ProcessBuilder();
		processBuilder.command(command.split("\\s"));
		System.err.println("Running: " + StringUtils.toString(processBuilder.command(), " "));
		var process = processBuilder.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
		try (var r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var all = r.lines().collect(Collectors.toList());
			if (all.size() > 0) {
				System.out.println(StringUtils.toString(all, "\n"));
			}
		}
		try (var r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			var all = r.lines().collect(Collectors.toList());
			if (all.size() > 0) {
				System.err.println(StringUtils.toString(all, "\n"));
				throw new IOException(all.get(0) + (all.size() == 1 ? "." : "..."));
			}
		}

		if (!FileUtils.fileExistsAndIsNonEmpty(outFile))
			throw new IOException("External program's output file does not exist or is empty");

		progress.setTasks("Reading file", outFile.getName());
		var importers = ImportManager.getInstance().getReaders(outFile.getPath());
		if (importers.size() == 1 && importers.get(0) instanceof TreesReader importer) {
			importer.read(progress, outFile.getPath(), taxaBlock, treesBlock);
			for (var t = 1; t <= treesBlock.getNTrees(); t++) {
				var tree = treesBlock.getTree(t);
				if (treesBlock.getNTrees() == 1)
					tree.setName(getOptionName());
				else
					tree.setName(getOptionName() + "-" + t);
				for (var v : tree.nodes()) {
					tree.clearTaxa(v);
					var label = tree.getLabel(v);
					if (label != null) {
						var taxId = taxaBlock.indexOf(label);
						if (taxId > 0)
							tree.addTaxon(v, taxId);
					}
				}
			}
		} else
			throw new IOException("Can't determine of return file: " + outFile);

		savePreset(getClass(), getOptionName(), getOptionProgramCall(), getOptionCharactersFormat().name());
	}

	public String getOptionProgramCall() {
		return optionProgramCall.get();
	}

	public StringProperty optionProgramCallProperty() {
		return optionProgramCall;
	}

	public void setOptionProgramCall(String optionProgramCall) {
		this.optionProgramCall.set(optionProgramCall);
	}

	public CharactersFormat getOptionCharactersFormat() {
		return optionCharactersFormat.get();
	}

	public ObjectProperty<CharactersFormat> optionCharactersFormatProperty() {
		return optionCharactersFormat;
	}

	public void setOptionCharactersFormat(CharactersFormat optionCharactersFormat) {
		this.optionCharactersFormat.set(optionCharactersFormat);
	}

	public String getOptionName() {
		return optionName.get();
	}

	public StringProperty optionNameProperty() {
		return optionName;
	}

	public void setOptionName(String optionName) {
		this.optionName.set(optionName);
	}

	/**
	 * saves a name and all its parts
	 *
	 * @param clazz used to generate class-specify entry
	 * @param name  name of feature
	 * @param parts parts of feature
	 */
	public void savePreset(Class<?> clazz, String name, String... parts) {
		var key = clazz.getSimpleName() + "Presets";
		var list = new ArrayList<String>();
		for (var item : ProgramProperties.get(key, new String[0])) {
			var tokens = item.split("____");
			if (tokens.length > 0) {
				var aName = tokens[0].trim();
				if (!aName.equals(name))
					list.add(item);
			}
		}
		{
			var buf = new StringBuilder();
			buf.append(name);
			for (var part : parts)
				buf.append("____").append(part);
			list.add(buf.toString());
		}
		ProgramProperties.put(key, list.toArray(new String[0]));
	}

	/**
	 * gets a preset or returns null, if not defined
	 *
	 * @param clazz used to generate class-specify entry
	 * @param name  name of feature
	 * @return array consisting of name and all the parts
	 */
	public String[] getPreset(Class<?> clazz, String name) {
		var key = clazz.getSimpleName() + "Presets";
		for (var item : ProgramProperties.get(key, new String[0])) {
			var tokens = item.split("____");
			if (tokens.length > 0) {
				var aName = tokens[0].trim();
				if (aName.equals(name)) {
					return tokens;
				}
			}
		}
		return null;
	}
}
