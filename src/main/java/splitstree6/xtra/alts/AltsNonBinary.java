/*
 *  AltsNonBinary.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.alts;

import jloda.fx.util.ArgsOptions;
import jloda.fx.util.ProgramExecutorService;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import jloda.util.UsageException;
import jloda.util.progress.ProgressPercentage;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.trees.TreesReader;
import splitstree6.io.writers.trees.NewickWriter;

import java.io.IOException;

public class AltsNonBinary {
	public static void main(String[] args) throws UsageException, IOException {
		var options = new ArgsOptions(args, AltsNonBinary.class, "Non-binary version of ALTS");
		var infile = options.getOptionMandatory("-i", "input", "Input Newick file", "");
		var outfile = options.getOption("-o", "output", "Output Newick file (.gz or stdout ok)", "stdout");
		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-t", "threads", "Number of threads to use", 4));

		options.done();

		var taxaBlock = new TaxaBlock();
		var inputTreesBlock = new TreesBlock();
		loadTrees(infile, taxaBlock, inputTreesBlock);

		// copy trees to output:
		var treesBlock = new TreesBlock();
		for (var tree : inputTreesBlock.getTrees()) {
			treesBlock.getTrees().add(new PhyloTree(tree));
		}

		System.err.println("Writing: " + outfile);
		try (var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(outfile)) {
			(new NewickWriter()).write(w, taxaBlock, treesBlock);
		}
	}

	public static void loadTrees(String fileName, TaxaBlock taxaBlock, TreesBlock treesBlock) throws IOException {
		var importManager = ImportManager.getInstance();
		var dataType = importManager.getDataType(fileName);
		if (dataType.equals(TreesBlock.class)) {
			var fileFormat = importManager.getFileFormat(fileName);
			var importer = (TreesReader) importManager.getImporterByDataTypeAndFileFormat(dataType, fileFormat);
			try (var progress = new ProgressPercentage("Reading: " + fileName)) {
				importer.read(progress, fileName, taxaBlock, treesBlock);
			}
			System.err.println("Trees: " + treesBlock.getNTrees());
		} else throw new IOException("File does not contain trees");

	}
}
