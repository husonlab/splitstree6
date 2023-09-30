/*
 * NexmlTreesHandler.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.readers.trees.utils;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * nexml tree handler
 * Daria Evseeva, 2019, Daniel Huson, 2020
 */
public class NexmlTreesHandler extends DefaultHandler {

	// tree 't1'=[&R] (((3:0.234,2:0.3243):0.324,(5:0.32443,4:0.2342):0.3247):0.34534,1:0.4353);
	private boolean bReadingTree = false;
	private boolean partial = false;
	private boolean rooted = false;

	private PhyloTree tree;
	private final ArrayList<String> treeOTUs = new ArrayList<>();
	private final Map<String, Integer> otu2taxonId = new HashMap<>();
	private final ArrayList<PhyloTree> trees = new ArrayList<>();
	private HashMap<String, Node> id2node = new HashMap<>();
	private final ArrayList<String> taxaLabels = new ArrayList<>();

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

		if (qName.equalsIgnoreCase("otus")) {
			String label = attributes.getValue("label");
			String id = attributes.getValue("id");
			//System.out.println("Label : " + label);
			//System.out.println("ID : " + id);
		} else if (qName.equalsIgnoreCase("otu")) {
			//otu = true;
			final String otu = attributes.getValue("id");
			final String label = attributes.getValue("label");

			if (!otu2taxonId.containsKey(otu)) {
				taxaLabels.add(label);
				otu2taxonId.put(otu, taxaLabels.size());
			}
		}
		// TREES INFO
		else if (qName.equalsIgnoreCase("tree")) {
			tree = new PhyloTree();
			treeOTUs.clear();
			id2node = new HashMap<>();
			bReadingTree = true;
		} else if (qName.equalsIgnoreCase("node") && bReadingTree) {
			String label = attributes.getValue("label");
			String id = attributes.getValue("id");
			String otu = attributes.getValue("otu");
			boolean root = Boolean.parseBoolean(attributes.getValue("root"));

			final Node v = tree.newNode();
			if (root) {
				tree.setRoot(v);
				rooted = true;
			}
			id2node.put(id, v);

			{
				if (label != null)
					tree.setLabel(v, label);
				if (otu2taxonId.containsKey(otu)) {
					tree.addTaxon(v, otu2taxonId.get(otu));
					treeOTUs.add(otu);
				}
			}

		} else if (qName.equalsIgnoreCase("rootEdge") && bReadingTree) {
			final double weight = (NumberUtils.isDouble(attributes.getValue("length")) ? NumberUtils.parseDouble(attributes.getValue("length")) : 1.0);
			final Node sourceNode = tree.newNode();
			final Node targetNode = tree.getRoot();
			tree.setRoot(sourceNode);
			tree.setWeight(tree.newEdge(sourceNode, targetNode), weight);
		} else if (qName.equalsIgnoreCase("edge") && bReadingTree) {
			final double weight = (NumberUtils.isDouble(attributes.getValue("length")) ? NumberUtils.parseDouble(attributes.getValue("length")) : 1.0);
			final String id = attributes.getValue("id");
			final String source = attributes.getValue("source");
			final String target = attributes.getValue("target");

			Node sourceNode = null;
			Node targetNode = null;

			for (String key : id2node.keySet()) {
				if (key.equals(source)) {
					sourceNode = id2node.get(key);
					if (targetNode != null)
						break;
				}
				if (key.equals(target)) {
					targetNode = id2node.get(key);
					if (sourceNode != null)
						break;
				}
			}

			if (sourceNode == null)
				throw new SAXException("Edge " + id + " contains not defined source node id=" + source);
			else if (targetNode == null)
				throw new SAXException("Edge " + id + " contains not defined target node id=" + target);
			else {
				tree.setWeight(tree.newEdge(sourceNode, targetNode), weight);
			}
		}
	}

	@Override
    public void endElement(String uri, String localName, String qName) {
        if (qName.equalsIgnoreCase("otus")) {
            //System.out.println("End Element :" + qName);
        } else if (qName.equalsIgnoreCase("tree")) {
            bReadingTree = false;
            trees.add(tree);

            // if a tree already set as partial, no further check
            if (partial || otu2taxonId.size() != treeOTUs.size())
                partial = true;

			treeContainsAllTaxa(tree);
		}
	}

	public ArrayList<String> getTaxaLabels() {
		return this.taxaLabels;
	}

	public ArrayList<PhyloTree> getTrees() {
		return this.trees;
	}

	public boolean isPartial() {
		return this.partial;
	}

	public boolean isRooted() {
		return this.rooted;
	}

    private void treeContainsAllTaxa(PhyloTree tree) {
        int numOfLabelsInTree = 0;

        for (var v : tree.nodes()) {
            if (v.getLabel() != null)
                numOfLabelsInTree++;
        }

        // todo : fix this
    }

	public static Collection<String> makeCollection(Iterable<String> iter) {
        Collection<String> list = new ArrayList<>();
		for (String item : iter) {
			list.add(item);
		}
		return list;
	}
}
