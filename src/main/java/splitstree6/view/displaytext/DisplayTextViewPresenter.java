/*
 * DisplayTextViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.StageStyle;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ClipboardUtils;
import jloda.fx.util.Print;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.util.NumberUtils;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.AlgorithmBreadCrumbsToolBar;
import splitstree6.utils.SwipeUtils;
import splitstree6.view.findreplace.FindReplaceUtils;
import splitstree6.window.MainWindow;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisplayTextViewPresenter implements IDisplayTabPresenter {
	private final double maxFontSize = 128;
	private final double minFontSize = 6;
	private final MainWindow mainWindow;
	private final DisplayTextView view;

	private final FindToolBar findToolBar;

	private final boolean editable;

	private final BooleanBinding selectionEmpty;

	public DisplayTextViewPresenter(MainWindow mainWindow, DisplayTextView view, boolean editable) {
		this.mainWindow = mainWindow;
		this.view = view;
		this.editable = editable;

		var controller = view.getController();

		var codeArea = controller.getCodeArea();

		codeArea.setEditable(editable);

		controller.getWrapTextToggle().selectedProperty().bindBidirectional(codeArea.wrapTextProperty());
		codeArea.setWrapText(true);

		controller.getCodeArea().getNode().setOnZoom(e -> {
			if (e.getZoomFactor() > 1 && view.getFontSize() < maxFontSize || e.getZoomFactor() < 1 && view.getFontSize() > minFontSize) {
				view.setFontSize(e.getZoomFactor() * view.getFontSize());
				codeArea.setStyle("-fx-font-size: " + view.getFontSize() + "px");
			}
		});

		findToolBar = new FindToolBar(mainWindow.getStage(), codeArea.createSearcher());
		FindReplaceUtils.additionalSetup(findToolBar);
		controller.getTopVBox().getChildren().add(findToolBar);

		selectionEmpty = Bindings.createBooleanBinding(() -> codeArea.getSelection().getLength() == 0, codeArea.selectionProperty());

		codeArea.selectionProperty().addListener((c, o, n) -> {
			if (n.getLength() > 0) {
				MainWindowManager.getPreviousSelection().clear();
				MainWindowManager.getPreviousSelection().add(codeArea.getText(n.getStart(), n.getEnd()));
			}
		});

		controller.getWrapTextToggle().selectedProperty().bindBidirectional(view.wrapTextProperty());
		controller.getLineNumbersToggle().selectedProperty().bindBidirectional(view.showLineNumbersProperty());


		// prevent double paste:
		{
			codeArea.addEventFilter(KeyEvent.ANY, e -> {
				if (e.getCode() == KeyCode.V && e.isShortcutDown()) {
					e.consume();
				}
			});
		}

		controller.getCopyButton().setOnAction(e -> {
			if (codeArea.getSelection().getLength() > 0)
				codeArea.copy();
			else {
				ClipboardUtils.putString(codeArea.getText());
			}
		});
		controller.getCopyButton().disableProperty().bind(view.emptyProperty());

		if (!editable) {
			controller.getToolBar().getItems().remove(controller.getPasteButton());
		} else {
			controller.getPasteButton().setOnAction(e -> codeArea.paste());
			controller.getPasteButton().disableProperty().bind(ClipboardUtils.hasStringProperty().not());
		}
		codeArea.setStyle("-fx-font-size: " + view.getFontSize() + "px;");

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null && n.getAlgorithmBreadCrumbsToolBar() != null
				&& BasicFX.getAllRecursively(controller.getTopVBox(), AlgorithmBreadCrumbsToolBar.class).isEmpty()
				&& !view.getViewTab().isClosable()) {
				controller.getTopVBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});

		SwipeUtils.setConsumeSwipes(controller.getAnchorPane());
	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();
		var controller = view.getController();

		var codeArea = controller.getCodeArea();

		mainController.getPrintMenuItem().setOnAction(e -> Print.printText(mainWindow.getStage(), codeArea.getText()));
		mainController.getPrintMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getCopyMenuItem().setOnAction(controller.getCopyButton().getOnAction());
		mainController.getCopyMenuItem().disableProperty().bind(controller.getCopyButton().disabledProperty());

		if (editable) {
			mainController.getCutMenuItem().setOnAction(e -> codeArea.cut());
			mainController.getCutMenuItem().disableProperty().bind(selectionEmpty);

			mainController.getPasteMenuItem().setOnAction(controller.getPasteButton().getOnAction());
			mainController.getPasteMenuItem().disableProperty().bind(controller.getPasteButton().disableProperty());

			mainController.getDeleteMenuItem().setOnAction(e -> codeArea.clear());
			mainController.getDeleteMenuItem().disableProperty().bind(view.emptyProperty().not());

			mainController.getUndoButton().setOnAction(e -> codeArea.undo());
			{
				var undoAvailable = new SimpleBooleanProperty();
				undoAvailable.bind(codeArea.undoAvailableProperty());
				mainController.getUndoButton().disableProperty().bind(undoAvailable.not());
			}

			mainController.getRedoButton().setOnAction(e -> codeArea.redo());
			{
				var redoAvailable = new SimpleBooleanProperty();
				redoAvailable.bind(codeArea.redoAvailableProperty());
				mainController.getRedoButton().disableProperty().bind(redoAvailable.not());
			}

		} else {
			mainController.getPasteMenuItem().disableProperty().unbind();
			mainController.getPasteMenuItem().setDisable(true);
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
					view.gotoLine(NumberUtils.parseInt(tokens[0]), tokens.length == 2 && NumberUtils.isInteger(tokens[1]) ? NumberUtils.parseInt(tokens[1]) : 0);
			}
		});

		mainController.getSelectAllMenuItem().setOnAction(e -> codeArea.selectAll());
		mainController.getSelectAllMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getSelectNoneMenuItem().setOnAction(e -> codeArea.selectRange(0, 0));
		mainController.getSelectNoneMenuItem().disableProperty().bind(view.emptyProperty());

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

		mainController.getSelectBracketsMenuItem().setOnAction(e -> view.selectBrackets(codeArea));
		mainController.getSelectBracketsMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getIncreaseFontSizeMenuItem().setOnAction(e -> {
			view.setFontSize(1.1 * view.getFontSize());
			codeArea.setStyle("-fx-font-size: " + view.getFontSize() + "px");
		});
		mainController.getIncreaseFontSizeMenuItem().disableProperty().bind(view.fontSizeProperty().greaterThan(maxFontSize));

		mainController.getDecreaseFontSizeMenuItem().setOnAction(e -> {
			view.setFontSize(1.0 / 1.1 * view.getFontSize());
			codeArea.setStyle("-fx-font-size: " + view.getFontSize() + "px");
		});
		mainController.getDecreaseFontSizeMenuItem().disableProperty().bind(view.fontSizeProperty().lessThan(minFontSize));
		mainController.getZoomInMenuItem().setOnAction(null);
		mainController.getZoomOutMenuItem().setOnAction(null);
	}

	@Override
	public FindToolBar getFindToolBar() {
		return findToolBar;
	}

	@Override
	public boolean allowFindReplace() {
		return editable;
	}

	public void processSelectButtonPressed() {
		if (view.getController().getCodeArea().getSelection().getLength() > 0)
			view.getController().getCodeArea().selectRange(0, 0);
		else
			view.getController().getCodeArea().selectAll();
	}
}
