package splitstree6.io.readers.characters;

import splitstree6.data.CharactersBlock;
import splitstree6.io.utils.DataReaderBase;

public abstract class CharactersReader extends DataReaderBase<CharactersBlock> {
	public CharactersReader() {
		super(CharactersBlock.class);
	}

	private char gap = '-';
	private char missing = 0; // is set when charactersType is set

	public char getGap() {
		return gap;
	}

	public void setGap(char gap) {
		this.gap = gap;
	}

	public char getMissing() {
		return missing;
	}

	public void setMissing(char missing) {
		this.missing = missing;
	}
}
