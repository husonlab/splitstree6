/*
 * DisplayTextViewPresenter.java Copyright (C) 2023 Daniel H. Huson
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


import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.StageStyle;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.BasicFX;
import jloda.fx.util.Print;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.util.NumberUtils;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.AlgorithmBreadCrumbsToolBar;
import splitstree6.view.utils.FindReplaceUtils;
import splitstree6.window.MainWindow;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisplayTextViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final DisplayTextView tab;

	private final FindToolBar findToolBar;

	private final boolean editable;

	private final BooleanBinding selectionEmpty;

	public DisplayTextViewPresenter(MainWindow mainWindow, DisplayTextView tab, boolean editable) {
		this.mainWindow = mainWindow;
		this.tab = tab;
		this.editable = editable;

		var controller = tab.getController();

		var codeArea = controller.getCodeArea();

		codeArea.setEditable(editable);

		controller.getWrapTextToggle().selectedProperty().bindBidirectional(codeArea.wrapTextProperty());
		codeArea.setWrapText(true);

		findToolBar = new FindToolBar(null, new CodeAreaSearcher("Text", codeArea));
		controller.getTopVBox().getChildren().add(findToolBar);

		selectionEmpty = Bindings.createBooleanBinding(() -> codeArea.getSelection().getLength() == 0, codeArea.selectionProperty());

		codeArea.selectionProperty().addListener((c, o, n) -> {
			if (n.getLength() > 0) {
				MainWindowManager.getPreviousSelection().clear();
				MainWindowManager.getPreviousSelection().add(codeArea.getText(n.getStart(), n.getEnd()));
			}
		});

		FindReplaceUtils.setup(findToolBar, controller.getFindToggleButton(), editable);

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


		if (false && editable) {
			codeArea.focusedProperty().addListener((c, o, n) -> {
				if (n) {
					mainWindow.getController().getPasteMenuItem().disableProperty().unbind();
					mainWindow.getController().getPasteMenuItem().disableProperty().set(!Clipboard.getSystemClipboard().hasString());
				}
			});
		}

		controller.getIncreaseFontButton().setOnAction(e -> {
			tab.setFontSize(1.1 * tab.getFontSize());
			codeArea.setStyle("-fx-font-size: " + tab.getFontSize() + "px");
		});
		controller.getIncreaseFontButton().disableProperty().bind(tab.fontSizeProperty().greaterThan(128));

		controller.getDecreaseFontButton().setOnAction(e -> {
			tab.setFontSize(1.0 / 1.1 * tab.getFontSize());
			codeArea.setStyle("-fx-font-size: " + tab.getFontSize() + "px");
		});
		controller.getDecreaseFontButton().disableProperty().bind(tab.fontSizeProperty().lessThan(6));

		codeArea.setStyle("-fx-font-size: " + tab.getFontSize() + "px");

		tab.viewTabProperty().addListener((v, o, n) -> {
			if (n != null && n.getAlgorithmBreadCrumbsToolBar() != null
				&& BasicFX.getAllRecursively(controller.getTopVBox(), AlgorithmBreadCrumbsToolBar.class).size() == 0
				&& !tab.getViewTab().isClosable()) {
				controller.getTopVBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();
		var controller = tab.getController();

		var codeArea = controller.getCodeArea();

		mainController.getPrintMenuItem().setOnAction(e -> Print.printText(mainWindow.getStage(), codeArea.getText()));
		mainController.getPrintMenuItem().disableProperty().bind(tab.emptyProperty());

		mainController.getCopyMenuItem().setOnAction(e -> codeArea.copy());
		mainController.getCopyMenuItem().disableProperty().bind(selectionEmpty);

		if (editable) {
			mainController.getCutMenuItem().setOnAction(e -> codeArea.cut());
			mainController.getCutMenuItem().disableProperty().bind(selectionEmpty);

			mainController.getPasteMenuItem().setOnAction(e -> codeArea.paste());
			mainController.getPasteMenuItem().setDisable(false);

			mainController.getDeleteMenuItem().setOnAction(e -> codeArea.clear());
			mainController.getDeleteMenuItem().disableProperty().bind(tab.emptyProperty().not());

			mainController.getUndoMenuItem().setOnAction(e -> codeArea.undo());
			{
				var undoAvailable = new SimpleBooleanProperty();
				undoAvailable.bind(codeArea.undoAvailableProperty());
				mainController.getUndoMenuItem().disableProperty().bind(undoAvailable.not());
			}

			mainController.getRedoMenuItem().setOnAction(e -> codeArea.redo());
			{
				var redoAvailable = new SimpleBooleanProperty();
				redoAvailable.bind(codeArea.redoAvailableProperty());
				mainController.getRedoMenuItem().disableProperty().bind(redoAvailable.not());
			}

		} else {
			mainController.getPasteMenuItem().disableProperty().unbind();
			mainController.getPasteMenuItem().setDisable(true);
		}

		mainController.getFindMenuItem().setOnAction(e -> findToolBar.setShowFindToolBar(true));
		mainController.getFindMenuItem().setDisable(false);
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
		if (editable) {
			mainController.getReplaceMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(true));
			mainController.getReplaceMenuItem().setDisable(false);
		}


		{
			var cut = new MenuItem("Cut");
			cut.setOnAction(a -> codeArea.cut());
			var copy = new MenuItem("Copy");
			copy.setOnAction(a -> codeArea.copy());
			var paste = new MenuItem("Paste");
			paste.setOnAction(a -> codeArea.paste());
			if (editable)
				codeArea.setContextMenu(new ContextMenu(cut, copy, paste));
			else
				codeArea.setContextMenu(new ContextMenu(copy));
		}

		mainController.getGotoLineMenuItem().setOnAction((e) -> {
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

		mainController.getSelectAllMenuItem().setOnAction(e -> codeArea.selectAll());
		mainController.getSelectAllMenuItem().disableProperty().bind(tab.emptyProperty());

		mainController.getSelectNoneMenuItem().setOnAction(e -> codeArea.selectRange(0, 0));
		mainController.getSelectNoneMenuItem().disableProperty().bind(tab.emptyProperty());

		mainController.getSelectFromPreviousMenuItem().setOnAction(e -> {
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
		mainController.getSelectFromPreviousMenuItem().disableProperty().bind(Bindings.isEmpty(MainWindowManager.getPreviousSelection()));

		mainController.getSelectBracketsMenuItem().setOnAction(e -> tab.selectBrackets(codeArea));
		mainController.getSelectBracketsMenuItem().disableProperty().bind(tab.emptyProperty());

		mainController.getIncreaseFontSizeMenuItem().setOnAction(controller.getIncreaseFontButton().getOnAction());
		mainController.getIncreaseFontSizeMenuItem().disableProperty().bind(controller.getIncreaseFontButton().disableProperty());

		mainController.getDecreaseFontSizeMenuItem().setOnAction(controller.getDecreaseFontButton().getOnAction());
		mainController.getDecreaseFontSizeMenuItem().disableProperty().bind(controller.getDecreaseFontButton().disableProperty());

		mainController.getZoomInMenuItem().setOnAction(null);
		mainController.getZoomOutMenuItem().setOnAction(null);
	}
}
