/*
 *  MyTextArea.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import jloda.fx.find.ITextSearcher;
import jloda.fx.find.TextAreaSearcher;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import splitstree6.utils.Platform;
import splitstree6.view.displaytext.highlighters.Highlighter;

public class MyTextArea {
	private final CodeArea codeArea;

	private final TextArea textArea;

	private final Node node;

	private final Node enclosingNode;


	public MyTextArea() {
		if (Platform.isDesktop()) {
			codeArea = new CodeArea();
			codeArea.requestFollowCaret();
			node = codeArea;
			enclosingNode = new VirtualizedScrollPane<>(codeArea);

			textArea = null;
		} else {
			codeArea = null;
			textArea = new TextArea();
			node = textArea;
			textArea.setPadding(new Insets(5, 2, 5, 2));
			enclosingNode = textArea;
		}
	}

	public Node getEnclosingNode() {
		return enclosingNode;
	}

	public Node getNode() {
		return node;
	}

	public final ObservableList<String> getStyleClass() {
		if (codeArea != null) return codeArea.getStyleClass();
		else return textArea.getStyleClass();
	}

	public void setStyle(String style) {
		if (codeArea != null)
			codeArea.setStyle(style);
		else
			textArea.setStyle(style);

	}

	public void setPadding(Insets insets) {
		if (codeArea != null) codeArea.setPadding(insets);
		else textArea.setPadding(insets);
	}

	public ObservableList<String> getStylesheets() {
		if (codeArea != null) return codeArea.getStylesheets();
		else return textArea.getStylesheets();
	}

	public DoubleProperty prefWidthProperty() {
		if (codeArea != null)
			return codeArea.prefWidthProperty();
		else return textArea.prefWidthProperty();
	}

	public void requestFollowCaret() {
		if (codeArea != null)
			codeArea.requestFollowCaret();
	}

	public Highlighter getHighlighter() {
		if (codeArea != null)
			return new Highlighter(codeArea);
		else
			return null;
	}

	public void selectRange(int start, Integer end) {
		if (codeArea != null)
			codeArea.selectRange(start, end);
		else {
			textArea.selectRange(start, end == null ? textArea.getCaretPosition() : end);
		}
	}

	public ObservableValue<String> textProperty() {
		if (codeArea != null)
			return codeArea.textProperty();
		else
			return textArea.textProperty();

	}

	public int getLength() {
		if (codeArea != null)
			return codeArea.getLength();
		else
			return textArea.getLength();
	}

	public String getText() {
		if (codeArea != null)
			return codeArea.getText();
		else
			return textArea.getText();
	}

	public String getText(int start, int end) {
		if (codeArea != null)
			return codeArea.getText(start, end);
		else
			return textArea.getText(start, end);
	}


	public void replaceText(String replacement) {
		if (codeArea != null)
			codeArea.replaceText(replacement);
		else
			textArea.setText(replacement);
	}

	public void setWrapText(boolean wrap) {
		if (codeArea != null)
			codeArea.setWrapText(wrap);
		else
			textArea.setWrapText(wrap);
	}

	public Property<Boolean> wrapTextProperty() {
		if (codeArea != null)
			return codeArea.wrapTextProperty();
		else
			return textArea.wrapTextProperty();
	}

	public void showLineNumbers(boolean show) {
		if (codeArea != null) {
			codeArea.setParagraphGraphicFactory(show ? LineNumberFactory.get(codeArea) : null);
		}
	}

	public boolean canShowLineNumbers() {
		return codeArea != null;
	}


	public void requestFocus() {
		if (codeArea != null)
			codeArea.requestFocus();
		else
			textArea.requestFocus();
	}

	public ReadOnlyBooleanProperty focusedProperty() {
		if (codeArea != null)
			return codeArea.focusedProperty();
		else
			return textArea.focusedProperty();
	}

	public void clear() {
		if (codeArea != null)
			codeArea.clear();
		else
			textArea.clear();

	}

	public void setEditable(boolean editable) {
		if (codeArea != null)
			codeArea.setEditable(editable);
		else
			textArea.setEditable(editable);
	}

	public ITextSearcher createSearcher() {
		if (codeArea != null)
			return new CodeAreaSearcher("Text", codeArea);
		else
			return new TextAreaSearcher("Text", textArea);

	}

	public IndexRange getSelection() {
		if (codeArea != null)
			return codeArea.getSelection();
		else
			return textArea.getSelection();
	}

	public ObservableValue<IndexRange> selectionProperty() {
		if (codeArea != null)
			return codeArea.selectionProperty();
		else
			return textArea.selectionProperty();
	}

	public final <T extends Event> void addEventFilter(EventType<T> type, EventHandler<? super T> handler) {
		if (codeArea != null)
			codeArea.addEventFilter(type, handler);
		else
			textArea.addEventFilter(type, handler);
	}

	public final <T extends Event> void addEventHandler(EventType<T> type, EventHandler<? super T> handler) {
		if (codeArea != null)
			codeArea.addEventHandler(type, handler);
		else
			textArea.addEventHandler(type, handler);
	}

	public void copy() {
		if (codeArea != null)
			codeArea.copy();
		else
			textArea.copy();

	}

	public void cut() {
		if (codeArea != null)
			codeArea.cut();
		else
			textArea.cut();
	}

	public void paste() {
		if (codeArea != null)
			codeArea.paste();
		else
			textArea.paste();
	}

	public void undo() {
		if (codeArea != null)
			codeArea.undo();
		else
			textArea.undo();
	}

	public void redo() {
		if (codeArea != null)
			codeArea.redo();
		else
			textArea.redo();
	}

	public ObservableValue<? extends Boolean> undoAvailableProperty() {
		if (codeArea != null)
			return codeArea.undoAvailableProperty();
		else
			return textArea.undoableProperty();
	}

	public ObservableValue<? extends Boolean> redoAvailableProperty() {
		if (codeArea != null)
			return codeArea.redoAvailableProperty();
		else
			return textArea.redoableProperty();
	}

	public void setContextMenu(ContextMenu contextMenu) {
		if (codeArea != null)
			codeArea.setContextMenu(contextMenu);
		else
			textArea.setContextMenu(contextMenu);

	}

	public void selectAll() {
		if (codeArea != null)
			codeArea.selectAll();
		else
			textArea.selectAll();

	}

	public int getCaretPosition() {
		if (codeArea != null)
			return codeArea.getCaretPosition();
		else
			return textArea.getCaretPosition();
	}

	public void clearHistory() {
		if (codeArea != null)
			codeArea.getUndoManager().forgetHistory();
		// todo: how to forget history for text area?
	}
}
