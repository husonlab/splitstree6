package splitstree6.algorithms.trees.trees2sink;

import jloda.util.ProgressListener;
import splitstree6.data.SinkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.writers.trees.NewickWriter;

import java.io.IOException;
import java.io.StringWriter;

public class ShowTreesConsole extends Trees2Sink {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treeData, SinkBlock outputData) throws IOException {
		try (var w = new StringWriter()) {
			w.write(treeData.getName() + ":\n");
			var writer = new NewickWriter();
			writer.write(w, taxaBlock, treeData);
			System.out.println(w);
		}
	}

	@Override
	public String getCitation() {
		return null;
	}
}
