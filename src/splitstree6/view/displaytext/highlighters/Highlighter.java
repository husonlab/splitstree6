/*
 *  Highlighter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.displaytext.highlighters;

import jloda.fx.util.RunAfterAWhile;
import jloda.util.StringUtils;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

/**
 * code area highlighter
 * Daniel Huson, 7.2022
 */
public class Highlighter {
	public enum Type {
		Universal, Nexus, XML
	}

	private Type type;
	private IHighlighter highlighter;

	public Highlighter(CodeArea codeArea) {
		highlighter = new UniversalHighlighter();
		type = Type.Universal;

		codeArea.textProperty().addListener((v, p, n) -> RunAfterAWhile.applyInFXThread(this, () -> {
			var line = StringUtils.getFirstLine(codeArea.getText()).toLowerCase();
			if (line.startsWith("#nexus"))
				setType(Type.Nexus);
			else if (line.startsWith("<nex:nexml") || line.startsWith("<?xml version="))
				setType(Type.XML);
			else
				setType(Type.Universal);
			// style everything once changes to text have stopped
			if (codeArea.getLength() < 10000000)
				codeArea.setStyleSpans(0, getHighlighter().computeHighlighting(codeArea.getText()));
		}));
	}

	public void setType(Type type) {
		if (type != this.type) {
			this.type = type;
			highlighter = switch (type) {
				case Nexus -> new NexusHighlighter();
				case XML -> new XMLHighlighter();
				case Universal -> new UniversalHighlighter();
			};
		}
	}

	public Type getType() {
		return type;
	}

	public IHighlighter getHighlighter() {
		return highlighter;
	}

	public interface IHighlighter {
		StyleSpans<Collection<String>> computeHighlighting(String text);
	}
}
