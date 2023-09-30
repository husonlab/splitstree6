/*
 * TraitsNexusOutput.java Copyright (C) 2023 Daniel H. Huson
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


import jloda.util.BitSetUtils;
import jloda.util.StringUtils;
import splitstree6.data.SetsBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;
import java.util.BitSet;

/**
 * traits nexus output
 * Daniel Huson, 2.2018
 */
public class SetsNexusOutput extends NexusIOBase implements INexusOutput<SetsBlock> {
	/**
	 * write a block in nexus format
	 */
	@Override
	public void write(Writer w, TaxaBlock taxaBlock, SetsBlock setBlock) throws IOException {
		w.write("\nBEGIN SETS;\n");
		writeTitleAndLink(w);
		for (var set : setBlock.getTaxSets()) {
			w.write("\tTAXSET %s = %s;\n".formatted(set.getName(), getRange(set, false)));
		}
		for (var set : setBlock.getCharSets()) {
			w.write("\tCHARSET %s = %s;\n".formatted(set.getName(), getRange(set, true)));
		}
		w.write("END; [SETS]\n");
	}

	public static String getRange(BitSet set, boolean allowModulo3) {
		if (allowModulo3) {
			var min = BitSetUtils.min(set);
			var max = BitSetUtils.max(set);
			var modulo3 = new BitSet();
			for (var t = min; t <= max; t += 3) {
				modulo3.set(t);
			}
			if (set.equals(modulo3))
				return "%d-%d\\3".formatted(min, max);
		}
		return StringUtils.toString(set).replaceAll(",", " ");
	}
}
