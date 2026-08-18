/*
 * RunPhyloFusion.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools;

import jloda.util.FileUtils;
import jloda.util.PeakMemoryUsageMonitor;
import jloda.util.ProgramExecutorService;
import jloda.util.UsageException;
import jloda.util.progress.ProgressPercentage;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.trees.trees2trees.PhyloFusion;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.trees.NewickReader;

import java.io.IOException;
import java.util.ArrayList;

public class RunPhyloFusion {
	public static void main(String[] args) throws IOException, UsageException {
		System.err.println("RunPhyloFusion");

		if(args.length != 2) {
			throw new UsageException("RunPhyloFusion input outfile");
		}

		ProgramExecutorService.setNumberOfCoresToUse(8);

		var inputFiles=new ArrayList<String>();
		if(FileUtils.isDirectory(args[0])) {
			inputFiles.addAll(FileUtils.getAllFilesInDirectory(args[0],false,".new"));
			inputFiles.sort((a,b)->{
				if(a.contains("L20") && !b.contains("L20")) {
					return -1;
				} else if(b.contains("L20") && !a.contains("L20")) {
					return 1;
				} if(a.contains("L50") && !b.contains("L50")) {
					return -1;
				} else if(b.contains("L50") && !a.contains("L50")) {
					return 1;
				}
				else return a.compareTo(b);
			});
			System.err.println("Input dir '"+args[0]+"': "+inputFiles.size()+" files");
		} else {
			inputFiles.add(args[0]);
			System.err.println("Input file '"+args[0]+"'");
		}
		var outputFile = args[1];
		System.err.println("Output file '"+outputFile+"'");

		try(var w=FileUtils.getOutputWriterPossiblyZIPorGZIP(outputFile)) {
			for (var inputFile : inputFiles) {
				var start=System.currentTimeMillis();

				var phyloFusion = new PhyloFusion();
				phyloFusion.setOptionMutualRefinement(true);
				phyloFusion.setOptionEdgeWeights(PhyloFusion.EdgeWeights.None);
				phyloFusion.setOptionOnlyOneNetwork(true);

				var taxaBlock = new TaxaBlock();
				var treesBlock = new TreesBlock();
				NewickReader newickReader = new NewickReader();
				newickReader.read(new ProgressSilent(), inputFile, taxaBlock, treesBlock);

				System.err.printf("Taxa: %d, trees: %d%n", taxaBlock.getNtax(), treesBlock.getNTrees());

				var networkBlock = new TreesBlock();
				phyloFusion.compute(new ProgressSilent(), taxaBlock, treesBlock, networkBlock);
				var seconds = Math.round((System.currentTimeMillis()-start)/1000.0);

				var network = networkBlock.getTree(1);

				var h = network.nodeStream().filter(v -> v.getInDegree() > 1).mapToInt(v -> v.getInDegree() - 1).sum();
				var info="PhyloFusion\t%s\t%d\t%s%n".formatted(FileUtils.getFileNameWithoutPathOrSuffix(inputFile), h, seconds);
				w.write(info);
				System.err.printf(info);
				w.write(network.toBracketString(false) + ";\n");
			}
		}
		System.exit(0);
	}
}
