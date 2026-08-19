/*
 *  BootstrappingUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.util.Pair;
import splitstree6.algorithms.splits.splits2splits.BootstrapSplits;
import splitstree6.algorithms.trees.trees2splits.TreeSelectorSplits;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

/**
 * Perform bootstrapping and transfer bootstrapping
 * Daniel Huson, 6.2023
 */
public class BootstrappingUtils {

	/**
	 * creates a bootstrap replicate
	 *
	 * @param charactersBlock characters
	 * @param random          random number generator
	 * @return bootstrap replicate
	 */
	public static CharactersBlock createReplicate(CharactersBlock charactersBlock, Random random) {
		final var srcMatrix = charactersBlock.getMatrix();
		final var numRows = srcMatrix.length;
		final var numCols = srcMatrix[0].length;
		final var tarMatrix = new char[numRows][numCols];
		for (var col = 0; col < numCols; col++) {
			var randomCol = random.nextInt(numCols);
			for (var row = 0; row < numRows; row++) {
				tarMatrix[row][col] = srcMatrix[row][randomCol];
			}
		}
		return new CharactersBlock(charactersBlock, tarMatrix);
	}

	/**
	 * get's the path of algorithms and datanodes from the working datanode to the target datanode
	 *
	 * @param workingDataNode
	 * @param target
	 * @return
	 */
	public static ArrayList<Pair<Algorithm, DataBlock>> extractPath(DataNode<? extends DataBlock> workingDataNode, DataNode target) throws IOException {
		var list = new ArrayList<Pair<Algorithm, DataBlock>>();

		var dataNode = target;
		while (dataNode != workingDataNode) {
			if (dataNode.getPreferredParent() == null)
				throw new IOException("Algorithm path not found");
			var algorithmNode = dataNode.getPreferredParent();
			list.add(0, new Pair<>(algorithmNode.getAlgorithm(), dataNode.getDataBlock().newInstance()));
			dataNode = algorithmNode.getPreferredParent();
		}
		return list;
	}

	/**
	 * builds a supplier of pipelines from an explicitly stated chain of algorithms
	 * <p>
	 * This is the counterpart of {@link #extractPath} for a caller that has no workflow. Where that
	 * recovers the chain that actually produced a result, this takes the chain the caller states, and
	 * hands back exactly what was asked for. Nothing here can check that it is the chain that produced
	 * the bootstrap's input - a caller naming a different one gets support values for a computation
	 * nobody performed, and they will look entirely plausible - so what can be checked is: that the
	 * chain starts at the alignment, that each step accepts what the step before it produces, and that
	 * it ends in the block the bootstrap aggregates.
	 * <p>
	 * The algorithms are shared between the pipelines this supplies, exactly as extractPath shares
	 * them: they carry the caller's option settings, which a fresh instance would not. Only the output
	 * buffers are new on each call, and they must be, because each worker thread gets its own pipeline.
	 *
	 * @param algorithms  the chain, in the order applied, the first one taking characters
	 * @param targetClass the block the pipeline must end in: splits for {@link BootstrapSplits} and
	 *                    BootstrapTreeSplits, trees for BootstrapTree. A chain producing trees where
	 *                    splits are required is completed with a tree selector, which is what the
	 *                    workflow entry points do
	 * @return a supplier of fresh pipelines, one per worker thread
	 */
	public static BootstrapSplits.PathSupplier newPathSupplier(Collection<? extends Algorithm> algorithms, Class<? extends DataBlock> targetClass) throws IOException {
		var chain = new ArrayList<Algorithm>(algorithms);
		if (chain.isEmpty())
			throw new IOException("Bootstrapping: empty pipeline, expected the algorithms leading from the characters to the input");

		if (!chain.get(0).getFromClass().isAssignableFrom(CharactersBlock.class))
			throw new IOException("Bootstrapping: pipeline must start at CharactersBlock, but " + chain.get(0).getName()
								  + " takes " + chain.get(0).getFromClass().getSimpleName());
		for (var i = 1; i < chain.size(); i++) {
			var previous = chain.get(i - 1);
			var current = chain.get(i);
			if (!current.getFromClass().isAssignableFrom(previous.getToClass()))
				throw new IOException("Bootstrapping: pipeline does not compose, " + previous.getName() + " produces "
									  + previous.getToClass().getSimpleName() + " but " + current.getName() + " takes "
									  + current.getFromClass().getSimpleName());
		}

		var last = chain.get(chain.size() - 1);
		// the workflow route appends a tree selector when the chain it recovered ends in trees and the
		// aggregation needs splits; do the same here, so that both routes bootstrap the same pipeline
		var selectTree = targetClass.equals(SplitsBlock.class) && TreesBlock.class.isAssignableFrom(last.getToClass());
		if (!selectTree && !targetClass.isAssignableFrom(last.getToClass()))
			throw new IOException("Bootstrapping: pipeline must end in " + targetClass.getSimpleName() + ", but "
								  + last.getName() + " produces " + last.getToClass().getSimpleName());

		return () -> {
			var path = new ArrayList<Pair<Algorithm, DataBlock>>();
			for (var algorithm : chain) {
				path.add(new Pair<>(algorithm, DataBlock.newInstance(algorithm.getToClass())));
			}
			if (selectTree) {
				// a fresh one per pipeline, not one shared instance appended to the chain above: it
				// rewrites its own optionWhich against the block it is handed
				path.add(new Pair<>(new TreeSelectorSplits(), new SplitsBlock()));
			}
			return path;
		};
	}

	/**
	 * return a overview of path
	 *
	 * @param characters input characters
	 * @param path       path of algorithms and data
	 * @return string
	 */
	public static String toString(CharactersBlock characters, ArrayList<Pair<Algorithm, DataBlock>> path) {
		var buf = new StringBuilder();

		DataBlock inputData = characters;
		buf.append(inputData.getBlockName());

		for (var pair : path) {
			var algorithm = pair.getFirst();
			var outputData = pair.getSecond();
			buf.append(" -> ").append(algorithm.getName()).append(" -> ").append(inputData.getBlockName());
			inputData = outputData;
		}
		return buf.toString();
	}
}
