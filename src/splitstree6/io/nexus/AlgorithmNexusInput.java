/*
 * AlgorithmNexusInput.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.io.nexus;

import jloda.util.Basic;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.PluginClassLoader;
import jloda.util.parse.NexusStreamParser;
import splitstree6.options.OptionIO;
import splitstree6.workflow.Algorithm;

import java.io.IOException;

/**
 * algorithm nexus input
 * Daniel Huson, 2.2018
 */
public class AlgorithmNexusInput extends NexusIOBase {
	public static final String SYNTAX = """
			BEGIN ALGORITHM;
				[TITLE <title>;]
				[LINK <parent-block-type> = <parent-title>;]
				NAME <name>;
				[OPTIONS
					<name>=<value>,
					...
					<name>=<value>
				;]
			END;
			""";

	/**
	 * get syntax
	 */
	public String getSyntax() {
		return SYNTAX;
	}

	/**
	 * parse and create an algorithm
	 *
	 */
	public Algorithm parse(NexusStreamParser np) throws IOException {
		np.matchBeginBlock("ALGORITHM");
		parseTitleAndLink(np);

		np.matchAnyTokenIgnoreCase("NAME ALGORITHM"); // ALGORITHM for SplitsTree5 compatiblity
		final String algorithmName = np.getWordRespectCase().replaceAll(" ", "");
		np.matchIgnoreCase(";");

		final Algorithm algorithm = createAlgorithmFromName(algorithmName);
		if (algorithm == null)
			throw new IOExceptionWithLineNumber("Unknown algorithm: " + algorithmName, np.lineno());

		if (np.peekMatchIgnoreCase("OPTIONS")) {
			try {
				OptionIO.parseOptions(np, algorithm);
			} catch (IOException ex) {
				Basic.caught(ex);
				throw ex;
			}
		}
		np.matchEndBlock();
		return algorithm;
	}

	/**
	 * creates an instance of the named algorithm
	 *
	 * @return instance or null
	 */
	public static Algorithm createAlgorithmFromName(String algorithmName) {
		var algorithms = PluginClassLoader.getInstances(algorithmName, Algorithm.class, null, "splitstree6.algorithms");
		if (algorithms.size() > 0)
			return algorithms.get(0);
		else
			return null;
	}

	/**
	 * is the parser at the beginning of a block that this class can parse?
	 *
	 * @return true, if can parse from here
	 */
	public boolean atBeginOfBlock(NexusStreamParser np) {
		return np.peekMatchIgnoreCase("begin ALGORITHM;");
	}
}
