/*
 *  CodeAreaSearcher.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.displaytext;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.fx.find.ITextSearcher;
import org.fxmisc.richtext.CodeArea;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * CodeArea searcher
 * Daniel Huson, Daria Evseeva 7.2018
 */
public class CodeAreaSearcher implements ITextSearcher {

	private final CodeArea codeArea;

	private final BooleanProperty globalFindable = new SimpleBooleanProperty(false);
	private final BooleanProperty selectionReplaceable = new SimpleBooleanProperty(false);

	private final String name;

	/**
	 * constructor
	 */
	public CodeAreaSearcher(String name, CodeArea codeArea) {
		this.name = name;
		this.codeArea = codeArea;
		BooleanBinding emptyProperty = new BooleanBinding() {
			{
				super.bind(codeArea.getContent().lengthProperty());
			}

			@Override
			protected boolean computeValue() {
				return codeArea.getContent().getLength() == 0;
			}
		};

		if (codeArea != null) {
			//globalFindable.bind(codeArea.textProperty().isNotEmpty());
			//selectionReplaceable.bind(codeArea.selectedTextProperty().isNotEmpty());
			globalFindable.bind(emptyProperty);
			//Val.map(codeArea.lengthProperty(), n -> n == 0));
			selectionReplaceable.bind(emptyProperty);
		}
	}

	/**
	 * get the name for this type of search
	 *
	 * @return name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Find first instance
	 */
	public boolean findFirst(String regEx) {
		//codeArea.positionCaret(0);
		codeArea.moveTo(0); //todo test
		return singleSearch(regEx, true);
	}

	/**
	 * Find next instance
	 */
	public boolean findNext(String regEx) {
		return singleSearch(regEx, true);
	}

	/**
	 * Find previous instance
	 */
	public boolean findPrevious(String regEx) {
		return codeArea != null && singleSearch(regEx, false);
	}

	/**
	 * Replace selection with current. Does nothing if selection invalid.
	 */
	public boolean replaceNext(String regEx, String replaceText) {
		if (codeArea == null) return false;
		if (findNext(regEx)) {
			var selection = codeArea.getSelection();
			var inputText = codeArea.getSelectedText();
			var newText = inputText.replaceAll(regEx, replaceText);
			codeArea.replaceSelection(newText);
			codeArea.selectRange(selection.getStart(), selection.getStart() + newText.length());
			return true;
		}
		return false;
	}

	/**
	 * Replace all occurrences of text in document, subject to options.
	 *
	 * @return number of instances replaced
	 */
	public int replaceAll(String regularExpression, String replaceText, boolean selectionOnly) {
		if (codeArea == null) return 0;

		var matcher = Pattern.compile(regularExpression).matcher(codeArea.getText());
		var count = (int) matcher.results().count();
		codeArea.replaceText(matcher.replaceAll(replaceText));
		return count;
	}

	/**
	 * is a global find possible?
	 *
	 * @return true, if there is at least one object
	 */
	public ReadOnlyBooleanProperty isGlobalFindable() {
		return globalFindable;
	}

	/**
	 * is a selection find possible
	 *
	 * @return true, if at least one object is selected
	 */
	public ReadOnlyBooleanProperty isSelectionFindable() {
		return selectionReplaceable;
	}

	/**
	 * Selects all occurrences of text in document, subject to options and constraints of document type
	 *
	 * @param pattern
	 */
	public int findAll(String pattern) {
		//Not implemented for text editors.... as we cannot select multiple chunks of text.
		return 0;
	}

	/**
	 * something has been changed or selected, update view
	 */
	public void updateView() {
	}

	/**
	 * does this searcher support find all?
	 *
	 * @return true, if find all supported
	 */
	public boolean canFindAll() {
		return false;
	}

	/**
	 * set select state of all objects
	 */
	public void selectAll(boolean select) {
		if (select) {
			codeArea.selectAll();
		} else {
			//codeArea.positionCaret(0); // todo test
			codeArea.moveTo(0);
		}
	}


	//We start the search at the end of the selection, which could be the dot or the mark.

	private int getSearchStart() {
		if (codeArea == null) return 0;
		return Math.max(codeArea.getAnchor(), codeArea.getCaretPosition());
	}

	private void selectMatched(Matcher matcher) {
		codeArea.selectRange(matcher.start(), matcher.end());
	}

	private boolean singleSearch(String regularExpression, boolean forward) throws PatternSyntaxException {
		if (codeArea == null) return false;

		//Do nothing if there is no text.
		if (regularExpression.length() == 0)
			return false;

		//Search begins at the end of the currently selected portion of text.
		int currentPoint = getSearchStart();

		boolean found = false;

		var pattern = Pattern.compile(regularExpression);

		var source = codeArea.getText();
		var matcher = pattern.matcher(source);

		if (forward)
			found = matcher.find(currentPoint);
		else {
			//This is an inefficient algorithm to handle reverse search. It is a temporary
			//stop gap until reverse searching is built into the API.
			//TODO: Check every once and a while to see when matcher.previous() is implemented in the API.
			//TODO: Consider use of GNU find/replace.
			//TODO: use regions to make searching more efficient when we know the length of the search string to match.
			int pos = 0;
			int searchFrom = 0;
			//System.err.println("Searching backwards before " + currentPoint);
			while (matcher.find(searchFrom) && matcher.end() < currentPoint) {
				pos = matcher.start();
				searchFrom = matcher.end();
				found = true;
				//System.err.println("\tfound at [" + pos + "," + matcher.end() + "]" + " but still looking");
			}
		}

		if (!found && currentPoint != 0) {
			matcher = pattern.matcher(source);
			found = matcher.find();
		}

		if (!found)
			return false;

		//System.err.println("Pattern found between positions " + matcher.start() + " and " + matcher.end());
		selectMatched(matcher);
		return true;
	}

	/**
	 * set scope global rather than selected
	 */
	public void setGlobalScope(boolean globalScope) {
	}

	/**
	 * get scope global rather than selected
	 *
	 * @return true, if search scope is global
	 */
	public boolean isGlobalScope() {
		return codeArea != null;
	}
}
