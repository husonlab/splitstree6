package splitstree6.io.writers.trees;

import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.io.Writer;

public class NewickWriter extends TreesWriter {
	public NewickWriter() {
		setFileExtensions("tree", "tre", "new", "nwk", "treefile");
	}

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, TreesBlock trees) throws IOException {
		if (trees != null) {
			for (int i = 0; i < trees.getNTrees(); i++) {
				w.write(trees.getTrees().get(i).toBracketString() + ";\n");
			}
		}
	}
}
