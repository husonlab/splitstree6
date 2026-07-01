/*
 * CheckForUpdate.java Copyright (C) 2025 Daniel H. Huson
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
 */

package splitstree6.main;

import jloda.fx.dialog.MessageInternalDialog;
import splitstree6.window.MainWindow;

/**
 * check for update
 * Daniel Huson, 6.2026
 */
public class CheckForUpdate {
	/**
	 * check for update, download and install, if present
	 */
	public static void apply(MainWindow mainWindow) {
		var text = """
				%s updates have moved to GitHub.
				Please download the latest release from: %s
				""".formatted(Version.NAME, Version.HOME_URL);
		var dialog = new MessageInternalDialog(mainWindow.getController().getRightAnchorPane(), "Updates have moved", text);
		dialog.show();
	}
}