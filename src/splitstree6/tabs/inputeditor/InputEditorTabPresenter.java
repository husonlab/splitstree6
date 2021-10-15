/*
 *  InputEditorTabPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.inputeditor;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.StageStyle;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.NotificationManager;
import jloda.util.Basic;
import jloda.util.FileLineIterator;
import jloda.util.ProgramProperties;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

import java.io.IOException;
import java.util.Optional;

/**
 * input editor tab presenter
 * Daniel Huson, 10.2021
 */
public class InputEditorTabPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final InputEditorTab tab;
	private final BooleanBinding textEmpty;
	private final BooleanBinding selectionEmpty;
	private final FindToolBar findToolBar;

	public InputEditorTabPresenter(MainWindow mainWindow, InputEditorTab tab) {
		this.mainWindow = mainWindow;
		this.tab = tab;

		tab.setGraphic(new HBox(new ImageView(ResourceManagerFX.getIcon("sun/Import16.gif")), new Label(tab.getText())));

		var tabController = tab.getController();

		var codeArea = tabController.getCodeArea();

		codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

		var css = ResourceManagerFX.getCssURL("styles.css");
		if (css != null)
			tabController.getScrollPane().getStylesheets().add(css.toString());

		codeArea.setEditable(true);

		findToolBar = new FindToolBar(null, new CodeAreaSearcher("Text", codeArea));
		tabController.getTopVBox().getChildren().add(findToolBar);

		textEmpty = new BooleanBinding() {
			{
				super.bind(codeArea.lengthProperty());
			}

			@Override
			protected boolean computeValue() {
				return codeArea.getLength() == 0;
			}
		};

		selectionEmpty = new BooleanBinding() {
			{
				super.bind(codeArea.selectionProperty());
			}

			@Override
			protected boolean computeValue() {
				return codeArea.getSelection().getLength() == 0;
			}
		};


		// prevent double paste:
		{
			codeArea.addEventFilter(KeyEvent.ANY, e -> {
				if (e.getCode() == KeyCode.V && e.isShortcutDown()) {
					e.consume();
				}
			});
		}

		codeArea.focusedProperty().addListener((c, o, n) -> {
			if (n)
				mainWindow.getController().getPasteMenuItem().disableProperty().set(!Clipboard.getSystemClipboard().hasString());
		});
	}

	public void setup() {
		var controller = mainWindow.getController();
		var tabController = tab.getController();

		tabController.getParseAndLoadButton().setOnAction(null);
		tabController.getParseAndLoadButton().disableProperty().bind(textEmpty);

		var codeArea = tabController.getCodeArea();

		controller.getCutMenuItem().setOnAction(e -> codeArea.cut());
		controller.getCutMenuItem().disableProperty().bind(selectionEmpty);

		controller.getCopyMenuItem().setOnAction(e -> codeArea.copy());
		controller.getCopyMenuItem().disableProperty().bind(selectionEmpty);

		controller.getPasteMenuItem().setOnAction(e -> codeArea.paste());

		controller.getUndoMenuItem().setOnAction(e -> codeArea.undo());
		{
			var undoAvailable = new SimpleBooleanProperty();
			undoAvailable.bind(codeArea.undoAvailableProperty());
			controller.getUndoMenuItem().disableProperty().bind(undoAvailable.not());
		}

		controller.getRedoMenuItem().setOnAction(e -> codeArea.redo());
		{
			var redoAvailable = new SimpleBooleanProperty();
			redoAvailable.bind(codeArea.redoAvailableProperty());
			controller.getRedoMenuItem().disableProperty().bind(redoAvailable.not());
		}

		controller.getFindMenuItem().setOnAction((e) -> findToolBar.setShowFindToolBar(true));
		controller.getFindAgainMenuItem().setOnAction((e) -> findToolBar.findAgain());
		controller.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
		controller.getReplaceMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(true));

		controller.getGotoLineMenuItem().setOnAction((e) -> {
			final TextInputDialog dialog = new TextInputDialog("");
			dialog.setTitle("Go to Line - " + ProgramProperties.getProgramName());
			dialog.initStyle(StageStyle.UTILITY);
			dialog.setX(mainWindow.getStage().getX() + 0.5 * mainWindow.getStage().getWidth());
			dialog.setY(mainWindow.getStage().getY() + 0.5 * mainWindow.getStage().getHeight());
			dialog.setHeaderText("Select line by number");
			dialog.setContentText("[line] [:column]:");
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent()) {
				final String[] tokens = result.get().split(":");
				if (tokens.length > 0 && Basic.isInteger(tokens[0]))
					tab.gotoLine(Basic.parseInt(tokens[0]), tokens.length == 2 && Basic.isInteger(tokens[1]) ? Basic.parseInt(tokens[1]) : 0);
			}
		});

		controller.getSelectAllMenuItem().setOnAction(e -> codeArea.selectAll());
		controller.getSelectAllMenuItem().disableProperty().bind(textEmpty);

		controller.getSelectNoneMenuItem().setOnAction(e -> codeArea.selectRange(0, 0));
		controller.getSelectNoneMenuItem().disableProperty().bind(textEmpty);

		controller.getSelectBracketsMenuItem().setOnAction(e -> selectBrackets(codeArea));
		controller.getSelectBracketsMenuItem().disableProperty().bind(textEmpty);

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		controller.getZoomInMenuItem().setOnAction(null);
		controller.getZoomOutMenuItem().setOnAction(null);

		controller.getWrapTextMenuItem().setOnAction(null);
	}

	public void loadFile(String fileName) {
		try (FileLineIterator it = new FileLineIterator(fileName)) {
			tab.getController().getCodeArea().replaceText(Basic.toString(it.lines(), "\n"));
		} catch (IOException ex) {
			NotificationManager.showError("Input file failed: " + ex.getMessage());
		}
	}

	/**
	 * select matching brackets
	 */
	protected void selectBrackets(CodeArea codeArea) {
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
}
