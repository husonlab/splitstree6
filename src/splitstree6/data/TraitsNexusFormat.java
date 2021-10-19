/*
 * TraitsNexusFormat.java Copyright (C) 2021. Daniel H. Huson
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

import jloda.util.StringUtils;
import splitstree6.io.nexus.INexusFormat;

/**
 * traits format
 * Daniel Huson, 2/2018
 */
public class TraitsNexusFormat implements INexusFormat {
	public enum Separator {Comma, SemiColon, WhiteSpace}

	private boolean optionLabel = true;
	private Separator optionSeparator = Separator.Comma;
	private char optionMissingCharacter = '?';

	/**
	 * Constructor
	 */
	public TraitsNexusFormat() {
	}

	/**
	 * Show labels?
	 *
	 * @return true, if labels are to be printed
	 */
	public boolean isOptionLabel() {
		return optionLabel;
	}

	/**
	 * Show labels
	 *
	 * @param flag whether labels should be printed
	 */
	public void setOptionLabel(boolean flag) {
		optionLabel = flag;
	}

	public Separator getOptionSeparator() {
		return optionSeparator;
	}

	public void setOptionSeparator(String optionSeparator) {
		this.optionSeparator = StringUtils.valueOfIgnoreCase(Separator.class, optionSeparator);
	}

	public void setSeparator(Separator separator) {
		this.optionSeparator = separator;
	}

	public String getSeparatorString() {
		switch (optionSeparator) {
			case Comma:
				return ",";
			case SemiColon:
				return ";";
			default:
				return " ";
		}
	}

	public char getOptionMissingCharacter() {
		return optionMissingCharacter;
	}

	public void setOptionMissingCharacter(char optionMissingCharacter) {
		this.optionMissingCharacter = optionMissingCharacter;
	}
}
