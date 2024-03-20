/*
 *  CharactersFormat.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.data;

/**
 * Characters nexus format
 * Daniel Huson, 12/22/16.
 */
public class CharactersFormat {
	private boolean optionTranspose = false;
	private boolean optionInterleave = true;
	private boolean optionLabels = true;
	private boolean optionTokens = false;
	private char optionMatchCharacter = 0;
	private int optionColumnsPerBlock = 80;

	/**
	 * constructor
	 */
	public CharactersFormat() {
	}

	/**
	 * copy constructor
	 *
	 * @param that to be copied
	 */
	public CharactersFormat(CharactersFormat that) {
		this.optionTranspose = that.optionTranspose;
		this.optionInterleave = that.optionInterleave;
		this.optionLabels = that.optionLabels;
		this.optionTokens = that.optionTokens;
		this.optionMatchCharacter = that.optionMatchCharacter;
		this.optionColumnsPerBlock = that.optionColumnsPerBlock;
	}


	public boolean isOptionTranspose() {
		return optionTranspose;
	}

	public void setOptionTranspose(boolean optionTranspose) {
		this.optionTranspose = optionTranspose;
	}

	public boolean isOptionInterleave() {
		return optionInterleave;
	}

	public void setOptionInterleave(boolean optionInterleave) {
		this.optionInterleave = optionInterleave;
	}

	public boolean isOptionLabels() {
		return optionLabels;
	}

	public void setOptionLabels(boolean optionLabels) {
		this.optionLabels = optionLabels;
	}


	public boolean isOptionTokens() {
		return optionTokens;
	}

	public void setOptionTokens(boolean optionTokens) {
		this.optionTokens = optionTokens;
	}

	public char getOptionMatchCharacter() {
		return optionMatchCharacter;
	}

	public void setOptionMatchCharacter(char optionMatchCharacter) {
		this.optionMatchCharacter = optionMatchCharacter;
	}

	public int getOptionColumnsPerBlock() {
		return this.optionColumnsPerBlock;
	}

	public void setOptionColumnsPerBlock(int optionColumnsPerBlock) {
		this.optionColumnsPerBlock = optionColumnsPerBlock;
	}
}
