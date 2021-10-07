package splitstree6.algorithms.characters.characters2sink;

import splitstree6.data.CharactersBlock;
import splitstree6.data.SinkBlock;
import splitstree6.workflow.Algorithm;

public abstract class Characters2Sink extends Algorithm<CharactersBlock, SinkBlock> {
	public Characters2Sink() {
		super(CharactersBlock.class, SinkBlock.class);
	}
}
