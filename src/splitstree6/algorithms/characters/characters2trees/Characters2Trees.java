package splitstree6.algorithms.characters.characters2trees;

import splitstree6.data.CharactersBlock;
import splitstree6.data.TreesBlock;
import splitstree6.sflow.Algorithm;

public abstract class Characters2Trees extends Algorithm<CharactersBlock, TreesBlock> {
	public Characters2Trees() {
		super(CharactersBlock.class, TreesBlock.class);
	}
}
