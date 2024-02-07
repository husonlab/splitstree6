/*
 *  RunKernelize.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.kernelize;

import jloda.fx.util.ArgsOptions;
import jloda.fx.util.ProgramExecutorService;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.BitSetUtils;
import jloda.util.FileUtils;
import jloda.util.UsageException;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressPercentage;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.trees.TreesReader;
import splitstree6.splits.TreesUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;

public class RunKernelize {
	public static void main(String[] args) throws UsageException, IOException {
		PhyloTree.SUPPORT_RICH_NEWICK = true;

		var options = new ArgsOptions(args, Kernelize.class, "Kernelization");
		var infile = options.getOptionMandatory("-i", "input", "Input Newick file", "");
		var subsetString = options.getOption("-w", "which", "List of input tree indices 1-based, or 0 for all", "0");
		var outfile = options.getOption("-o", "output", "Output Newick file (.gz or stdout ok)", "stdout");
		var maxNumberOfNetworks = options.getOption("-m", "max", "Maximum number of output networks", 1000);

		var algorithmName = options.getOption("-a", "algorithm", "Which algorithm", List.of("trees", "clusternetwork", "alts"), "clusternetwork");
		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-t", "threads", "Number of threads to use", 4));

		options.done();

		// read in trees:
		var taxaBlock = new TaxaBlock();
		var inputTreesBlock = new TreesBlock();

		loadTrees(infile, taxaBlock, inputTreesBlock);

		// select subset:
		var treeIds = new BitSet();
		for (
				var id : BitSetUtils.members(BitSetUtils.valueOf(subsetString))) {
			if (id == 0) {
				for (var i : BitSetUtils.range(1, inputTreesBlock.getNTrees()))
					treeIds.set(i);
			} else if (id > 0 && id <= inputTreesBlock.getNTrees())
				treeIds.set(id);
		}
		System.err.println("Input trees:");

		var inputTrees = new ArrayList<PhyloTree>();
		for (var t : BitSetUtils.members(treeIds)) {
			inputTrees.add(inputTreesBlock.getTree(t));
			System.err.println(NewickIO.toString(inputTreesBlock.getTree(t), false) + ";");
		}
		System.err.println();

		// setup the algorithm that is used to resolve each incompatibility component
		BiFunction<Collection<PhyloTree>, ProgressListener, Collection<PhyloTree>> algorithm =
				switch (algorithmName) {
					case "clusternetwork" -> (trees, p) -> {
						var clusters = new HashSet<BitSet>();
						for (var tree : trees) {
							clusters.addAll(TreesUtils.extractClusters(tree).values());
						}
						var network = new PhyloTree();
						ClusterPoppingAlgorithm.apply(clusters, network);
						return List.of(network);
					};
					case "trees" -> (trees, p) -> trees;
					case "alts" -> (trees, p) -> {
						throw new RuntimeException("--algorithm alts: not implemented");
					};
					default -> throw new RuntimeException("--algorithm " + algorithmName + ": not implemented");
				};

		var networks = Kernelize.apply(new ProgressPercentage(), taxaBlock, inputTrees, algorithm, maxNumberOfNetworks);

		System.err.println("Writing networks (" + networks.size() + "): " + outfile + "\n");
		try (var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(outfile)) {
			for (var network : networks) {
				w.write(NewickIO.toString(network, false) + ";\n");
			}
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
			System.err.println("Taxa:  " + taxaBlock.getNtax());

		} else throw new IOException("File does not contain trees");
	}

}
