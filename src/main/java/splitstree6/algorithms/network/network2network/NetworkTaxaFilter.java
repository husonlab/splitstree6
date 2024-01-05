/*
 * NetworkTaxaFilter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.network.network2network;

import jloda.graph.Node;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.DataTaxaFilter;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * removes nodes from graph
 */
public class NetworkTaxaFilter extends DataTaxaFilter<NetworkBlock, NetworkBlock> {
	public NetworkTaxaFilter() {
		super(NetworkBlock.class, NetworkBlock.class);
	}

	public NetworkTaxaFilter(Class<NetworkBlock> fromClass, Class<NetworkBlock> toClass) {
		super(fromClass, toClass);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, NetworkBlock inputData, NetworkBlock outputData) {
		var labels = new HashSet<>(modifiedTaxaBlock.getLabels());

		outputData.copy(inputData);
		var toDelete = new ArrayList<Node>();
		var graph = outputData.getGraph();
		for (var v : graph.nodes()) {
			var label = graph.getLabel(v);
			if (label != null && !labels.contains(label))
				toDelete.add(v);
		}
		for (var v : toDelete)
			graph.deleteNode(v);

		setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " taxa");
	}
}
