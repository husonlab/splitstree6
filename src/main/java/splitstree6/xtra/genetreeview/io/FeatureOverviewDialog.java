/*
 *  FeatureOverviewDialog.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import splitstree6.xtra.genetreeview.model.GeneTreeSet;

import java.util.HashMap;

public class FeatureOverviewDialog extends Stage {

    // TODO: make editable

    public FeatureOverviewDialog(Stage parentStage, GeneTreeSet geneTreeSet) {
        this.initStyle(parentStage.getStyle());
        this.setTitle("FeatureOverview");
        this.initModality(Modality.APPLICATION_MODAL);
        this.initOwner(parentStage);

        // Creating a tableView of Features
        TableView<Feature> tableView = new TableView<>();
        ObservableList<Feature> features = FXCollections.observableArrayList();
        for (var featureName : geneTreeSet.getAvailableFeatures()) {
            HashMap<Integer,String> treeId2value = geneTreeSet.getFeatureValues(featureName);
            HashMap<String,String> treeName2value = new HashMap<>();
            for (int treeId : treeId2value.keySet()) {
                treeName2value.put(geneTreeSet.getGeneTree(treeId).getGeneName(), treeId2value.get(treeId));
            }
            Feature feature = new Feature(featureName, treeName2value);
            features.add(feature);
        }
        tableView.setItems(features);

        // First column: feature names
        TableColumn<Feature,String> firstNameCol = new TableColumn<>("Feature\\Tree");
        firstNameCol.setCellValueFactory(new PropertyValueFactory<>("featureName"));
        tableView.getColumns().add(firstNameCol);

        // Further columns: one column per tree
        for (var geneTree : geneTreeSet.getGeneTrees()) {
            String treeName = geneTree.getGeneName();
            TableColumn<Feature,String> treeColumn = new TableColumn<>(treeName);
            treeColumn.setCellValueFactory(feature -> new SimpleStringProperty(feature.getValue().getTreeName2value().get(treeName)));
            tableView.getColumns().add(treeColumn);
        }

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(tableView);
        Scene scene = new Scene(borderPane, 500, 300);
        this.setScene(scene);
        this.show();
    }
}