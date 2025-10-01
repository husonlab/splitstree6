/*
 * HaplotypeDialog.java Copyright (C) 2025 Daniel H. Huson
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

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import jloda.util.Basic;

import java.io.IOException;

public class HaplotypeDialog {

	/**
	 * Shows the "Open Haplotype Data" dialog as a modal window.
	 *
	 * @param owner owner window (nullable)
	 * @throws IOException if FXML cannot be loaded
	 */
	public static void show(Window owner) {
		try {
			FXMLLoader loader = new FXMLLoader(HaplotypeDialog.class.getResource("haplotype.fxml"));
			Parent root = loader.load();
			HaplotypeController controller = loader.getController();

			Stage stage = new Stage();
			stage.setTitle("Open Haplotype Data");
			stage.initModality(Modality.APPLICATION_MODAL);
			if (owner != null) stage.initOwner(owner);
			stage.setScene(new Scene(root));

			var presenter = new HaplotypePresenter(controller, stage);
			stage.showAndWait();
		} catch (IOException ex) {
			Basic.caught(ex);
		}
	}
}