package splitstree6.io.readers.characters;

import splitstree6.data.CharactersBlock;
import splitstree6.io.utils.DataReaderBase;

public abstract class CharactersReader extends DataReaderBase<CharactersBlock> {
	public CharactersReader() {
		super(CharactersBlock.class);
	}
}
