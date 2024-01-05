/*
 *  GeneOrderDialog.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.io;

import javafx.stage.Stage;

public class GeneOrderDialog extends SelectionDialog {

	public GeneOrderDialog(Stage parentStage, String taxonName) {
		super(parentStage, taxonName, "taxon name");
		this.setTitle("GeneOrderRequest");
		introLabel.setText("If entries are available in NCBI for all genes for the selected taxon, the " +
						   "genes' starting positions in the genome can be downloaded. This might take some time. \nThe gene trees " +
						   "will be ordered as genes in the genome of:");
		startButton.setText("Get gene order from NCBI");
	}
}
