/*
 *  NexusReader.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.readers.network;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.io.GraphGML;
import jloda.util.FileUtils;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class GMLReader extends NetworkReader {

	public GMLReader() {
		setFileExtensions("gml");
	}

	@Override
	public void read(ProgressListener progress, String fileName, TaxaBlock taxaBlock, NetworkBlock networkBlock) throws IOException {
		var graph = networkBlock.getGraph();

		var labelNodeValueMap = new HashMap<String, Map<Node, String>>();
		var labelEdgeValueMap = new HashMap<String, Map<Edge, String>>();

		try (var reader = FileUtils.getReaderPossiblyZIPorGZIP(fileName)) {
			var gmlInfo = GraphGML.readGML(reader, graph, labelNodeValueMap, labelEdgeValueMap);
			if (gmlInfo.label() != null)
				graph.setName(gmlInfo.label());
		}

		// parse taxa:
		{
			var labelTaxonMap = new HashMap<String, Integer>();
			var taxonLabelMap = new TreeMap<Integer, String>();
			if (labelNodeValueMap.containsKey("label")) {
				for (var entry : labelNodeValueMap.get("label").entrySet()) {
					var v = entry.getKey();
					var name = entry.getValue();
					var count = 0;
					while (labelTaxonMap.containsKey(name)) {
						name = entry.getValue() + (++count);
					}
					var t = labelTaxonMap.size() + 1;
					labelTaxonMap.put(name, t);
					taxonLabelMap.put(t, name);
					graph.setLabel(v, name);
					graph.addTaxon(v, t);
				}
			} else throw new IOException("No labeled nodes found");
			taxaBlock.addTaxaByNames(taxonLabelMap.values());
		}

		// copy all  node annotations
		for (var v : graph.nodes()) {
			var data = networkBlock.getNodeData(v);
			for (var key : labelNodeValueMap.keySet()) {
				var map = labelNodeValueMap.get(key);
				var value = map.get(v);
				if (value != null) {
					data.put(key, value);
				}
			}
		}

		// parse weights
		if (labelEdgeValueMap.containsKey("weight")) {
			var weightMap = labelEdgeValueMap.get("weight");
			for (var e : graph.edges()) {
				var value = weightMap.get(e);
				if (NumberUtils.isDouble(value)) {
					graph.setWeight(e, NumberUtils.parseDouble(value));
				}
			}
		}

		// copy all edge annotations
		for (var e : graph.edges()) {
			var data = networkBlock.getEdgeData(e);
			for (var key : labelEdgeValueMap.keySet()) {
				var map = labelEdgeValueMap.get(key);
				var value = map.get(e);
				if (value != null) {
					data.put(key, value);
				}
			}
		}
	}

	@Override
	public boolean accepts(String fileName) {
		if (!super.accepts(fileName))
			return false;
		else {
			return acceptsFile(fileName);
		}
	}

	public boolean acceptsFirstLine(String text) {
		return StringUtils.getFirstLine(text).toLowerCase().startsWith("graph [");
	}
}
