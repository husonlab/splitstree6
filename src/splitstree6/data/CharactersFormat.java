/*
 * CharactersFormat.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.data;

/**
 * Characters nexus format
 * Daniel Huson, 12/22/16.
 */
public class CharactersFormat {
	private boolean optionTranspose;
	private boolean optionInterleave = true;
	private boolean optionLabels = true;
	private boolean optionTokens;
	private char optionMatchCharacter = 0;
	private int optionColumnsPerBlock = 80;

	/**
	 * the Constructor
	 */
	public CharactersFormat() {
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
