/*
 * NexusIOBase.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.io.nexus;

import jloda.util.IOExceptionWithLineNumber;
import jloda.util.Pair;
import jloda.util.parse.NexusStreamParser;

import java.io.IOException;
import java.io.Writer;

/**
 * base class for nexus input and output
 * Daniel Huson, 3.2018
 */
public class NexusIOBase {
	private String title;
	private Pair<String, String> link;

	/**
	 * write the block title and link, if set
	 */
	public void writeTitleAndLink(Writer w) throws IOException {
		if (getTitle() != null && getTitle().length() > 0) {
			w.write("TITLE '" + getTitle() + "';\n");
			if (getLink() != null)
				w.write("LINK " + getLink().getFirst() + " = '" + getLink().getSecond() + "';\n");
		}
	}

	/**
	 * parse the title and link, if present
	 */
	public void parseTitleAndLink(NexusStreamParser np) throws IOExceptionWithLineNumber {
		setTitle(null);
		setLink(null);

		if (np.peekMatchIgnoreCase("TITLE")) {
			np.matchIgnoreCase("TITLE");
			setTitle(np.getWordRespectCase());
			np.matchIgnoreCase(";");
			if (np.peekMatchIgnoreCase("LINK")) {
				np.matchIgnoreCase("LINK");
				final String parentType = np.getWordRespectCase();
				np.matchIgnoreCase("=");
				final String parentTitle = np.getWordRespectCase();
				setLink(new Pair<>(parentType, parentTitle));
				np.matchIgnoreCase(";");
			}
		}
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Pair<String, String> getLink() {
		return link;
	}

	public void setLink(Pair<String, String> link) {
		this.link = link;
	}

	public void setTitleAndLink(String title, Pair<String, String> link) {
		this.title = title;
		this.link = link;
	}
}
