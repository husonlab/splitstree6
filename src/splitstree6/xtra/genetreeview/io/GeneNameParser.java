/*
 *  GeneNameParser.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import splitstree6.xtra.genetreeview.Model;

public class GeneNameParser extends Stage {

    private final BooleanProperty parsedProperty = new SimpleBooleanProperty(false);

    public GeneNameParser(Stage parentStage, Model model) {
        this.initStyle(parentStage.getStyle());
        this.setTitle("GeneNameParser");
        this.initModality(Modality.APPLICATION_MODAL);
        this.initOwner(parentStage);

        TextArea textInputArea = new TextArea();
        Label infoLabel = new Label("");
        Button parseButton = new Button("Import");
        parseButton.setOnAction(e -> {
            String textInput = textInputArea.getText();
            String[] geneNames = extractGeneNames(textInput, model.getTreesBlock().getNTrees());
            if (geneNames != null) {
                model.setGeneNames(geneNames);
                parsedProperty.set(true);
                this.close();
            }
            else {
                infoLabel.setText("import failed");
            }
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> this.close());

        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10));
        HBox hBox = new HBox();
        hBox.getChildren().addAll(parseButton, cancelButton, infoLabel);
        hBox.setSpacing(5);
        vBox.getChildren().addAll(new Label("Enter gene names in tree input order:"), textInputArea, hBox);

        Scene scene = new Scene(vBox, 300, 200);
        this.setScene(scene);
        this.show();
    }

    private String[] extractGeneNames(String textInput, int expectedLength) {
        String[] geneNames;
        if (textInput.split("\t").length == expectedLength) geneNames = textInput.split("\t");
        else if (textInput.split(",").length == expectedLength) geneNames = textInput.split(",");
        else if (textInput.split(";").length == expectedLength) geneNames = textInput.split(";");
        else if (textInput.split("\n").length == expectedLength) geneNames = textInput.split("\n");
        else if (textInput.split(" ").length == expectedLength) geneNames = textInput.split(" ");
        else return null;
        for (int i = 0; i < expectedLength; i++) {
            geneNames[i] = geneNames[i].trim();
        }
        return geneNames;
    }

    public BooleanProperty parsedProperty() {
        return parsedProperty;
    }
}
