/*
 *  InputNoteEditor.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tabs.displaytext;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.RunAfterAWhile;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.window.MainWindow;

import java.util.Objects;

/**
 * adds an "Edit Input" toggle to a display-text view's toolbar. When selected, a titled pane appears
 * at the top with an editable text area bound to the input taxa block's comments, i.e. the free text
 * that is stored at the top of a nexus/SplitsTree6 file and shown under "Input:" in the "How to Cite"
 * tab. Editing writes the note back (so it is saved), marks the document dirty and refreshes the tab.
 * Daniel Huson, 8.2026
 */
public class InputNoteEditor {
	/**
	 * set up input-note editing on the given display-text view (the "How to Cite" tab)
	 */
	public static void setup(MainWindow mainWindow, DisplayTextView displayTextView) {
		var controller = displayTextView.getController();

		// the toggle in the tab's toolbar
		var editToggle = new ToggleButton("Edit Input");
		editToggle.setFont(new Font(10.0));
		MaterialIcons.setIcon(editToggle, MaterialIcons.edit_note);
		editToggle.setTooltip(new Tooltip("Edit the note on the origin of the input data (saved at the top of the file)"));
		editToggle.disableProperty().bind(mainWindow.emptyProperty());
		controller.getToolBar().getItems().add(editToggle);

		// the editable note, shown at the top when the toggle is selected
		var textArea = new TextArea();
		textArea.setPromptText("Enter a note on the origin of the input data. It is saved at the top of the nexus/SplitsTree6 file and shown under \"Input:\".");
		textArea.setWrapText(true);
		textArea.setPrefRowCount(4);
		var titledPane = new TitledPane("Input", textArea);
		titledPane.setAnimated(false);
		titledPane.setCollapsible(false);
		titledPane.visibleProperty().bind(editToggle.selectedProperty());
		titledPane.managedProperty().bind(editToggle.selectedProperty());
		controller.getTopVBox().getChildren().add(titledPane);

		// true while the text area is set programmatically, to avoid writing back to the taxa block
		var updating = new SimpleBooleanProperty(false);

		// copy the note stored in the current input taxa block into the text area
		Runnable refresh = () -> {
			updating.set(true);
			try {
				var taxaBlock = mainWindow.getWorkflow().getInputTaxaBlock();
				var comments = (taxaBlock == null ? null : taxaBlock.getComments());
				textArea.setText(comments == null ? "" : comments);
			} finally {
				updating.set(false);
			}
		};

		// refresh when a document (re)loads and whenever the editor is opened
		mainWindow.getWorkflow().validProperty().addListener((v, o, n) -> {
			if (n)
				refresh.run();
		});
		editToggle.selectedProperty().addListener((v, o, n) -> {
			if (n)
				refresh.run();
		});

		// key for debouncing the (potentially expensive) regeneration of the methods text
		var updateMethodsKey = new Object();

		// write edits back to the input taxa block (so they are saved), mark dirty and refresh the "Input:" section
		textArea.textProperty().addListener((v, o, n) -> {
			if (!updating.get()) {
				var taxaBlock = mainWindow.getWorkflow().getInputTaxaBlock();
				if (taxaBlock != null) {
					var text = (n == null || n.isBlank() ? null : n);
					if (!Objects.equals(text, taxaBlock.getComments())) {
						taxaBlock.setComments(text);
						mainWindow.setDirty(true);
						RunAfterAWhile.applyInFXThread(updateMethodsKey, mainWindow::updateMethodsTab);
					}
				}
			}
		});
	}
}
