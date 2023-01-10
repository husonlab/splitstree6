/*
 *  CoordinatesForRootedNetworkIO.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra;

import javafx.geometry.Point2D;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.graph.io.GraphGML;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;

import java.io.*;
import java.util.HashMap;

/**
 * reads and writes a coordinates for a rooted network
 * Daniel Huson, 8.2022
 */
public class CoordinatesForRootedNetworkIO {
	public static void write(Writer w, PhyloTree tree, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap) throws IOException {
		try (NodeArray<String> nodeXMap = tree.newNodeArray(); NodeArray<String> nodeYMap = tree.newNodeArray();
			 NodeArray<String> nodeAngleStringMap = tree.newNodeArray(); NodeArray<String> nodeLabelMap = tree.newNodeArray()) {
			for (var v : tree.nodes()) {
				var point = nodePointMap.get(v);
				nodeXMap.put(v, "%.5f".formatted(point.getX()));
				nodeYMap.put(v, "%.5f".formatted(point.getY()));
				if (nodeAngleMap.containsKey(v))
					nodeAngleStringMap.put(v, "%.5f".formatted(nodeAngleMap.get(v)));
				nodeLabelMap.put(v, tree.getLabel(v));
			}
			var labelNodeValueMap = new HashMap<String, NodeArray<String>>();
			labelNodeValueMap.put("x", nodeXMap);
			labelNodeValueMap.put("y", nodeYMap);
			labelNodeValueMap.put("angle", nodeAngleStringMap);
			labelNodeValueMap.put("label", nodeLabelMap);
			GraphGML.writeGML(tree, "Rooted network with node coordinates", tree.getName(), true, 1, w, labelNodeValueMap, null);
		}
	}

	public static GraphGML.GMLInfo read(Reader r, PhyloTree tree, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap) throws IOException {
		tree.clear();
		var labelNodeValueMap = new HashMap<String, NodeArray<String>>();
		var gmlInfo = GraphGML.readGML(r, tree, labelNodeValueMap, null);
		tree.setRoot(tree.nodeStream().filter(v -> v.getInDegree() == 0).findAny().orElse(tree.getFirstNode()));
		try (var nodeXMap = labelNodeValueMap.get("x"); var nodeYMap = labelNodeValueMap.get("y");
			 var nodeAngleStringMap = labelNodeValueMap.get("angle");
			 var nodeLabelMap = labelNodeValueMap.get("label")) {
			if (nodeXMap == null)
				throw new IOException("x: not found");
			if (nodeYMap == null)
				throw new IOException("y: not found");
			if (nodeAngleStringMap == null)
				throw new IOException("angle: not found");
			if (nodeLabelMap == null)
				throw new IOException("label: not found");

			for (var v : tree.nodes()) {
				var x = Double.parseDouble(nodeXMap.get(v));
				var y = Double.parseDouble(nodeYMap.get(v));
				nodePointMap.put(v, new Point2D(x, y));
				if (nodeAngleStringMap.containsKey(v) && !nodeAngleStringMap.get(v).equals("null"))
					nodeAngleMap.put(v, Double.parseDouble(nodeAngleStringMap.get(v)));
				tree.setLabel(v, nodeLabelMap.get(v));
			}
		}
		return gmlInfo;
	}

	public static void run(String[] commandLine, PhyloTree tree, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap) throws IOException {
		if (commandLine.length != 3)
			throw new IOException("CommandLine must be: command infile outfile, got: " + StringUtils.toString(commandLine, " "));
		var file1 = new File(commandLine[1]);
		if (file1.exists() && !file1.delete())
			throw new IOException("Failed to delete: " + file1);
		var file2 = new File(commandLine[2]);
		if (file2.exists() && !file2.delete())
			throw new IOException("Failed to delete: " + file2);

		System.err.println("\nWriting file: " + file1);
		try (var w = new BufferedWriter(new FileWriter(file1))) {
			write(w, tree, nodePointMap, nodeAngleMap);
		}
		System.err.println("File size: " + file1.length());

		System.err.println("\nExecuting: " + StringUtils.toString(commandLine, " "));
		var process = Runtime.getRuntime().exec(commandLine);
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new IOException(e);
		}

		System.err.println("\nReading file: " + file2);
		System.err.println("File size: " + file2.length());
		try (var r = new BufferedReader(new FileReader(file2))) {
			read(r, tree, nodePointMap, nodeAngleMap);
		}
	}
}
