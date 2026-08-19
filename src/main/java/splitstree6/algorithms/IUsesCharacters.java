/*
 *  IUsesCharacters.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms;

import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.DataBlock;

import java.io.IOException;

/**
 * an algorithm that can additionally use the characters the input was derived from
 * <p>
 * Some algorithms want more than their declared input. A minimum-spanning network takes
 * distances, but labels its nodes and edges with the sequences behind those distances if it
 * can find them; the bootstrap algorithms take splits or trees, but resample the original
 * alignment. Inside the application they find that data by walking back up the workflow -
 * {@code getNode().getPreferredParent()}, or {@code getNode().getOwner()} for the whole
 * workflow - which works there and throws a NullPointerException everywhere else, because a
 * block that was never put in a workflow has no node.
 * <p>
 * An algorithm that implements this offers a second {@code compute} taking the characters
 * explicitly. Its four-argument {@code compute} does the workflow archaeology and delegates,
 * so the application is unaffected; a caller that already has the characters - a test, a
 * command-line tool, a language binding - calls the five-argument form and needs no workflow
 * at all. The characters may be null, and an implementation must cope: for these algorithms
 * the extra data is an enrichment, not a requirement.
 * <p>
 * Daniel Huson, 8.2026
 *
 * @param <S> input data
 * @param <T> output data
 */
public interface IUsesCharacters<S extends DataBlock, T extends DataBlock> {
	/**
	 * run, using the given characters rather than looking for them in the workflow
	 *
	 * @param charactersBlock the characters the input was derived from, or null if there are none
	 */
	void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData, CharactersBlock charactersBlock) throws IOException;

	/**
	 * the characters a data block was derived from, by walking two steps back up the workflow
	 * <p>
	 * Two steps because the shape is characters -> [algorithm] -> distances: the data block's
	 * preferred parent is the algorithm node that made it, and that node's preferred parent is
	 * the data it was made from.
	 *
	 * @return the characters, or null if there is no workflow or nothing of that type in it
	 */
	static CharactersBlock findCharacters(DataBlock dataBlock) {
		if (dataBlock != null && dataBlock.getNode() != null) {
			var algorithmNode = dataBlock.getNode().getPreferredParent();
			if (algorithmNode != null && algorithmNode.getPreferredParent() != null
				&& algorithmNode.getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock)
				return charactersBlock;
		}
		return null;
	}
}
