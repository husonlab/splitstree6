/*
 * NeXMLExporter.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.io.writers.trees;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * export in NeXML format
 * Daria Evseeva, 2019
 */
public class NeXMLWriter extends TreesWriterBase {
	private static XMLOutputFactory xmlOutputFactory;

	public NeXMLWriter() {
		setFileExtensions("xml", "nexml");
	}

	private static synchronized XMLOutputFactory getXMLFactory() {
		if (xmlOutputFactory == null)
			xmlOutputFactory = XMLOutputFactory.newFactory();
		return xmlOutputFactory;
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, TreesBlock trees) throws IOException {
		try {
			final XMLStreamWriter xmlWriter = createXMLStreamWriter(w);
			writeStart(xmlWriter);
			export(xmlWriter, taxa);
			export(xmlWriter, taxa, trees);
			writeEnd(xmlWriter);
			xmlWriter.close();
		} catch (XMLStreamException xmlEx) {
			throw new IOException(xmlEx);
		}
	}

	private void writeStart(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
		xmlStreamWriter.writeStartDocument();
		writeNewLineWithTabs(xmlStreamWriter, 0);
		xmlStreamWriter.writeStartElement("nex:nexml");
		xmlStreamWriter.writeAttribute("generator", "SplitsTree6");
		xmlStreamWriter.writeAttribute("version", "0.9");
		xmlStreamWriter.writeNamespace("nex", "http://www.nexml.org/2009");
		xmlStreamWriter.writeDefaultNamespace("http://www.nexml.org/2009");
		xmlStreamWriter.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
		xmlStreamWriter.writeNamespace("sp5", "https://github.com/danielhuson/splitstree6");
		//w.write("\n\t");
		//xmlStreamWriter.writeNamespace("xml", "http://www.w3.org/XML/1998/namespace");
		writeNewLineWithTabs(xmlStreamWriter, 0);
		xmlStreamWriter.flush();
	}

	private void writeEnd(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
		writeNewLineWithTabs(xmlStreamWriter, 0);
		xmlStreamWriter.writeEndElement(); // nex
		xmlStreamWriter.writeEndDocument();
	}

	private void export(XMLStreamWriter xmlWriter, TaxaBlock taxa) throws XMLStreamException {
		writeNewLineWithTabs(xmlWriter, 1);
		xmlWriter.writeStartElement("otus");
		xmlWriter.writeAttribute("id", "otus1");
		xmlWriter.writeAttribute("label", "TaxaBlock");

		for (Taxon taxon : taxa.getTaxa()) {
			writeNewLineWithTabs(xmlWriter, 2);
			xmlWriter.writeEmptyElement("otu");
			xmlWriter.writeAttribute("id", "otu" + taxa.indexOf(taxon));
			xmlWriter.writeAttribute("label", taxon.getName());
		}
		writeNewLineWithTabs(xmlWriter, 1);
		xmlWriter.writeEndElement(); // otus
		writeNewLineWithTabs(xmlWriter, 1);
		xmlWriter.flush();
	}

	private void export(XMLStreamWriter xmlWriter, TaxaBlock taxa, TreesBlock trees) throws XMLStreamException {
		writeNewLineWithTabs(xmlWriter, 1);
		xmlWriter.writeStartElement("trees");
		xmlWriter.writeAttribute("otus", "otus1");
		xmlWriter.writeAttribute("id", "trees1");
		xmlWriter.writeAttribute("label", "TreesBlock");

		int treesCounter = 0;
		int nodesCounter = 0;
		int edgesCounter = 0;

		for (PhyloTree tree : trees.getTrees()) {
			treesCounter++;
			final Map<Integer, Integer> nodeId2externalId = new HashMap<>();

			writeNewLineWithTabs(xmlWriter, 2);
			xmlWriter.writeStartElement("tree");
			xmlWriter.writeAttribute("id", "tree" + treesCounter);
			xmlWriter.writeAttribute("label", "tree" + treesCounter);
			xmlWriter.writeAttribute("xsi:type", "nex:FloatTree");


			for (Node v : tree.nodes()) {
				writeNewLineWithTabs(xmlWriter, 3);
				xmlWriter.writeEmptyElement("node");
				final int externalId = (++nodesCounter);
				nodeId2externalId.put(v.getId(), externalId);
				xmlWriter.writeAttribute("id", "n" + externalId);
				if (StringUtils.notBlank(tree.getLabel(v)))
					xmlWriter.writeAttribute("label", tree.getLabel(v));
				if (tree.getRoot() != null && tree.getRoot().equals(v))
					xmlWriter.writeAttribute("root", "true");
				if (v.isLeaf()) {
					final int taxonId = tree.getTaxa(v).iterator().next();
					xmlWriter.writeAttribute("otu", "otu" + taxonId);
				}
			}

			for (Edge edge : tree.edges()) {
				writeNewLineWithTabs(xmlWriter, 3);
				xmlWriter.writeEmptyElement("edge");
				xmlWriter.writeAttribute("source", "n" + nodeId2externalId.get(edge.getSource().getId()));
				xmlWriter.writeAttribute("target", "n" + nodeId2externalId.get(edge.getTarget().getId()));
				xmlWriter.writeAttribute("id", "e" + ++edgesCounter);
				xmlWriter.writeAttribute("length", tree.getWeight(edge) + "");
			}
			writeNewLineWithTabs(xmlWriter, 2);
			xmlWriter.writeEndElement(); // tree
		}
		writeNewLineWithTabs(xmlWriter, 1);
		xmlWriter.writeEndElement(); //trees
	}

	private void writeNewLineWithTabs(XMLStreamWriter xmlStreamWriter, int numTabs) throws XMLStreamException {
		xmlStreamWriter.writeCharacters("\n");
		for (int i = 0; i < numTabs; i++)
			xmlStreamWriter.writeCharacters("\t");
	}

	private static XMLStreamWriter createXMLStreamWriter(Writer w) throws XMLStreamException {
		return getXMLFactory().createXMLStreamWriter(w);
	}
}
