/*
 *  GeneOrderDialog.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GeneOrderDialog extends Stage {

    private final BooleanProperty doneProperty = new SimpleBooleanProperty(false);
    private String finalTaxonName;

    public GeneOrderDialog(Stage parentStage, String taxonName) {
        this.initStyle(parentStage.getStyle());
        this.setTitle("GeneOrderRequest");
        this.initModality(Modality.APPLICATION_MODAL);
        this.initOwner(parentStage);

        Label introLabel = new Label("If entries are available in NCBI for all genes for the selected taxon, the " +
                "genes' starting positions in the genome can be downloaded. This might take some time. \nThe gene trees " +
                "will be ordered as genes in the genome of:");
        introLabel.setWrapText(true);
        TextField taxonNameTextField = new TextField(taxonName);
        Label infoLabel = new Label("");
        Button startButton = new Button("Get gene order from NCBI");
        startButton.setOnAction(e -> {
            String finalTaxonName = taxonNameTextField.getText();
            if (finalTaxonName != null) {
                this.finalTaxonName = finalTaxonName;
                doneProperty.set(true);
                this.close();
            }
            else {
                infoLabel.setText("Please provide a taxon name");
            }
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> {
            finalTaxonName = null;
            doneProperty.set(true);
            this.close();
        });

        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10));
        HBox hBox = new HBox();
        hBox.getChildren().addAll(startButton, cancelButton);
        hBox.setSpacing(5);
        vBox.getChildren().addAll(introLabel, taxonNameTextField, hBox, infoLabel);

        Scene scene = new Scene(vBox, 370, 190);
        this.setScene(scene);
        this.show();
    }

    public BooleanProperty doneProperty() {
        return doneProperty;
    }

    public String getFinalTaxonName() {
        return finalTaxonName;
    }
}
