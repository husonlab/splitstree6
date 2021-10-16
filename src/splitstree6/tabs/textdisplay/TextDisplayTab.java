/*
 *  TextDisplayTab.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.textdisplay;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

public class TextDisplayTab extends Tab implements IDisplayTab {
	private final TextDisplayTabController controller;
	private final TextDisplayTabPresenter presenter;

	private final UndoManager undoManager = new UndoManager();
	private final MainWindow mainWindow;
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final BooleanProperty showLineNumbers = new SimpleBooleanProperty(false);
	private final BooleanProperty wrapText = new SimpleBooleanProperty(true);

	/**
	 * constructor
	 */
	public TextDisplayTab(MainWindow mainWindow, String name, boolean closable, boolean editable) {
		this.mainWindow = mainWindow;

		var loader = new ExtendedFXMLLoader<TextDisplayTabController>(TextDisplayTab.class);
		controller = loader.getController();

		presenter = new TextDisplayTabPresenter(mainWindow, this, editable);

		setContent(loader.getRoot());

		controller.getCodeArea().lengthProperty().addListener((v, o, n) -> empty.set(n == 0));
		controller.getCodeArea().setWrapText(true);

		controller.getCodeArea().getStyleClass().add("background");

		// todo: use darker line numbers in dark theme
		showLineNumbers.addListener((v, o, n) -> controller.getCodeArea().setParagraphGraphicFactory(n ? LineNumberFactory.get(controller.getCodeArea()) : null));
		wrapText.bindBidirectional(controller.getCodeArea().wrapTextProperty());

		setText(name);
		setClosable(closable);
	}

	public void replaceText(String text) {
		controller.getCodeArea().clear();
		controller.getCodeArea().replaceText(text);
	}

	/**
	 * go to given line and given col
	 *
	 * @param col if col<=1 or col>line length, will select the whole line, else selects line starting at given col
	 */
	public void gotoLine(long lineNumber, int col) {
		if (col < 0)
			col = 0;
		else if (col > 0)
			col--; // because col is 1-based

		lineNumber = Math.max(1, lineNumber);
		final String text = controller.getCodeArea().getText();
		int start = 0;
		for (int i = 1; i < lineNumber; i++) {
			start = text.indexOf('\n', start + 1);
			if (start == -1) {
				System.err.println("No such line number: " + lineNumber);
				return;
			}
		}
		start++;
		if (start < text.length()) {
			int end = text.indexOf('\n', start);
			if (end == -1)
				end = text.length();
			if (start + col < end)
				start = start + col;
			controller.getScrollPane().requestFocus();
			controller.getCodeArea().selectRange(start, end);
		}
	}


	/**
	 * select matching brackets
	 */
	public void selectBrackets(CodeArea codeArea) {
		int pos = codeArea.getCaretPosition() - 1;
		while (pos > 0 && pos < codeArea.getText().length()) {
			final char close = codeArea.getText().charAt(pos);
			if (close == ')' || close == ']' || close == '}') {
				final int closePos = pos;
				final int open = (close == ')' ? '(' : (close == ']' ? '[' : '}'));

				int balance = 0;
				for (; pos >= 0; pos--) {
					char ch = codeArea.getText().charAt(pos);
					if (ch == open)
						balance--;
					else if (ch == close)
						balance++;
					if (balance == 0) {
						final int fpos = pos;
						Platform.runLater(() -> codeArea.selectRange(fpos, closePos + 1));
						return;
					}
				}
			}
			pos++;
		}
	}


	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty isEmptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return null;
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	public TextDisplayTabController getController() {
		return controller;
	}

	public boolean isShowLineNumbers() {
		return showLineNumbers.get();
	}

	public BooleanProperty showLineNumbersProperty() {
		return showLineNumbers;
	}

	public void setShowLineNumbers(boolean showLineNumbers) {
		this.showLineNumbers.set(showLineNumbers);
	}

	public boolean isWrapText() {
		return wrapText.get();
	}

	public BooleanProperty wrapTextProperty() {
		return wrapText;
	}

	public void setWrapText(boolean wrapText) {
		this.wrapText.set(wrapText);
	}
}

