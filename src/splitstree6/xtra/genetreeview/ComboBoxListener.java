/*
 *  ComboBoxListener.java Copyright (C) 2023 Daniel H. Huson
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
 *
 * This class has been implemented using some code
 * from https://stackoverflow.com/questions/19924852/autocomplete-combobox-in-javafx
 */

package splitstree6.xtra.genetreeview;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class ComboBoxListener implements EventHandler<KeyEvent> {

    private final ComboBox<String> comboBox;
    private final ObservableList<String> data;

    public ComboBoxListener(final ComboBox<String> comboBox) {
        this.comboBox = comboBox;
        data = comboBox.getItems();

        this.comboBox.setEditable(true);
        this.comboBox.setOnKeyPressed(e -> comboBox.hide());
        this.comboBox.setOnKeyReleased(ComboBoxListener.this);
    }

    @Override
    public void handle(KeyEvent e) {

        if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.LEFT
                || e.isControlDown() || e.getCode() == KeyCode.HOME
                || e.getCode() == KeyCode.END || e.getCode() == KeyCode.TAB
                || e.getCode() == KeyCode.ENTER) {
            return;
        }
        if(e.getCode() == KeyCode.UP) {
            comboBox.getEditor().positionCaret(comboBox.getEditor().getText().length());
            return;
        }
        if(e.getCode() == KeyCode.DOWN) {
            if(!comboBox.isShowing()) {
                comboBox.show();
            }
            comboBox.getEditor().positionCaret(comboBox.getEditor().getText().length());
            return;
        }

        ObservableList<String> list = FXCollections.observableArrayList();
        for (String item : data) {
            if (item.toLowerCase().startsWith(
                    comboBox.getEditor().getText().toLowerCase())) {
                list.add(item);
            }
        }
        String t = comboBox.getEditor().getText();
        comboBox.setItems(list);
        comboBox.getEditor().setText(t);
        comboBox.getEditor().positionCaret(comboBox.getEditor().getText().length());
        if(!list.isEmpty()) {
            comboBox.show();
        }
    }
}