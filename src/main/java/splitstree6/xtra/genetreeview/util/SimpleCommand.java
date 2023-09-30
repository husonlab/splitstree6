/*
 *  SimpleCommand.java Copyright (C) 2023 Daniel H. Huson
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

import splitstree6.xtra.genetreeview.util.Command;

public class SimpleCommand implements Command {
    private final String name;
    private final Runnable undoRunnable;
    private final Runnable redoRunnable;

    public SimpleCommand(String name, Runnable undoRunnable, Runnable redoRunnable) {
        this.name = name;
        this.undoRunnable = undoRunnable;
        this.redoRunnable = redoRunnable;
    }

    public void undo() {
        undoRunnable.run();
    }

    public void redo() {
        redoRunnable.run();
    }

    public String name() {
        return name;
    }

    public boolean canUndo() {
        return undoRunnable!=null;
    }

    public boolean canRedo() {
        return redoRunnable!=null;
    }
}
