/*
 *  TaxLabelFormatterPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.taxlabels;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import jloda.fx.control.RichTextLabel;
import jloda.util.NumberUtils;
import splitstree6.data.parts.Taxon;
import splitstree6.window.MainWindow;

import java.util.HashSet;

public class TaxLabelFormatterPresenter {
	private final InvalidationListener selectionListener;

	private final static ObservableList<String> fontFamilies = FXCollections.observableArrayList(Font.getFamilies());

	private boolean inUpdatingDefaults = false;

	public TaxLabelFormatterPresenter(MainWindow mainWindow, TaxLabelFormatterController controller, ObservableMap<Taxon, RichTextLabel> taxonLabelMap) {
		var selectionModel = mainWindow.getTaxonSelectionModel();

		controller.getFontFamilyCbox().setItems(fontFamilies);
		new AutoCompleteComboBoxListener<>(controller.getFontFamilyCbox());

		controller.getFontFamilyCbox().setValue((new RichTextLabel()).getFontFamily());
		controller.getFontFamilyCbox().valueProperty().addListener((v, o, n) -> {
			if (!inUpdatingDefaults && n != null && !n.isBlank()) {
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = taxon.getDisplayLabelOrName();
					taxon.setDisplayLabel(RichTextLabel.setFontFamily(text, n));
					var label = taxonLabelMap.get(taxon);
					if (label != null)
						Platform.runLater(() -> label.setText(text));
				}
			}
		});

		controller.getFontSizeTextArea().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var size = Math.max(0.1, NumberUtils.parseDouble(controller.getFontSizeTextArea().getText()));
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = RichTextLabel.setFontSize(taxon.getDisplayLabelOrName(), size);
					taxon.setDisplayLabel(text);
					var label = taxonLabelMap.get(taxon);
					if (label != null)
						Platform.runLater(() -> label.setText(text));
				}
			}
		});

		controller.getBoldToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = RichTextLabel.setBold(taxon.getDisplayLabelOrName(), controller.getBoldToggleButton().isSelected());
					taxon.setDisplayLabel(text);
					var label = taxonLabelMap.get(taxon);
					if (label != null)
						Platform.runLater(() -> label.setText(text));
				}
			}
		});

		controller.getItalicToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = RichTextLabel.setItalic(taxon.getDisplayLabelOrName(), controller.getItalicToggleButton().isSelected());
					taxon.setDisplayLabel(text);
					var label = taxonLabelMap.get(taxon);
					if (label != null)
						Platform.runLater(() -> label.setText(text));
				}
			}
		});

		controller.getUnderlineToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = RichTextLabel.setUnderline(taxon.getDisplayLabelOrName(), controller.getUnderlineToggleButton().isSelected());
					taxon.setDisplayLabel(text);
					var label = taxonLabelMap.get(taxon);
					if (label != null)
						Platform.runLater(() -> label.setText(text));
				}
			}
		});

		controller.getStrikeToggleButton().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = RichTextLabel.setStrike(taxon.getDisplayLabelOrName(), controller.getStrikeToggleButton().isSelected());
					taxon.setDisplayLabel(text);
					var label = taxonLabelMap.get(taxon);
					if (label != null)
						Platform.runLater(() -> label.setText(text));
				}
			}
		});

		controller.getTextFillColorChooser().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var color = controller.getTextFillColorChooser().getValue();
				for (var taxon : selectionModel.getSelectedItems()) {
					var text = RichTextLabel.setTextFill(taxon.getDisplayLabelOrName(), color);
					taxon.setDisplayLabel(text);
					var label = taxonLabelMap.get(taxon);
					if (label != null)
						Platform.runLater(() -> label.setText(text));
				}
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
					var text = taxon.getDisplayLabelOrName();
					fontFamilies.add(RichTextLabel.getFontFamily(text));
					fontSizes.add(RichTextLabel.getFontSize(text));
					boldStates.add(RichTextLabel.isBold(text));
					italicStates.add(RichTextLabel.isItalic(text));
					underlineStates.add(RichTextLabel.isUnderline(text));
					strikeStates.add(RichTextLabel.isStrike(text));
					colors.add(RichTextLabel.getTextFill(text));
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

	public class AutoCompleteComboBoxListener<T> implements EventHandler<KeyEvent> {

		private final ComboBox<T> comboBox;
		private final ObservableList<T> data;
		private boolean moveCaretToPos = false;
		private int caretPos;

		public AutoCompleteComboBoxListener(final ComboBox<T> comboBox) {
			this.comboBox = comboBox;
			StringBuilder sb = new StringBuilder();
			data = comboBox.getItems();

			this.comboBox.setEditable(true);
			this.comboBox.setOnKeyPressed(new EventHandler<KeyEvent>() {

				@Override
				public void handle(KeyEvent t) {
					comboBox.hide();
				}
			});
			this.comboBox.setOnKeyReleased(AutoCompleteComboBoxListener.this);
		}

		@Override
		public void handle(KeyEvent event) {
			if (event.getCode() == KeyCode.UP) {
				caretPos = -1;
				moveCaret(comboBox.getEditor().getText().length());
				return;
			} else if (event.getCode() == KeyCode.DOWN) {
				if (!comboBox.isShowing()) {
					comboBox.show();
				}
				caretPos = -1;
				moveCaret(comboBox.getEditor().getText().length());
				return;
			} else if (event.getCode() == KeyCode.BACK_SPACE) {
				moveCaretToPos = true;
				caretPos = comboBox.getEditor().getCaretPosition();
			} else if (event.getCode() == KeyCode.DELETE) {
				moveCaretToPos = true;
				caretPos = comboBox.getEditor().getCaretPosition();
			}

			if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.LEFT
				|| event.isControlDown() || event.getCode() == KeyCode.HOME
				|| event.getCode() == KeyCode.END || event.getCode() == KeyCode.TAB) {
				return;
			}

			ObservableList<T> list = FXCollections.observableArrayList();
			for (T datum : data) {
				if (datum.toString().toLowerCase().startsWith(
						AutoCompleteComboBoxListener.this.comboBox
								.getEditor().getText().toLowerCase())) {
					list.add(datum);
				}
			}
			String t = comboBox.getEditor().getText();

			comboBox.setItems(list);
			comboBox.getEditor().setText(t);
			if (!moveCaretToPos) {
				caretPos = -1;
			}
			moveCaret(t.length());
			if (!list.isEmpty()) {
				comboBox.show();
			}
		}

		private void moveCaret(int textLength) {
			if (caretPos == -1) {
				comboBox.getEditor().positionCaret(textLength);
			} else {
				comboBox.getEditor().positionCaret(caretPos);
			}
			moveCaretToPos = false;
		}

	}
}
