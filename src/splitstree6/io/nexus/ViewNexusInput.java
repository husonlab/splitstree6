/*
 * ViewNexusInput.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;

import java.io.IOException;
import java.util.ArrayList;

/**
 * view nexus input
 * Daniel Huson, 11.2021
 */
public class ViewNexusInput extends NexusIOBase {
	public static final String SYNTAX = """
			BEGIN VIEW;
				[TITLE title;]
				[LINK {type} = {title};]
				NAME <name>;
				INPUT <input-block-name>;
				[OPTIONS
					<name>=<value>,
					...
					<name>=<value>
				;]
			END;
			""";

	public String getSyntax() {
		return SYNTAX;
	}

	/**
     * parse a view block
     */
    public void parse(NexusStreamParser np, TaxaBlock taxaBlock, ViewBlock viewBlock) throws IOException {
        final var taxonNamesFound = new ArrayList<String>();

        np.matchBeginBlock("VIEW");
        parseTitleAndLink(np);

        np.matchIgnoreCase("NAME");
        viewBlock.setName(np.getLabelRespectCase());
        np.matchIgnoreCase(";");
        np.matchIgnoreCase("INPUT");
        viewBlock.setInputBlockName(np.getWordMatchesIgnoringCase("TAXA TRAITS CHARACTERS DISTANCES SPLITS TREES NETWORK"));
		np.matchIgnoreCase(";");

		if (np.peekMatchIgnoreCase("OPTIONS")) {
			viewBlock.setInitializationLines(StringUtils.toString(np.getTokensRespectCase("OPTIONS", ";"), " "));
		}
		np.matchEndBlock();

    }
}
