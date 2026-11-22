/*
 *  NetworkViewPipelineTest.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.util.progress.ProgressListener;
import org.junit.jupiter.api.Test;
import splitstree6.algorithms.characters.characters2network.Characters2Network;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.distances.distances2network.Distances2Network;
import splitstree6.algorithms.distances.distances2network.MinSpanningNetwork;
import splitstree6.algorithms.network.network2network.ShaveFilter;
import splitstree6.algorithms.network.network2view.ShowNetwork;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * the view of a network: for Razor Net and Razor Haplo Net the Shave filter comes first
 */
public class NetworkViewPipelineTest {
	/**
	 * stands for the algorithm of that name, which comes from a plugin
	 */
	public static class RazorNet extends Distances2Network {
		@Override
		public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distances, NetworkBlock network) {
		}
	}

	public static class RazorHaploNet extends Characters2Network {
		@Override
		public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock characters, NetworkBlock network) {
		}
	}

	public static class RazorHaplotypeNetwork2 extends RazorHaploNet {
	}

	@Test
	void razorNetworksAreShavedBeforeTheyAreShown() {
		for (Algorithm<?, ?> algorithm : new Algorithm<?, ?>[]{new RazorNet(), new RazorHaploNet(), new RazorHaplotypeNetwork2()}) {
			assertTrue(NetworkViewPipeline.isShavedFirst(algorithm));
			var workflow = new Workflow(null);
			var networkNode = workflow.newDataNode(new NetworkBlock());
			var nodes = NetworkViewPipeline.attachView(workflow, algorithm, networkNode);
			assertEquals(4, nodes.size());
			// network -> Shave filter -> network -> Show Network -> view
			var shave = (AlgorithmNode<?, ?>) networkNode.getPreferredChild();
			assertInstanceOf(ShaveFilter.class, shave.getAlgorithm());
			assertEquals(ShaveFilter.Target.Balanced, ((ShaveFilter) shave.getAlgorithm()).optionTargetProperty().get());
			var shaved = (DataNode<?>) shave.getPreferredChild();
			assertInstanceOf(NetworkBlock.class, shaved.getDataBlock());
			assertNotSame(networkNode, shaved);
			var show = (AlgorithmNode<?, ?>) shaved.getPreferredChild();
			assertInstanceOf(ShowNetwork.class, show.getAlgorithm());
			assertSame(show, nodes.get(nodes.size() - 1));
			assertInstanceOf(ViewBlock.class, ((DataNode<?>) show.getPreferredChild()).getDataBlock());
		}
	}

	@Test
	void otherNetworksAreShownAsTheyAre() {
		for (Algorithm<?, ?> algorithm : new Algorithm<?, ?>[]{new MinSpanningNetwork(), new MedianJoining(), null}) {
			assertFalse(NetworkViewPipeline.isShavedFirst(algorithm));
			var workflow = new Workflow(null);
			var networkNode = workflow.newDataNode(new NetworkBlock());
			var nodes = NetworkViewPipeline.attachView(workflow, algorithm, networkNode);
			assertEquals(2, nodes.size());
			var show = (AlgorithmNode<?, ?>) networkNode.getPreferredChild();
			assertInstanceOf(ShowNetwork.class, show.getAlgorithm());
			assertInstanceOf(ViewBlock.class, ((DataNode<?>) show.getPreferredChild()).getDataBlock());
		}
	}
}
