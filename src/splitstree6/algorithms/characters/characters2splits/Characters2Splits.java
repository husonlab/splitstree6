package splitstree6.algorithms.characters.characters2splits;

import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.sflow.Algorithm;

public abstract class Characters2Splits extends Algorithm<CharactersBlock, SplitsBlock> {
	public Characters2Splits() {
		super(CharactersBlock.class, SplitsBlock.class);
	}
}
