/*
 *  CharactersReader.java Copyright (C) 2021 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
