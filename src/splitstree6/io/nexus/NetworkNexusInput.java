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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

		np.matchIgnoreCase("dimensions nNodes=");
		final int nNodes = np.getInt(0, Integer.MAX_VALUE);
		np.matchIgnoreCase("nEdges=");
		final int nEdges = np.getInt(0, Integer.MAX_VALUE);
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
	 * is the parser at the beginning of a block that this class can parse?
	 *
	 * @return true, if can parse from here
	 */
	public boolean atBeginOfBlock(NexusStreamParser np) {
		return np.peekMatchIgnoreCase("begin NETWORK;");
	}
}
