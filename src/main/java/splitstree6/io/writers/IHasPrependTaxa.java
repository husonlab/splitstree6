/*
 *  IHasPrependTaxa.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.writers;

import javafx.beans.property.BooleanProperty;
import splitstree6.data.TaxaBlock;
import splitstree6.io.nexus.TraitsNexusOutput;

import java.io.IOException;
import java.io.Writer;

public interface IHasPrependTaxa {
	BooleanProperty optionPrependTaxaProperty();

	/**
	 * writes the prepended TAXA block (with the #nexus header) and, if it exists, the associated TRAITS block, so
	 * that trait annotations are not lost when a data block is exported with the "prepend taxa" option turned on.
	 */
	static void writePrependedTaxa(Writer w, TaxaBlock taxa) throws IOException {
		new splitstree6.io.writers.taxa.NexusWriter(true).write(w, taxa, taxa);
		var traits = taxa.getTraitsBlock();
		if (traits != null && traits.getNTraits() > 0)
			new TraitsNexusOutput().write(w, taxa, traits);
	}
}
