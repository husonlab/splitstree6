/*
 * TextDisplayTabPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.textdisplay;


import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.StageStyle;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.Print;
import jloda.fx.window.MainWindowManager;
import jloda.util.NumberUtils;
import jloda.util.ProgramProperties;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextDisplayTabPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TextDisplayTab tab;
	private final TextDisplayController controller;

	private final FindToolBar findToolBar;

	private final boolean editable;

	private final BooleanBinding selectionEmpty;

	public TextDisplayTabPresenter(MainWindow mainWindow, TextDisplayTab tab, boolean editable) {
		this.mainWindow = mainWindow;
		this.tab = tab;
		this.editable = editable;

		controller = tab.getController();

		var codeArea = controller.getCodeArea();

		codeArea.setEditable(editable);

		controller.getWrapTextToggle().selectedProperty().bindBidirectional(codeArea.wrapTextProperty());
		codeArea.setWrapText(true);

		findToolBar = new FindToolBar(null, new CodeAreaSearcher("Text", codeArea));
		controller.getTopVBox().getChildren().add(findToolBar);

		selectionEmpty = new BooleanBinding() {
			{
				super.bind(codeArea.selectionProperty());
			}

			@Override
			protected boolean computeValue() {
				return codeArea.getSelection().getLength() == 0;
			}
		};

		codeArea.selectionProperty().addListener((c, o, n) -> {
			MainWindowManager.getPreviousSelection().clear();
			MainWindowManager.getPreviousSelection().add(codeArea.getText(n.getStart(), n.getEnd()));
		});

		controller.getFindButton().setOnAction((e) -> findToolBar.setShowFindToolBar(true));

		if (editable) {
			controller.getFindAndReplaceButton().setOnAction(e -> findToolBar.setShowReplaceToolBar(true));
		}
		if (!editable) {
			var items = controller.getToolBar().getItems();
			items.remove(controller.getFindAndReplaceButton());
		}

		controller.getWrapTextToggle().selectedProperty().bindBidirectional(tab.wrapTextProperty());
		controller.getLineNumbersToggle().selectedProperty().bindBidirectional(tab.showLineNumbersProperty());


		// prevent double paste:
		{
			codeArea.addEventFilter(KeyEvent.ANY, e -> {
				if (e.getCode() == KeyCode.V && e.isShortcutDown()) {
					e.consume();
				}
			});
		}

		codeArea.focusedProperty().addListener((c, o, n) -> {
			if (n) {
				mainWindow.getController().getPasteMenuItem().disableProperty().unbind();
				mainWindow.getController().getPasteMenuItem().disableProperty().set(!Clipboard.getSystemClipboard().hasString());
			}
		});
	}

	public void setupMenuItems() {
		var windowController = mainWindow.getController();

		var codeArea = controller.getCodeArea();

		windowController.getPrintMenuItem().setOnAction(e -> Print.printText(mainWindow.getStage(), codeArea.getText()));
		windowController.getPrintMenuItem().disableProperty().bind(tab.emptyProperty());

		if (editable) {
			windowController.getCutMenuItem().setOnAction(e -> codeArea.cut());
			windowController.getCutMenuItem().disableProperty().bind(selectionEmpty);
		}

		windowController.getCopyMenuItem().setOnAction(e -> codeArea.copy());
		windowController.getCopyMenuItem().disableProperty().bind(selectionEmpty);

		if (editable) {
			windowController.getPasteMenuItem().setOnAction(e -> codeArea.paste());

			windowController.getDeleteMenuItem().setOnAction(e -> codeArea.clear());
			windowController.getDeleteMenuItem().disableProperty().bind(tab.emptyProperty().not());

			windowController.getUndoMenuItem().setOnAction(e -> codeArea.undo());
			{
				var undoAvailable = new SimpleBooleanProperty();
				undoAvailable.bind(codeArea.undoAvailableProperty());
				windowController.getUndoMenuItem().disableProperty().bind(undoAvailable.not());
			}

			windowController.getRedoMenuItem().setOnAction(e -> codeArea.redo());
			{
				var redoAvailable = new SimpleBooleanProperty();
				redoAvailable.bind(codeArea.redoAvailableProperty());
				windowController.getRedoMenuItem().disableProperty().bind(redoAvailable.not());
			}
		} else
			windowController.getPasteMenuItem().setDisable(true);

		windowController.getFindMenuItem().setOnAction(controller.getFindButton().getOnAction());
		if (editable) {
			windowController.getReplaceMenuItem().setOnAction(controller.getFindAndReplaceButton().getOnAction());
		}

		windowController.getFindAgainMenuItem().setOnAction((e) -> findToolBar.findAgain());
		windowController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		windowController.getGotoLineMenuItem().setOnAction((e) -> {
			final TextInputDialog dialog = new TextInputDialog("");
			if (MainWindowManager.isUseDarkTheme()) {
				dialog.getDialogPane().getScene().getWindow().getScene().getStylesheets().add("jloda/resources/css/dark.css");
			}
			dialog.setTitle("Go to Line - " + ProgramProperties.getProgramName());
			dialog.initStyle(StageStyle.UTILITY);
			dialog.setX(mainWindow.getStage().getX() + 0.5 * mainWindow.getStage().getWidth());
			dialog.setY(mainWindow.getStage().getY() + 0.5 * mainWindow.getStage().getHeight());
			dialog.setHeaderText("Go to line");
			dialog.setContentText("Line [:column]:");
			Optional<String> result = dialog.showAndWait();
			if (result.isPresent()) {
				final String[] tokens = result.get().split(":");
				if (tokens.length > 0 && NumberUtils.isInteger(tokens[0]))
					tab.gotoLine(NumberUtils.parseInt(tokens[0]), tokens.length == 2 && NumberUtils.isInteger(tokens[1]) ? NumberUtils.parseInt(tokens[1]) : 0);
			}
		});

		windowController.getSelectAllMenuItem().setOnAction(e -> codeArea.selectAll());
		windowController.getSelectAllMenuItem().disableProperty().bind(tab.emptyProperty());

		windowController.getSelectNoneMenuItem().setOnAction(e -> codeArea.selectRange(0, 0));
		windowController.getSelectNoneMenuItem().disableProperty().bind(tab.emptyProperty());

		windowController.getSelectFromPreviousMenuItem().setOnAction(e -> {
			for (String word : MainWindowManager.getPreviousSelection()) {
				final Pattern pattern = Pattern.compile(word);
				String source = codeArea.getText();
				Matcher matcher = pattern.matcher(source);

				if (matcher.find(0)) {
					codeArea.selectRange(matcher.start(), matcher.end());
					break;
				}
			}
		});
		windowController.getSelectFromPreviousMenuItem().disableProperty().bind(Bindings.isEmpty(MainWindowManager.getPreviousSelection()));

		windowController.getSelectBracketsMenuItem().setOnAction(e -> tab.selectBrackets(codeArea));
		windowController.getSelectBracketsMenuItem().disableProperty().bind(tab.emptyProperty());

		windowController.getIncreaseFontSizeMenuItem().setOnAction(null);
		windowController.getDecreaseFontSizeMenuItem().setOnAction(null);

		windowController.getZoomInMenuItem().setOnAction(null);
		windowController.getZoomOutMenuItem().setOnAction(null);
	}
}
