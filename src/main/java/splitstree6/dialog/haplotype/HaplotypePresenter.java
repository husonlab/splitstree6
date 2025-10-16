/*
 * HaplotypePresenter.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.dialog.haplotype;

import javafx.beans.binding.Bindings;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.window.NotificationManager;

import java.io.File;

public class HaplotypePresenter {

	private final HaplotypeController controller;
	private final Stage stage;

	public HaplotypePresenter(HaplotypeController controller, Stage stage) {
		this.controller = controller;
		this.stage = stage;
		wire();
	}

	private void wire() {
		// Apply enabled only if the Sequences text field is non-empty
		controller.getApplyBtn().disableProperty().bind(
				Bindings.createBooleanBinding(
						() -> controller.getSequencesField().getText() == null || controller.getSequencesField().getText().isBlank(),
						controller.getSequencesField().textProperty()
				)
		);

		controller.getBrowseSequencesBtn().setOnAction(e -> {
			File f = chooseFile("Select Sequences File");
			if (f != null) controller.getSequencesField().setText(f.getAbsolutePath());
		});

		controller.getBrowseTraitsBtn().setOnAction(e -> {
			File f = chooseFile("Select Traits File");
			if (f != null) controller.getTraitsField().setText(f.getAbsolutePath());
		});

		controller.getCancelBtn().setOnAction(e -> {
			stage.close();
		});

		controller.getApplyBtn().setOnAction(e -> {
			var result = new Result(
					safe(controller.getSequencesField().getText()),
					safe(controller.getTraitsField().getText()),
					controller.getDistanceChoice().getSelectionModel().getSelectedItem(),
					controller.getMethodChoice().getSelectionModel().getSelectedItem()
			);
			try {
				ImportHaplotypeApply.apply(result);
			} catch (Exception ex) {
				NotificationManager.showError("Failed: " + ex.getMessage());
			}
			stage.close();
		});

		controller.getDistanceChoice().disableProperty().bind(controller.getMethodChoice().valueProperty().isEqualTo("MedianJoining"));
	}

	private File chooseFile(String title) {
		FileChooser fc = new FileChooser();
		fc.setTitle(title);
		// Optionally set filters:
		// fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
		return fc.showOpenDialog(stage);
	}

	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}

	/**
	 * Immutable result returned when user presses Apply.
	 */
	public record Result(String sequencesPath, String traitsPath, String distanceModel, String method) {
	}
}