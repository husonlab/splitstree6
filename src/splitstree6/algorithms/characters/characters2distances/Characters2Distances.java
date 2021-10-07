package splitstree6.algorithms.characters.characters2distances;

import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.workflow.Algorithm;

public abstract class Characters2Distances extends Algorithm<CharactersBlock, DistancesBlock> {
	public Characters2Distances() {
		super(CharactersBlock.class, DistancesBlock.class);
	}
}
