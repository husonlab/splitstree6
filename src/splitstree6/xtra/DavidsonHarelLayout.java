/*
 *  DavidsonHarelLayout.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * The Davidson Harel layout algorithm,with the help of ChatGPT
 * Daniel Huson, 6.2023
 */
public class DavidsonHarelLayout {
	private double k = 10.0; // Repulsive force constant
	private double c = 0.1; // Spring force constant
	private double t = 0.1; // Temperature (controls displacement)
	private double dt = 0.01; // Time step
	private int iterations = 100; // Number of iterations
	private int randomSeed = 42;

	public DavidsonHarelLayout() {
	}

	public NodeArray<Point2D> performLayout(Graph graph, Function<Node, Point2D> initialLocations) {
		var nodes = new Node[graph.getNumberOfNodes()];
		var edges = new int[2][graph.getNumberOfEdges()];
		try (var nodeIdMap = graph.newNodeIntArray()) {
			int vId = 0;
			for (var v : graph.nodes()) {
				nodeIdMap.put(v, vId);
				nodes[vId++] = v;
			}
			int eId = 0;
			for (var e : graph.edges()) {
				edges[0][eId] = nodeIdMap.get(e.getSource());
				edges[1][eId] = nodeIdMap.get(e.getTarget());
				eId++;
			}
		}

		// Initialize node positions randomly
		var positions = new Point2D[nodes.length];
		var random = new Random(randomSeed);

		for (var i = 0; i < nodes.length; i++) {
			var v = nodes[i];
			if (initialLocations != null) {
				var point = initialLocations.apply(v);
				if (point != null) {
					positions[i] = point;
					continue;
				}
			}
			positions[i] = new Point2D(random.nextDouble(), random.nextDouble());
		}

		// Perform iterations
		for (int iter = 0; iter < iterations; iter++) {
			// Calculate repulsive forces
			var repulsiveForces = new double[nodes.length][2];
			for (var i = 0; i < nodes.length; i++) {
				for (var j = 0; j < nodes.length; j++) {
					if (i != j) {
						var pi = positions[i];
						var pj = positions[j];
						var dx = pi.getX() - pj.getX();
						var dy = pi.getY() - pj.getY();
						var distanceSquared = dx * dx + dy * dy;
						if (distanceSquared > 0) {
							var distance = Math.sqrt(distanceSquared);
							var force = k * k / distanceSquared;
							repulsiveForces[i][0] += force * dx / distance;
							repulsiveForces[i][1] += force * dy / distance;
						}
					}
				}
			}

			// Calculate spring forces
			var springForces = new double[nodes.length][2];
			for (var edge : edges) {
				var pi = positions[edge[0]];
				var pj = positions[edge[1]];
				var dx = pi.getX() - pj.getX();
				var dy = pi.getY() - pj.getY();
				var distance = Math.sqrt(dx * dx + dy * dy);
				if (distance > 0) {
					var force = c * (distance - k);
					springForces[edge[0]][0] -= force * dx / distance;
					springForces[edge[0]][1] -= force * dy / distance;
					springForces[edge[1]][0] += force * dx / distance;
					springForces[edge[1]][1] += force * dy / distance;
				}
			}

			// Update positions
			for (var i = 0; i < nodes.length; i++) {
				var dx = t * (repulsiveForces[i][0] + springForces[i][0]);
				var dy = t * (repulsiveForces[i][1] + springForces[i][1]);
				positions[i] = positions[i].add(dx * dt, dy * dt);
			}

			// Reduce temperature
			t *= 1 - (double) iter / iterations;
		}

		NodeArray<Point2D> result = graph.newNodeArray();
		for (var i = 0; i < nodes.length; i++) {
			result.put(nodes[i], positions[i]);
		}
		return result;
	}

	public double getK() {
		return k;
	}

	public void setK(double k) {
		this.k = k;
	}

	public double getC() {
		return c;
	}

	public void setC(double c) {
		this.c = c;
	}

	public double getT() {
		return t;
	}

	public void setT(double t) {
		this.t = t;
	}

	public double getDt() {
		return dt;
	}

	public void setDt(double dt) {
		this.dt = dt;
	}

	public int getIterations() {
		return iterations;
	}

	public void setIterations(int iterations) {
		this.iterations = iterations;
	}

	public int getRandomSeed() {
		return randomSeed;
	}

	public void setRandomSeed(int randomSeed) {
		this.randomSeed = randomSeed;
	}

	public static void main(String[] args) {
		var graph = new PhyloGraph();        // Sample graph with nodes and edges
		var nodes = new ArrayList<Node>();
		nodes.add(graph.newNode("A"));
		nodes.add(graph.newNode("B"));
		nodes.add(graph.newNode("C"));
		nodes.add(graph.newNode("D"));

		graph.newEdge(nodes.get(0), nodes.get(1));
		graph.newEdge(nodes.get(0), nodes.get(2));
		graph.newEdge(nodes.get(0), nodes.get(3));

		// Perform graph layout
		var layouter = new DavidsonHarelLayout();
		Map<Node, Point2D> layout = layouter.performLayout(graph, null);

		// Print the final coordinates of nodes
		for (Node node : nodes) {
			var point = layout.get(node);
			System.out.println(node.getInfo() + ": (" + point.getX() + ", " + point.getY() + ")");
		}
	}
}
