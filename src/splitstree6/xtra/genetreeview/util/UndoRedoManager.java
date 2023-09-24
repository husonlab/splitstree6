/*
 *  UndoRedoManager.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.util;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import splitstree6.xtra.genetreeview.util.Command;

public class UndoRedoManager {

    private final ObservableList<Command> undoStack = FXCollections.observableArrayList();
    private final ObservableList<Command> redoStack = FXCollections.observableArrayList();

    private final StringProperty undoLabel = new SimpleStringProperty("Undo");
    private final StringProperty redoLabel = new SimpleStringProperty("Redo");

    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);

    private final BooleanProperty inUndoRedo = new SimpleBooleanProperty(false);

    public UndoRedoManager() {
        undoStack.addListener((InvalidationListener) e -> {
            undoLabel.set("Undo" + (undoStack.size() == 0 ? "":"_"+undoStack.get(undoStack.size()-1).name()));
        });
        redoStack.addListener((InvalidationListener) e ->  {
            redoLabel.set("Redo" + (redoStack.size() == 0 ? "":"_"+redoStack.get(redoStack.size()-1).name()));
        });
        canUndo.bind(Bindings.isNotEmpty(undoStack));
        canRedo.bind(Bindings.isNotEmpty(redoStack));
    }

    public void undo() {
        inUndoRedo.set(true);
        try {
            if (isCanUndo()) {
                var command = undoStack.remove(undoStack.size()-1);
                command.undo();
                if (command.canRedo())
                    redoStack.add(command);
                else
                    redoStack.clear();
            }
        } finally {
            inUndoRedo.set(false);
        }
    }

    private boolean isCanUndo() {
        return !undoStack.isEmpty();
    }

    public void redo() {
        inUndoRedo.set(true);
        try {
            if (isCanRedo()) {
                var command = redoStack.remove(redoStack.size()-1);
                command.redo();
                if (command.canUndo())
                    undoStack.add(command);
                else
                    undoStack.clear();
            }
        } finally {
            inUndoRedo.set(false);
        }
    }

    private boolean isCanRedo() {
        return !redoStack.isEmpty();
    }

    public void add(Command command) {
        if (!inUndoRedo()) {
            if (command.canUndo())
                undoStack.add(command);
            else
                undoStack.clear();
        }
    }

    private boolean inUndoRedo() {
        return inUndoRedo.get();
    }

    public ObservableValue<String> undoLabelProperty() {
        return undoLabel;
    }
    public ObservableValue<String> redoLabelProperty() {
        return redoLabel;
    }

    public BooleanProperty canUndoProperty() {
        return canUndo;
    }

    public BooleanProperty canRedoProperty() {
        return canRedo;
    }
}