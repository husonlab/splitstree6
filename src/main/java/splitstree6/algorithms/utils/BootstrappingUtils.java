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
import splitstree6.data.CharactersBlock;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;

import java.io.IOException;
import java.util.ArrayList;
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
