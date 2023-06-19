/*
 *  TreeSheet.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.BasicFX;
import jloda.phylo.PhyloTree;
import splitstree6.layout.tree.ComputeTreeLayout;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.TreeDiagramType;

import java.util.HashMap;
import java.util.TreeSet;
import java.util.function.Function;

public class TreeSheet extends Group implements Selectable{

    private final double width;
    private final double height;
    private Rectangle backgroundRectangle;
    private Text nameLabel;
    private final BooleanProperty isSelectedProperty = new SimpleBooleanProperty();
    private final BooleanProperty mediatorProperty = new SimpleBooleanProperty();

    public TreeSheet(PhyloTree tree, double width, double height, TreeDiagramType diagram) {
        this.width = width;
        this.height = height;

        Function<Integer, StringProperty> taxonLabelMap = (taxonId) ->
                new SimpleStringProperty(tree.getTaxon2Node(taxonId).getLabel());

        super.getChildren().add(createTreeBackground(tree.getName()));

        Group layoutedTree = ComputeTreeLayout.apply(tree, tree.getNumberOfTaxa(), taxonLabelMap,
                diagram, HeightAndAngles.Averaging.ChildAverage,width-80,height-20,
                false, new HashMap<>(), new HashMap<>()).getAllAsGroup();


        // For debugging only
        var testTree =  ComputeTreeLayout.apply(tree, tree.getNumberOfTaxa(), taxonLabelMap,
                diagram, HeightAndAngles.Averaging.ChildAverage,width-80,height-20,
                false, new HashMap<>(), new HashMap<>());
        var set = new TreeSet<Double>();
        for (var node : testTree.nodes().getChildren()) {
            set.add(node.getTranslateY());
        }
        //System.out.println(set.first());


        // Adjusting tree position on the background and label font size
        if (diagram.isRadialOrCircular()) {
            layoutedTree.setTranslateX(width / 2);
            layoutedTree.setTranslateY(height / 2);
            for (var label : BasicFX.getAllRecursively(layoutedTree, RichTextLabel.class)) {
                label.setScale(0.3);
            }
        }
        else if (diagram.isPhylogram()) { // for rectangular phylogram
            layoutedTree.setTranslateX(5);
            int n = tree.getNumberOfTaxa();
            if (n == 2) layoutedTree.setTranslateY(-216+15);
            else if (n == 3) layoutedTree.setTranslateY(-108+15);
            else layoutedTree.setTranslateY(-352.39*Math.pow(tree.getNumberOfTaxa(),-1.137)+15);
            for (var label : BasicFX.getAllRecursively(layoutedTree, RichTextLabel.class)) {
                label.setScale(0.4);
            }
        }
        else { // for rectangular and triangular cladogram
            layoutedTree.setTranslateX(width-100);
            int n = tree.getNumberOfTaxa();
            if (n == 2) layoutedTree.setTranslateY(-216+15);
            else if (n == 3) layoutedTree.setTranslateY(-108+15);
            else layoutedTree.setTranslateY(-352.39*Math.pow(tree.getNumberOfTaxa(),-1.137)+15);
            for (var label : BasicFX.getAllRecursively(layoutedTree, RichTextLabel.class)) {
                label.setScale(0.4);
            }
        }
        super.getChildren().addAll(layoutedTree);

        isSelectedProperty.addListener((observableValue, wasSelected, isSelected) -> {
            if (isSelected) {
                backgroundRectangle.setStrokeWidth(1);
            }
            else {
                backgroundRectangle.setStrokeWidth(0);
            }
            if (isSelected != mediatorProperty.get()) mediatorProperty.set(isSelected);
        });
    }

    private Group createTreeBackground(String treeName) {
        var treeBackground = new Group();
        backgroundRectangle = new Rectangle(width, height, Color.WHITE);
        //backgroundRectangle.setOpacity(0.5); // not working in 3D
        backgroundRectangle.setStroke(Color.BLACK);
        backgroundRectangle.setStrokeWidth(0);
        backgroundRectangle.setTranslateZ(2); // to avoid conflicts with the on top drawn tree later
        backgroundRectangle.setOnMouseClicked(e -> {
            setSelectedProperty();
        });
        nameLabel = new Text(treeName);
        nameLabel.setFont(new Font(9));
        nameLabel.setX(2);
        nameLabel.setY(9);
        treeBackground.getChildren().addAll(backgroundRectangle, nameLabel);
        return treeBackground;
    }

    public void setTreeName(String treeName) {
        nameLabel.setText(treeName);
    }

    public void setSelectedProperty(boolean selected) {
        isSelectedProperty.set(selected);
    };

    public void setSelectedProperty() {
        setSelectedProperty(!isSelectedProperty.get());
    }

    public BooleanProperty isSelectedProperty() {
        return isSelectedProperty;
    }

    public BooleanProperty mediatorProperty() {
        return mediatorProperty;
    }
}
