/*
 * NetworkNexusInput.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.io.nexus;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.IOExceptionWithLineNumber;
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
				[DIMENSIONS [NNODES=number-of-nodes] [NEDGES=number-of-edges];]
					[NETWORK={HaplotypeNetwork|Other};]
				[FORMAT
				;]
				[PROPERTIES
				;]
				NODES
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

		final ArrayList<String> taxonNamesFound = new ArrayList<>();

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
			np.matchIgnoreCase("nVertices=");
			nNodes = np.getInt(0, Integer.MAX_VALUE);
			np.matchIgnoreCase("nEdges=");
			nEdges = np.getInt(0, Integer.MAX_VALUE);
			np.matchIgnoreCase(";");
			return readSplitsTree4(np, nNodes, nEdges, taxaBlock, networkBlock);
		} else {
			np.matchIgnoreCase("nNodes=");
			nNodes = np.getInt(0, Integer.MAX_VALUE);
			np.matchIgnoreCase("nEdges=");
			nEdges = np.getInt(0, Integer.MAX_VALUE);
		}
		np.matchIgnoreCase(";");


		if (np.peekMatchIgnoreCase("TYPE")) {
			np.matchIgnoreCase("TYPE=");
			String typeString = np.getWordRespectCase().toUpperCase();
			NetworkBlock.Type type = StringUtils.valueOfIgnoreCase(NetworkBlock.Type.class, typeString);
			if (type == null)
				throw new IOExceptionWithLineNumber("Unknown network type: " + typeString, np.lineno());
			networkBlock.setNetworkType(type);
			np.matchIgnoreCase(";");
		}

		if (np.peekMatchIgnoreCase("FORMAT")) {
			np.matchIgnoreCase("FORMAT");
			np.matchIgnoreCase(":");
		}

		if (np.peekMatchIgnoreCase("PROPERTIES")) {
			np.matchIgnoreCase("PROPERTIES");
			np.matchIgnoreCase(":");
		}

		final PhyloGraph graph = networkBlock.getGraph();

		final Map<Integer, Node> id2node = new TreeMap<>();

		np.matchIgnoreCase("NODES");
		{
			boolean first = true;
			for (int i = 0; i < nNodes; i++) {
				if (first)
					first = false;
				else
					np.matchIgnoreCase(",");

				np.matchIgnoreCase("id=");
				final int id = np.getInt();
				if (id2node.containsKey(id))
					throw new IOExceptionWithLineNumber("Multiple occurrence of node id: " + id, np.lineno());

				final Node v = graph.newNode();
				id2node.put(id, v);

				if (np.peekMatchIgnoreCase("label")) {
					np.matchIgnoreCase("label=");
					graph.setLabel(v, np.getWordRespectCase());
					if (taxaBlock.getLabels().size() == 0) {
						taxonNamesFound.add(graph.getLabel(v));
					}
				}
				while (!np.peekMatchAnyTokenIgnoreCase(", ;")) {
					String key = np.getWordRespectCase();
					np.matchIgnoreCase("=");
					String value = np.getWordRespectCase();
					networkBlock.getNodeData(v).put(key, value);
				}
			}
		}
		np.matchIgnoreCase(";");

		final Map<Integer, Edge> id2edge = new TreeMap<>();

		np.matchIgnoreCase("EDGES");
		{
			boolean first = true;
			for (int i = 0; i < nEdges; i++) {
				if (first)
					first = false;
				else
					np.matchIgnoreCase(",");

				np.matchIgnoreCase("id=");
				final int id = np.getInt();
				if (id2edge.containsKey(id))
					throw new IOExceptionWithLineNumber("Multiple occurrence of edge id: " + id, np.lineno());

				np.matchIgnoreCase("sid=");
				final int sid = np.getInt();
				if (!id2node.containsKey(sid))
					throw new IOExceptionWithLineNumber("Unknown node id: " + sid, np.lineno());

				np.matchIgnoreCase("tid=");
				final int tid = np.getInt();
				if (!id2node.containsKey(tid))
					throw new IOExceptionWithLineNumber("Unknown node id: " + tid, np.lineno());

				final Node source = id2node.get(sid);
				final Node target = id2node.get(tid);

				final Edge e = graph.newEdge(source, target);
				id2edge.put(id, e);

				if (np.peekMatchIgnoreCase("label")) {
					np.matchIgnoreCase("label=");
					graph.setLabel(e, np.getWordRespectCase());
				}
				while (!np.peekMatchAnyTokenIgnoreCase(", ;")) {
					String key = np.getWordRespectCase();
					np.matchIgnoreCase("=");
					String value = np.getWordRespectCase();
					networkBlock.getEdgeData(e).put(key, value);
				}
			}
		}
		np.matchIgnoreCase(";");
		np.matchEndBlock();

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
				if (taxaBlock.getLabels().size() == 0) {
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
			for (int i = 1; i <= nVertices; i++) {
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
}
