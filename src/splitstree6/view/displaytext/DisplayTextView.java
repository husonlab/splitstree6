/*
 * DisplayTextView.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.displaytext;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.Node;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

public class DisplayTextView implements IView {
	private final DisplayTextViewController controller;
	private final DisplayTextViewPresenter presenter;

	private final UndoManager undoManager = new UndoManager();
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final String name;

	private final BooleanProperty showLineNumbers = new SimpleBooleanProperty(this, "showLineNumbers");
	private final BooleanProperty wrapText = new SimpleBooleanProperty(this, "wrapText");

	private final DoubleProperty fontSize = new SimpleDoubleProperty(this, "fontSize");

	{
		ProgramProperties.track(showLineNumbers, false);
		ProgramProperties.track(wrapText, true);
		ProgramProperties.track(fontSize, 12.0);
	}

	/**
	 * constructor
	 */
	public DisplayTextView(MainWindow mainWindow, String name, boolean editable) {
		this.name = name;

		var loader = new ExtendedFXMLLoader<DisplayTextViewController>(DisplayTextViewController.class);
		controller = loader.getController();

		presenter = new DisplayTextViewPresenter(mainWindow, this, editable);

		controller.getCodeArea().textProperty().addListener((v, o, n) -> empty.set(n.length() == 0));

		controller.getCodeArea().setWrapText(true);

		showLineNumbers.addListener((v, o, n) -> controller.getCodeArea().setParagraphGraphicFactory(n ? LineNumberFactory.get(controller.getCodeArea()) : null));
		if (isShowLineNumbers())
			controller.getCodeArea().setParagraphGraphicFactory(LineNumberFactory.get(controller.getCodeArea()));

		controller.getCodeArea().setWrapText(isWrapText());
		wrapText.bindBidirectional(controller.getCodeArea().wrapTextProperty());
	}

	public void replaceText(String text) {
		controller.getCodeArea().clear();
		Platform.runLater(() -> controller.getCodeArea().replaceText(text));
	}

	@Override
	public void setViewTab(ViewTab viewTab) {
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
	public void clear() {
		controller.getCodeArea().replaceText("");
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return controller.getCodeArea();
	}

	@Override
	public DisplayTextViewPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Node getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();
	}

	@Override
	public int size() {
		return controller.getCodeArea().getLength();
	}

	public DisplayTextViewController getController() {
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

	public double getFontSize() {
		return fontSize.get();
	}

	public DoubleProperty fontSizeProperty() {
		return fontSize;
	}

	public void setFontSize(double fontSize) {
		this.fontSize.set(fontSize);
	}

	@Override
	public String getCitation() {
		return null;
	}
}

