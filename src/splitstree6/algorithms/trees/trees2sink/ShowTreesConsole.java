package splitstree6.algorithms.trees.trees2sink;

import jloda.util.ProgressListener;
import splitstree6.data.SinkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;

public class ShowTreesConsole extends Trees2Sink {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treeData, SinkBlock outputData) throws IOException {
		for (var tree : treeData.getTrees()) {
			System.out.println(tree.toBracketString());
		}
	}

	@Override
	public String getCitation() {
		return null;
	}
}
