/*
 *  NetworkViewPipeline.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.workflow;

import jloda.fx.workflow.WorkflowNode;
import splitstree6.algorithms.network.network2network.ShaveFilter;
import splitstree6.algorithms.network.network2view.ShowNetwork;
import splitstree6.data.NetworkBlock;
import splitstree6.data.ViewBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * attaches a view to the network that an algorithm produces.
 * <p>
 * The network of Razor Net and of Razor Haplo Net is first passed through the Shave filter, with its default target
 * Balanced, and the result of that is shown: the network as computed is usually not what is wanted. The
 * algorithms come from a plugin, so they are recognized by their names: all whose class name begins with RazorNet
 * or RazorHaplo, which covers RazorNet, RazorHaploNet, RazorHaplotypeNetwork and their numbered variants.
 * Daniel Huson, 11.2026
 */
public class NetworkViewPipeline {

	/**
	 * is the network of this algorithm passed through the Shave filter before it is shown?
	 */
	public static boolean isShavedFirst(Algorithm<?, ?> algorithm) {
		if (algorithm == null)
			return false;
		var name = algorithm.getClass().getSimpleName();
		return name.startsWith("RazorNet") || name.startsWith("RazorHaplo");
	}

	/**
	 * attach a network view to a network node, after a Shave filter if the algorithm that computes the network asks
	 * for one
	 *
	 * @param algorithm   the algorithm that computes the network
	 * @param networkNode the node that holds the network
	 * @return the new nodes, in the order in which they were created; the last algorithm node is the Show Network one
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static List<WorkflowNode> attachView(Workflow workflow, Algorithm<?, ?> algorithm, DataNode networkNode) {
		var result = new ArrayList<WorkflowNode>();
		var toShow = networkNode;
		if (isShavedFirst(algorithm)) {
			DataNode shavedNode = workflow.newDataNode(new NetworkBlock());
			var shaveNode = workflow.newAlgorithmNode(new ShaveFilter(), workflow.getWorkingTaxaNode(), networkNode, shavedNode);
			result.add(shavedNode);
			result.add(shaveNode);
			toShow = shavedNode;
		}
		DataNode viewNode = workflow.newDataNode(new ViewBlock());
		var showNode = workflow.newAlgorithmNode(new ShowNetwork(), workflow.getWorkingTaxaNode(), toShow, viewNode);
		result.add(viewNode);
		result.add(showNode);
		return result;
	}
}
