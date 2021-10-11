package splitstree6.algorithms.characters.characters2network;

import splitstree6.data.CharactersBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.sflow.Algorithm;

public abstract class Characters2Network extends Algorithm<CharactersBlock, NetworkBlock> {
	public Characters2Network() {
		super(CharactersBlock.class, NetworkBlock.class);
	}
}
