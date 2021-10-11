package splitstree6.algorithms.source.source2characters;

import splitstree6.data.CharactersBlock;
import splitstree6.data.SourceBlock;
import splitstree6.sflow.Algorithm;

public abstract class Source2Characters extends Algorithm<SourceBlock, CharactersBlock> {
	public Source2Characters() {
		super(SourceBlock.class, CharactersBlock.class);
	}
}
