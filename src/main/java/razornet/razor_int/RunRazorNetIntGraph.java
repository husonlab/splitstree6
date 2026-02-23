/*
 * RunRazorNetIntGraph.java Copyright (C) 2026 Daniel H. Huson
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

package razornet.razor_int;

import razornet.utils.CanceledException;
import razornet.utils.TriConsumer;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntToDoubleFunction;

/**
 * Dummy entry point for the (non-public) RazorNet algorithm.
 * <p>
 * This is a stub used to let dependent projects compile publicly.
 * The real implementation lives in the private RazorNet package.
 */
public final class RunRazorNetIntGraph {

	private RunRazorNetIntGraph() {
	}

	/**
	 * Referenced by Splitstree code.
	 */
	public static int NUMBER_OF_PARALLEL_PROCESSES = 1;

	/**
	 * Signature shaped to match the call from razornetaccess.RazorNet.
	 * <p>
	 * This dummy implementation does not construct a network; it simply ensures
	 * nodes exist (optionally) and returns.
	 *
	 * @param ensureNode      callback to ensure a graph node exists for an id
	 * @param mapDistanceBack maps integer-encoded distances back to doubles (unused here)
	 * @param newEdgeInteger  callback to create a new edge (unused here)
	 * @param matrix          integer distance matrix
	 * @param polish          unused
	 * @param removeRedundant unused
	 * @param maxRounds       unused
	 * @param verbose         unused
	 * @param progress        opaque progress object (kept as Object to avoid depending on private types)
	 * @param warningConsumer optional warning sink
	 */
	public static void run(IntConsumer ensureNode,
						   IntToDoubleFunction mapDistanceBack,
						   TriConsumer<Integer, Integer, Integer> newEdgeInteger,
						   int[][] matrix,
						   boolean polish,
						   boolean removeRedundant,
						   int maxRounds,
						   boolean verbose,
						   Object progress,
						   Consumer<String> warningConsumer) throws CanceledException {

		Objects.requireNonNull(ensureNode, "ensureNode");
		Objects.requireNonNull(mapDistanceBack, "mapDistanceBack");
		Objects.requireNonNull(newEdgeInteger, "newEdgeInteger");

		// Minimal behavior: ensure all nodes for 0..n-1 exist, no edges added.
		final int n = (matrix == null ? 0 : matrix.length);
		for (int i = 0; i < n; i++) {
			ensureNode.accept(i);
		}

		if (warningConsumer != null) {
			warningConsumer.accept("RazorNet stub in use: no network edges were generated.");
		}
		throw new UnsupportedOperationException("RazorNet is not available in the public build.");
	}
}