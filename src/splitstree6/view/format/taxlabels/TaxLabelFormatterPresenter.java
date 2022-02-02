/*
 * TaxLabelFormatterPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.taxlabels;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import jloda.fx.control.RichTextLabel;
import jloda.fx.undo.UndoManager;
import jloda.fx.undo.UndoableRedoableCommandList;
import jloda.fx.util.AutoCompleteComboBox;
import jloda.util.NumberUtils;
import splitstree6.data.parts.Taxon;
import splitstree6.window.MainWindow;

import java.util.HashSet;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class TaxLabelFormatterPresenter {
	private final InvalidationListener selectionListener;

	private final static ObservableList<String> fontFamilies = FXCollections.observableArrayList(Font.getFamilies());

	private boolean inUpdatingDefaults = false;

	public TaxLabelFormatterPresenter(MainWindow mainWindow, TaxLabelFormatterController controller, UndoManager undoManager,
									  Function<Taxon, String> taxonLabelGetter, BiConsumer<Taxon, String> taxonLabelSetter) {
		var selectionModel = mainWindow.getTaxonSelectionModel();

		controller.getFontFamilyCbox().setItems(fontFamilies);
		AutoCompleteComboBox.install(controller.getFontFamilyCbox());

		controller.getFontFamilyCbox().setValue((new RichTextLabel()).getFontFamily());
		controller.getFontFamilyCbox().valueProperty().addListener((v, o, n) -> {
			if (!inUpdatingDefaults && n != null && !n.isBlank() && controller.getFontFamilyCbox().getItems().contains(n)) {
				var undoList = new UndoableRedoableCommandList("set font");
				for (var taxon : selectionModel.getSelectedItems()) {
					var oldLabel = taxonLabelGetter.apply(taxon);
					if (oldLabel != null && !RichTextLabel.getFontFamily(oldLabel).equals(n)) {
						var newLabel = RichTextLabel.setFontFamily(oldLabel, n);
						Platform.runLater(() -> taxonLabelSetter.accept(taxon, newLabel));
						undoList.add(() -> taxonLabelSetter.accept(taxon, oldLabel), () -> taxonLabelSetter.accept(taxon, newLabel));
					}
				}
				if (undoList.size() > 0)
					undoManager.add(undoList);
			}
		});

		controller.getFontSizeTextArea().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var undoList = new UndoableRedoableCommandList("set font size");
				var size = Math.max(0.1, NumberUtils.parseDouble(controller.getFontSizeTextArea().getText()));
				for (var taxon : selectionModel.getSelectedItems()) {
					var oldLabel = taxonLabelGetter.apply(taxon);
					if (oldLabel != null && RichTextLabel.getFontSize(oldLabel) != size) {
						var newLabel = RichTextLabel.setFontSize(oldLabel, size);
						Platform.runLater(() -> taxonLabelSetter.accept(taxon, newLabel));
						undoList.add(() -> taxonLabelSetter.accept(taxon, oldLabel), () -> taxonLabelSetter.accept(taxon, newLabel));
					}
				}
				if (undoList.size() > 0)
					undoManager.add(undoList);
			}
		});

		controller.getBoldToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var undoList = new UndoableRedoableCommandList("set bold");
				for (var taxon : selectionModel.getSelectedItems()) {
					var oldLabel = taxonLabelGetter.apply(taxon);
					if (oldLabel != null && RichTextLabel.isBold(oldLabel) != controller.getBoldToggleButton().isSelected()) {
						var newLabel = RichTextLabel.setBold(oldLabel, controller.getBoldToggleButton().isSelected());
						Platform.runLater(() -> taxonLabelSetter.accept(taxon, newLabel));
						undoList.add(() -> taxonLabelSetter.accept(taxon, oldLabel), () -> taxonLabelSetter.accept(taxon, newLabel));
					}
				}
				if (undoList.size() > 0)
					undoManager.add(undoList);
			}
		});

		controller.getItalicToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var undoList = new UndoableRedoableCommandList("set italic");
				for (var taxon : selectionModel.getSelectedItems()) {
					var oldLabel = taxonLabelGetter.apply(taxon);
					if (oldLabel != null && RichTextLabel.isItalic(oldLabel) != controller.getItalicToggleButton().isSelected()) {
						var newLabel = RichTextLabel.setItalic(oldLabel, controller.getItalicToggleButton().isSelected());
						Platform.runLater(() -> taxonLabelSetter.accept(taxon, newLabel));
						undoList.add(() -> taxonLabelSetter.accept(taxon, oldLabel), () -> taxonLabelSetter.accept(taxon, newLabel));
					}
				}
				if (undoList.size() > 0)
					undoManager.add(undoList);
			}
		});

		controller.getUnderlineToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var undoList = new UndoableRedoableCommandList("set underline");
				for (var taxon : selectionModel.getSelectedItems()) {
					var oldLabel = taxonLabelGetter.apply(taxon);
					if (oldLabel != null && RichTextLabel.isUnderline(oldLabel) != controller.getUnderlineToggleButton().isSelected()) {
						var newLabel = RichTextLabel.setUnderline(oldLabel, controller.getUnderlineToggleButton().isSelected());
						Platform.runLater(() -> taxonLabelSetter.accept(taxon, newLabel));
						undoList.add(() -> taxonLabelSetter.accept(taxon, oldLabel), () -> taxonLabelSetter.accept(taxon, newLabel));
					}
				}
				if (undoList.size() > 0)
					undoManager.add(undoList);
			}
		});

		controller.getStrikeToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var undoList = new UndoableRedoableCommandList("set strike");
				for (var taxon : selectionModel.getSelectedItems()) {
					var oldLabel = taxonLabelGetter.apply(taxon);
					if (oldLabel != null && RichTextLabel.isStrike(oldLabel) != controller.getStrikeToggleButton().isSelected()) {
						var newLabel = RichTextLabel.setStrike(oldLabel, controller.getStrikeToggleButton().isSelected());
						Platform.runLater(() -> taxonLabelSetter.accept(taxon, newLabel));
						undoList.add(() -> taxonLabelSetter.accept(taxon, oldLabel), () -> taxonLabelSetter.accept(taxon, newLabel));
					}
				}
				if (undoList.size() > 0)
					undoManager.add(undoList);
			}
		});

		controller.getTextFillColorChooser().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var color = controller.getTextFillColorChooser().getValue();
				var undoList = new UndoableRedoableCommandList("set label color");
				for (var taxon : selectionModel.getSelectedItems()) {
					var oldLabel = taxonLabelGetter.apply(taxon);
					if (oldLabel != null && !RichTextLabel.getTextFill(oldLabel).equals(color)) {
						var newLabel = RichTextLabel.setTextFill(oldLabel, color);
						Platform.runLater(() -> taxonLabelSetter.accept(taxon, newLabel));
						undoList.add(() -> taxonLabelSetter.accept(taxon, oldLabel), () -> taxonLabelSetter.accept(taxon, newLabel));
					}
				}
				if (undoList.size() > 0)
					undoManager.add(undoList);
			}
		});

		selectionListener = e -> {
			inUpdatingDefaults = true;
			try {
				controller.getFontFamilyCbox().setDisable(selectionModel.size() == 0);
				controller.getFontSizeTextArea().setDisable(selectionModel.size() == 0);
				controller.getBoldToggleButton().setDisable(selectionModel.size() == 0);
				controller.getItalicToggleButton().setDisable(selectionModel.size() == 0);
				controller.getUnderlineToggleButton().setDisable(selectionModel.size() == 0);
				controller.getStrikeToggleButton().setDisable(selectionModel.size() == 0);
				controller.getTextFillColorChooser().setDisable(selectionModel.size() == 0);

				controller.getFontSizeTextArea().setText("");
				controller.getBoldToggleButton().setSelected(false);
				controller.getItalicToggleButton().setSelected(false);
				controller.getUnderlineToggleButton().setSelected(false);
				controller.getStrikeToggleButton().setSelected(false);
				controller.getTextFillColorChooser().setValue(null);

				var fontFamilies = new HashSet<String>();
				var fontSizes = new HashSet<Double>();
				var boldStates = new HashSet<Boolean>();
				var italicStates = new HashSet<Boolean>();
				var underlineStates = new HashSet<Boolean>();
				var strikeStates = new HashSet<Boolean>();
				var colors = new HashSet<Paint>();
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = taxonLabelGetter.apply(taxon);
					if (text != null) {
						fontFamilies.add(RichTextLabel.getFontFamily(text));
						fontSizes.add(RichTextLabel.getFontSize(text));
						boldStates.add(RichTextLabel.isBold(text));
						italicStates.add(RichTextLabel.isItalic(text));
						underlineStates.add(RichTextLabel.isUnderline(text));
						strikeStates.add(RichTextLabel.isStrike(text));
						colors.add(RichTextLabel.getTextFill(text));
					}
				}
				controller.getFontFamilyCbox().setValue(fontFamilies.size() == 1 ? fontFamilies.iterator().next() : null);
				controller.getFontSizeTextArea().setText(fontSizes.size() == 1 ? String.valueOf(fontSizes.iterator().next()) : "");
				controller.getBoldToggleButton().setSelected(boldStates.size() == 1 ? boldStates.iterator().next() : false);
				controller.getItalicToggleButton().setSelected(boldStates.size() == 1 ? italicStates.iterator().next() : false);
				controller.getUnderlineToggleButton().setSelected(boldStates.size() == 1 ? underlineStates.iterator().next() : false);
				controller.getStrikeToggleButton().setSelected(strikeStates.size() == 1 ? strikeStates.iterator().next() : false);
				controller.getTextFillColorChooser().setValue(colors.size() == 1 ? (Color) colors.iterator().next() : null);
			} finally {
				inUpdatingDefaults = false;
			}
		};

		//selectionModel.getSelectedItems().addListener(selectionListener);
		selectionModel.getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));
		selectionListener.invalidated(null);
	}

}