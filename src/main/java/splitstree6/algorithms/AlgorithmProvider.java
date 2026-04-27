/*
 * AlgorithmProvider.java Copyright (C) 2026 Daniel H. Huson
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

// splitstree6.algorithms.AlgorithmProvider (new file)
package splitstree6.algorithms;

import splitstree6.workflow.Algorithm;

import java.util.List;
import java.util.Map;

public interface AlgorithmProvider {
	/**
	 * Returns additional algorithms to register with SplitsTree6.
	 */
	List<Algorithm<?, ?>> getAlgorithms();

	/**
	 * Returns true if this provider contributes an algorithm with the given simple class name.
	 */
	default boolean provides(String simpleClassName) {
		return getAlgorithms().stream()
				.anyMatch(a -> a.getClass().getSimpleName().equals(simpleClassName));
	}

	/**
	 * Create and configure an algorithm by name. Return null if this provider
	 * does not handle the given name. Parameters are interpretation-defined.
	 */
	default Algorithm<?, ?> createAlgorithm(String simpleClassName, Map<String, String> parameters) {
		return null;
	}

}