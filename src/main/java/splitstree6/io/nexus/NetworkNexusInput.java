/*
 *  NetworkNexusInput.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.*;

/**
 * network block nexus input
 * Daniel Huson, 2.2018
 */
public class NetworkNexusInput extends NexusIOBase implements INexusInput<NetworkBlock> {
	public static final String SYNTAX = """
			BEGIN NETWORK;
			    [TITLE {title};]
				[LINK {type} = {title};]
				[DIMENSIONS [NVertices=number-of-nodes] [NEdges=number-of-edges];]
					[TYPE {HaplotypeNetwork|Points|Other};]
				[FORMAT
				;]
				[PROPERTIES
					[info =' information string to be shown with plot']
				;]
				VERTICES
					ID=number [LABEL=label] [x=number] [y=number] [key=value ...],
					...
					ID=number [LABEL=label] [x=number] [y=number] [key=value ...]
				;
				EDGES
					ID=number SID=number TID=number [LABEL=label] [key=value ...],
					...
					ID=number SID=number TID=number [LABEL=label] [key=value ...]
				;
			END;
			""";

	public static final String DESCRIPTION = "Maintain a network, such as a haplotype network or just a set of points (for PCoA).\n";

	@Override
	public String getSyntax() {
		return SYNTAX;
	}

	/**
	 * parse a network block
	 */
	@Override
	public List<String> parse(NexusStreamParser np, TaxaBlock taxaBlock, NetworkBlock networkBlock) throws IOException {
		networkBlock.clear();

		final var taxonNamesFound = new ArrayList<String>();

		np.matchBeginBlock("NETWORK");
		parseTitleAndLink(np);

		np.matchIgnoreCase("dimensions");
		final int nNodes;
		final int nEdges;
		if (np.peekMatchIgnoreCase("nTax=")) {
			if (taxaBlock.getNtax() == 0) {
				np.matchIgnoreCase("nTax=");
				taxaBlock.setNtax(np.getInt());
			} else {
				np.matchIgnoreCase("nTax=" + taxaBlock.getNtax());
			}
			np.matchAnyTokenIgnoreCase("nVertices nNodes"); // nNodes deprecated
			np.matchIgnoreCase("=");
			nNodes = np.getInt(0, Integer.MAX_VALUE);
			np.matchIgnoreCase("nEdges=");
			nEdges = np.getInt(0, Integer.MAX_VALUE);
			np.matchIgnoreCase(";");
			return readSplitsTree4(np, nNodes, nEdges, taxaBlock, networkBlock);
		} else {
			np.matchAnyTokenIgnoreCase("nVertices nNodes");  // nNodes deprecated
			np.matchIgnoreCase("=");
			nNodes = np.getInt(0, Integer.MAX_VALUE);
			np.matchIgnoreCase("nEdges=");
			nEdges = np.getInt(0, Integer.MAX_VALUE);
		}
		np.matchIgnoreCase(";");


		if (np.peekMatchIgnoreCase("TYPE")) {
			np.matchIgnoreCase("TYPE");
			if (np.peekMatchIgnoreCase("="))
				np.matchIgnoreCase("="); // backward compatibility
			var typeString = np.getWordRespectCase().toUpperCase();
			var type = StringUtils.valueOfIgnoreCase(NetworkBlock.Type.class, typeString);
			if (type == null)
				throw new IOExceptionWithLineNumber("Unknown network type: " + typeString, np.lineno());
			networkBlock.setNetworkType(type);
			np.matchIgnoreCase(";");
		}

		if (np.peekMatchIgnoreCase("FORMAT")) {
			np.matchIgnoreCase("FORMAT");
			np.matchIgnoreCase(";");
		}

		if (np.peekMatchIgnoreCase("PROPERTIES")) {
			np.matchIgnoreCase("PROPERTIES");
			if (np.peekMatchIgnoreCase("info")) {
				np.matchIgnoreCase("info=");
				networkBlock.setInfoString(np.getWordRespectCase());
			}
			np.matchIgnoreCase(";");
		}

		final var graph = networkBlock.getGraph();
		final var id2node = new TreeMap<Integer, Node>();

		np.matchAnyTokenIgnoreCase("VERTICES NODES"); // nodes deprecated

		{
			var first = true;
			for (var i = 0; i < nNodes; i++) {
				if (first)
					first = false;
				else
					np.matchIgnoreCase(",");

				np.matchIgnoreCase("id=");
				var id = np.getInt();
				if (id2node.containsKey(id))
					throw new IOExceptionWithLineNumber("Multiple occurrence of node id: " + id, np.lineno());

				var v = graph.newNode();
				id2node.put(id, v);

				while (!np.peekMatchAnyTokenIgnoreCase(", ;")) {
					var key = np.getWordRespectCase();
					np.matchIgnoreCase("=");
					var value = np.getWordRespectCase();
					networkBlock.getNodeData(v).put(key, value);
					if (key.equals("tid") && NumberUtils.isInteger(value) && !taxaBlock.getLabels().isEmpty()) {
						var taxId = NumberUtils.parseInt(value);
						if (taxId <= 0 || taxId > taxaBlock.getNtax())
							throw new IOExceptionWithLineNumber("Invalid tax id: " + taxId, np.lineno());
						graph.addTaxon(v, taxId);
						graph.setLabel(v, taxaBlock.getLabel(taxId));
					} else if (key.equals("label")) {
						graph.setLabel(v, value);
						if (taxaBlock.getLabels().isEmpty()) {
							taxonNamesFound.add(value);
							graph.addTaxon(v, taxonNamesFound.size());
						} else {
							graph.addTaxon(v, taxaBlock.indexOf(value));
						}
					}
				}
			}
		}
		np.matchIgnoreCase(";");

		final var id2edge = new TreeMap<Integer, Edge>();

		np.matchIgnoreCase("EDGES");
		{
			var first = true;
			for (var i = 0; i < nEdges; i++) {
				if (first)
					first = false;
				else
					np.matchIgnoreCase(",");

				np.matchIgnoreCase("id=");
				var id = np.getInt();
				if (id2edge.containsKey(id))
					throw new IOExceptionWithLineNumber("Multiple occurrence of edge id: " + id, np.lineno());

				np.matchIgnoreCase("sid=");
				var sid = np.getInt();
				if (!id2node.containsKey(sid))
					throw new IOExceptionWithLineNumber("Unknown node id: " + sid, np.lineno());

				np.matchIgnoreCase("tid=");
				var tid = np.getInt();
				if (!id2node.containsKey(tid))
					throw new IOExceptionWithLineNumber("Unknown node id: " + tid, np.lineno());

				final var source = id2node.get(sid);
				final var target = id2node.get(tid);

				final var e = graph.newEdge(source, target);
				id2edge.put(id, e);

				if (np.peekMatchIgnoreCase("label")) {
					np.matchIgnoreCase("label=");
					graph.setLabel(e, np.getWordRespectCase());
				}
				while (!np.peekMatchAnyTokenIgnoreCase(", ;")) {
					var key = np.getWordRespectCase();
					np.matchIgnoreCase("=");
					var value = np.getWordRespectCase();
					networkBlock.getEdgeData(e).put(key, value);
					if (key.equals("weight") && NumberUtils.isDouble(value)) {
						networkBlock.getGraph().setWeight(e, NumberUtils.parseDouble(value));
					}
					if (key.equals("confidence") && NumberUtils.isDouble(value)) {
						networkBlock.getGraph().setConfidence(e, Double.parseDouble(value));
					}
					if (key.equals("probability") && NumberUtils.isDouble(value)) {
						networkBlock.getGraph().setProbability(e, Double.parseDouble(value));
					}
				}
			}
		}
		np.matchIgnoreCase(";");
		np.matchEndBlock();

		if (!graph.isConnected()) {
			NotificationManager.showError("Network is not connected");
			createStar(taxaBlock, graph);
		}

		return taxonNamesFound;
	}

	/**
	 * reads in a network block in SplitsTree4 format
	 */
	private static List<String> readSplitsTree4(NexusStreamParser np, int nVertices, int nEdges, TaxaBlock taxaBlock, NetworkBlock networkBlock) throws IOException {
		var taxonNamesFound = new ArrayList<String>();

		var graph = networkBlock.getGraph();

		if (np.peekMatchIgnoreCase("draw")) {
			np.getWordsRespectCase("draw", ";");// skip draw
		}

		var nodeId2TaxonLabel = new HashMap<Integer, String>();

		if (np.peekMatchIgnoreCase("translate")) {
			np.matchIgnoreCase("translate");
			while (!np.peekMatchIgnoreCase(";")) {
				var id = np.getInt();
				var label = np.getLabelRespectCase();

				if (nodeId2TaxonLabel.containsKey(id))
					throw new IOExceptionWithLineNumber("Repeated id", np.lineno());
				nodeId2TaxonLabel.put(id, label);
				if (taxaBlock.getLabels().isEmpty()) {
					taxonNamesFound.add(label);
				}

				while (!np.peekMatchIgnoreCase(",") && !np.peekMatchIgnoreCase(";"))
					np.getWordRespectCase();
				if (np.peekMatchIgnoreCase(","))
					np.matchIgnoreCase(",");
			}
			np.matchIgnoreCase(";");
		}

		var nodeId2Node = new HashMap<Integer, Node>();
		{
			np.matchIgnoreCase("vertices");
			for (var i = 1; i <= nVertices; i++) {
				var v = graph.newNode();

				var id = np.getInt();
				if (nodeId2Node.containsKey(id))
					throw new IOExceptionWithLineNumber("Repeated id", np.lineno());
				var x = np.getDouble();
				var y = np.getDouble();

				networkBlock.getNodeData(v).put(NetworkBlock.NodeData.BasicKey.x.name(), String.valueOf(x));
				networkBlock.getNodeData(v).put(NetworkBlock.NodeData.BasicKey.y.name(), String.valueOf(y));
				nodeId2Node.put(id, v);
				graph.setLabel(v, nodeId2TaxonLabel.get(id));

				while (!np.peekMatchIgnoreCase(",") && !np.peekMatchIgnoreCase(";"))
					np.getWordRespectCase();
				if (np.peekMatchIgnoreCase(","))
					np.matchIgnoreCase(",");
			}
			np.matchIgnoreCase(";");
		}

		if (np.peekMatchIgnoreCase("vLabels")) {
			np.matchIgnoreCase("vLabels");
			while (!np.peekMatchIgnoreCase(";")) {
				var id = np.getInt();
				var label = np.getLabelRespectCase();

				var nodeData = networkBlock.getNodeData(nodeId2Node.get(id));
				nodeData.put(NetworkBlock.NodeData.BasicKey.label.name(), label);

				while (!np.peekMatchIgnoreCase(",") && !np.peekMatchIgnoreCase(";"))
					np.getWordRespectCase();
				if (np.peekMatchIgnoreCase(","))
					np.matchIgnoreCase(",");
			}
			np.matchIgnoreCase(";");
		}

		{
			np.matchIgnoreCase("EDGES");
			var found = new BitSet();
			for (var i = 0; i < nEdges; i++) {
				var id = np.getInt();
				if (found.get(id))
					throw new IOExceptionWithLineNumber("Multiple occurrence of edge id: " + id, np.lineno());
				else
					found.set(id);

				var vId = np.getInt();
				var wId = np.getInt();
				var e = graph.newEdge(nodeId2Node.get(vId), nodeId2Node.get(wId));

				if (np.peekMatchIgnoreCase("s=")) {
					np.matchIgnoreCase("s=");
					networkBlock.getEdgeData(e).put("s", String.valueOf(np.getInt()));
				}
				if (np.peekMatchIgnoreCase("w=")) {
					np.matchIgnoreCase("w=");
					networkBlock.getEdgeData(e).put("w", String.valueOf(np.getDouble()));
				}

				while (!np.peekMatchIgnoreCase(",") && !np.peekMatchIgnoreCase(";"))
					np.getWordRespectCase();
				if (np.peekMatchIgnoreCase(","))
					np.matchIgnoreCase(",");
			}
			np.matchIgnoreCase(";");
		}
		np.matchEndBlock();
		return taxonNamesFound;
	}

	private static void createStar(TaxaBlock taxaBlock, PhyloGraph graph) {
		graph.clear();
		var center = graph.newNode();
		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			var v = graph.newNode();
			graph.addTaxon(v, t);
			var e = graph.newEdge(center, v);
			graph.setWeight(e, 1.0);
		}
	}
}
