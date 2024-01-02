/*
 * InputEditorTab.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tabs.displaytext;

import javafx.application.Platform;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.window.MainWindow;

/**
 * text display tab
 * Daniel Huson, 4.2022
 */
public class DisplayTextTab extends ViewTab {
	private final DisplayTextView displayTextView;

	/**
	 * constructor
	 */
	public DisplayTextTab(MainWindow mainWindow, String name, boolean closable) {
		super(mainWindow, null, closable);
		this.displayTextView = new DisplayTextView(mainWindow, name, false);
		Platform.runLater(() -> {
			setView(displayTextView);
			displayTextView.getController().getCodeArea().requestFocus();
		});
		displayTextView.getController().getCodeArea().textProperty().addListener(e -> setEmpty(displayTextView.getController().getCodeArea().getText().isEmpty()));
	}

	public void replaceText(String text) {
		displayTextView.replaceText(text);
	}

	public DisplayTextView getDisplayTextView() {
		return displayTextView;
	}
}

