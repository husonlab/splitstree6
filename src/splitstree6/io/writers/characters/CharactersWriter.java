package splitstree6.io.writers.characters;

import splitstree6.data.CharactersBlock;
import splitstree6.io.utils.DataWriterBase;

public abstract class CharactersWriter extends DataWriterBase<CharactersBlock> {
	public CharactersWriter() {
		super(CharactersBlock.class);
	}
}
