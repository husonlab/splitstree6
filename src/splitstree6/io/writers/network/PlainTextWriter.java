/*
 * PlainTextWriter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.writers.network;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * write block in text format
 * Daniel Huson, 11.2021
 */
public class PlainTextWriter extends NetworkWriterBase {
	private final BooleanProperty optionShowAllDetails = new SimpleBooleanProperty(this, "optionShowAllDetails", false);

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, NetworkBlock dataBlock) throws IOException {
		var graph = dataBlock.getGraph();
		for (var v : graph.nodes()) {
			w.write("" + v.getId());
			if (v.getLabel() != null)
				w.write(" tax=" + v.getLabel());
			if (isOptionShowAllDetails()) {
				var label = StringUtils.toString(dataBlock.getNodeData(v).entrySet(), " ");
				if (!label.isBlank())
					w.write(" " + label);
			}
			w.write("\n");
		}
		w.write("#\n");
		for (var e : graph.edges()) {
			w.write(e.getSource().getId() + " " + e.getTarget().getId());
			w.write(" wgt=" + graph.getWeight(e));
			if (isOptionShowAllDetails()) {
				var label = StringUtils.toString(dataBlock.getEdgeData(e).entrySet(), " ");
				if (!label.isBlank())
					w.write(" " + label);
			}
			w.write("\n");
		}
	}

	public boolean isOptionShowAllDetails() {
		return optionShowAllDetails.get();
	}

	public BooleanProperty optionShowAllDetailsProperty() {
		return optionShowAllDetails;
	}

	public void setOptionShowAllDetails(boolean optionShowAllDetails) {
		this.optionShowAllDetails.set(optionShowAllDetails);
	}
}
