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

package splitstree6.xtra.genetreeview.layout;

import javafx.beans.property.*;
import javafx.collections.SetChangeListener;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.SelectionEffectBlue;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;
import splitstree6.layout.tree.ComputeTreeLayout;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.xtra.genetreeview.util.SelectionModelSet;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class TreeSheet extends StackPane implements Selectable {

    private final PhyloTree tree;
    private final int id;
    private final double width;
    private final double height;
    private Rectangle selectionRectangle;
    private Text nameLabel;
    private final Group taxonLabels;
    private final Group edges;
    private final BooleanProperty isSelectedProperty = new SimpleBooleanProperty();
    private final TaxaBlock taxaBlock;
    private final SelectionModelSet<Integer> taxaSelectionModel;
    private final HashMap<Integer,Integer> taxaBlock2phyloTreeIds;
    private final SelectionModelSet<Integer> edgeSelectionModel;
    private final LongProperty lastUpdate = new SimpleLongProperty(this, "lastUpdate", 0L);

    public TreeSheet(PhyloTree tree, int id, double width, double height, TreeDiagramType diagram, TaxaBlock taxaBlock,
                     SelectionModelSet<Integer> taxaSelectionModel, SelectionModelSet<Integer> edgeSelectionModel) {
        this.tree = tree;
        this.id = id;
        this.width = width;
        this.height = height;
        this.taxaBlock = taxaBlock;
        this.taxaSelectionModel = taxaSelectionModel;
        this.edgeSelectionModel = edgeSelectionModel;

        createTreeBackground(tree.getName());

        Function<Integer, StringProperty> taxonLabelMap = (taxonId) ->
                new SimpleStringProperty(tree.getTaxon2Node(taxonId).getLabel());

        var computedTreeLayout = ComputeTreeLayout.apply(tree, tree.getNumberOfTaxa(), taxonLabelMap,
                diagram, HeightAndAngles.Averaging.ChildAverage,width-80,height-20,
                false, new HashMap<>(), new HashMap<>());

        Group layoutedTree = new Group();
        if (computedTreeLayout.labelConnectors() != null)
            layoutedTree.getChildren().add(computedTreeLayout.labelConnectors());
        if (computedTreeLayout.edges() != null)
            layoutedTree.getChildren().add(computedTreeLayout.edges());
        if (computedTreeLayout.nodes() != null)
            layoutedTree.getChildren().add(computedTreeLayout.nodes());
        if (computedTreeLayout.otherLabels() != null)
            layoutedTree.getChildren().add(computedTreeLayout.otherLabels());
        if (computedTreeLayout.taxonLabels() != null)
            layoutedTree.getChildren().add(computedTreeLayout.taxonLabels());
        this.edges = computedTreeLayout.edges();
        this.taxonLabels = computedTreeLayout.taxonLabels();

        // Adjusting label font size
        if (diagram.isRadialOrCircular()) {
            assert taxonLabels != null;
            for (var label : taxonLabels.getChildren()) {
                ((RichTextLabel)label).setScale(0.3);
                ((RichTextLabel)label).ensureUpright();
            }
        } else if (diagram.equals(TreeDiagramType.RectangularCladogram) |
                diagram.equals(TreeDiagramType.RectangularPhylogram)) {
            assert taxonLabels != null;
            for (var label : taxonLabels.getChildren()) {
                ((RichTextLabel)label).setScale(0.4);
            }
            layoutedTree.setTranslateY(5); // space on top needed for name label
        }
        else { // for triangular
            assert taxonLabels != null;
            for (var label : taxonLabels.getChildren()) {
                ((RichTextLabel)label).setScale(0.4);
            }
        }
        assert computedTreeLayout.otherLabels() != null;
        for (var label : computedTreeLayout.otherLabels().getChildren()) {
            ((RichTextLabel)label).ensureUpright();
            ((RichTextLabel)label).setScale(0.2);
        }
        this.getChildren().addAll(layoutedTree);

        // Taxon Selection
        taxaBlock2phyloTreeIds = new HashMap<>();
        for (var taxonId : tree.getTaxonNodeMap().keySet()) {
            String taxonName = tree.getTaxon2Node(taxonId).getLabel();
            taxaBlock2phyloTreeIds.put(taxaBlock.indexOf(taxonName), taxonId);
        }
        for (var taxonLabel : taxonLabels.getChildren()) {
            RichTextLabel taxonRichTextLabel = (RichTextLabel) taxonLabel;
            taxonLabel.setOnMouseEntered(e -> taxonRichTextLabel.setScale(1.1*taxonRichTextLabel.getScale()));
            taxonLabel.setOnMouseExited(e -> taxonRichTextLabel.setScale(1/1.1*taxonRichTextLabel.getScale()));
            int taxonId = taxaBlock.indexOf(taxonRichTextLabel.getText());
            if (taxaSelectionModel.getSelectedItems().contains(taxonId))
                selectTaxon(taxonRichTextLabel.getText(),true);
            taxonLabel.setOnMouseClicked(e -> {
                boolean selectedBefore = taxaSelectionModel.getSelectedItems().contains(taxonId);
                if (!e.isShiftDown()) {
                    taxaSelectionModel.clearSelection();
                    if (!selectedBefore) {
                        taxaSelectionModel.select(taxonId);
                    }
                } else {
                    taxaSelectionModel.setSelected(taxonId, !selectedBefore);
                }
            });
        }

        // Edge Selection
        edgeSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Integer>) c -> {
            if (c.wasAdded()) {
                int edgeId = c.getElementAdded();
                selectEdge(edgeId, true);
            } else if (c.wasRemoved()) {
                int edgeId = c.getElementRemoved();
                selectEdge(edgeId, false);
            }
        });
        for (var edge : edges.getChildren()) {
            var labeledEdgeShape = (LabeledEdgeShape) edge;
            for (var node : labeledEdgeShape.all()) {
                if (node instanceof Shape shape) {
                    shape.setOnMouseEntered(e -> shape.setStrokeWidth(shape.getStrokeWidth() + 3));
                    shape.setOnMouseExited(e -> shape.setStrokeWidth(shape.getStrokeWidth() - 3));
                }
                else if (node instanceof RichTextLabel label) {
                    label.setOnMouseEntered(e -> label.setScale(1.1*label.getScale()));
                    label.setOnMouseExited(e -> label.setScale(1/1.1*label.getScale()));
                }
            }

            int edgeId = edges.getChildren().indexOf(edge)+1;
            if (edgeSelectionModel.getSelectedItems().contains(edgeId)) selectEdge(edge,true);
            edge.setOnMousePressed(e -> {
                if (e.getButton().equals(MouseButton.PRIMARY)) {
                    if (e.getClickCount() == 1) {
                        boolean selectedBefore = edgeSelectionModel.getSelectedItems().contains(edgeId);
                        if (!e.isShiftDown()) {
                            taxaSelectionModel.clearSelection();
                            edgeSelectionModel.clearSelection();
                        }
                        edgeSelectionModel.setSelected(edgeId, !selectedBefore);
                    }
                    else if (e.getClickCount() == 2) { // if-block above has been executed after first click
                        boolean selectedBefore = !edgeSelectionModel.getSelectedItems().contains(edgeId);
                        selectTaxaBelow(edgeId, !selectedBefore);
                    }
                    lastUpdate.set(System.currentTimeMillis());
                }
            });
        }
        updateEdgeSelection();

        // Tree Selection
        isSelectedProperty.addListener((observableValue, wasSelected, isSelected) -> {
            if (isSelected) {
                this.setStyle("-fx-border-color: -fx-accent");
            }
            else {
                this.setStyle("-fx-border-color: -fx-box-border");
            }
            lastUpdate.set(System.currentTimeMillis());
        });
    }

    private void createTreeBackground(String treeName) {
        this.setPrefSize(width,height);
        this.setBackground(new Background(new BackgroundFill(Color.web("white",1),null,null)));
        this.setStyle("-fx-border-color: -fx-box-border; -fx-display:inline;");
        var nameLabelContainer = new Pane();
        nameLabel = new Text(treeName);
        nameLabel.setFont(new Font(9));
        nameLabel.setLayoutX(2);
        nameLabel.setLayoutY(9);
        nameLabelContainer.getChildren().add(nameLabel);
        this.getChildren().add(nameLabelContainer);

        selectionRectangle = new Rectangle(width, height, Color.TRANSPARENT);
        this.getChildren().add(selectionRectangle);
    }

    public boolean selectTaxon(String taxonName, boolean select) {
        for (var label : taxonLabels.getChildren()) {
            if (((RichTextLabel)label).getText().equals(taxonName)) {
                // Selection indication like in splitstree
                if (select) label.setEffect(SelectionEffectBlue.getInstance());
                else label.setEffect(null);
                // Alternative: Selection indication with CSS style (no suitable fx color found yet)
                //if (select) label.setStyle("-fx-effect: dropshadow(one-pass-box,-fx-focus-color,5,1,0,0)");
                //else label.setStyle("-fx-effect: dropshadow(one-pass-box,transparent,5,1,0,0)");
                lastUpdate.set(System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    private void selectEdge(int edgeId, boolean select) {
        var edge = edges.getChildren().get(edgeId-1);
        if (edge != null) {
            selectEdge(edge,select);
        }
    }

    private void selectEdge(Node edge, boolean select) {
        if (select) edge.setEffect(SelectionEffectBlue.getInstance());
        else edge.setEffect(null);
    }

    private void selectTaxaBelow(int edgeId, boolean select) {
        tree.postorderTraversal(tree.findEdgeById(edgeId).getTarget(), n -> n.outEdges().forEach(e -> {
            if (e.getTarget().isLeaf()) {
                taxaSelectionModel.setSelected(taxaBlock.indexOf(e.getTarget().getLabel()), select);
            }
        }));
    }

    public void updateEdgeSelection() {
        edgeSelectionModel.clearSelection();
        LinkedList<jloda.graph.Node> nodes = new LinkedList<>();
        for (var taxonId : taxaSelectionModel.getSelectedItems()) {
            if (taxaBlock2phyloTreeIds.containsKey(taxonId)) {
                var taxonNode = tree.getTaxon2Node(taxaBlock2phyloTreeIds.get(taxonId));
                if (taxonNode != null) {
                    edgeSelectionModel.setSelected(taxonNode.getFirstInEdge().getId(), true);
                    nodes.add(taxonNode);
                }
            }
        }
        int counter = 0;
        int max = (int) (nodes.size()*((nodes.size()-1)/2.));
        while (nodes.size() > 1 & counter < max) {
            var node = nodes.removeFirst();
            var edgeIn = node.getFirstInEdge();
            var sourceNode = edgeIn.getSource();
            LinkedList<jloda.graph.Node> sisters = new LinkedList<>();
            for (var outEdge : sourceNode.outEdges()) {
                var sister = outEdge.getTarget();
                if (sister == node) continue;
                if (nodes.contains(sister)) {
                    sisters.add(sister);
                }
                else {
                    nodes.addLast(node);
                    break;
                }
            }
            if (sisters.size() == sourceNode.getOutDegree()-1) {
                for (var sisterNode : sisters) {
                    edgeSelectionModel.setSelected(sisterNode.getFirstInEdge().getId(), true);
                    nodes.remove(sisterNode);
                }
                nodes.addLast(sourceNode);
                if (sourceNode.getFirstInEdge() != null)
                    edgeSelectionModel.setSelected(sourceNode.getFirstInEdge().getId(), true);
            }
            counter++;
        }
        lastUpdate.set(System.currentTimeMillis());
    }

    private boolean monophyletic(List<jloda.graph.Node> taxa) {
        LinkedList<jloda.graph.Node> nodes = new LinkedList<>(taxa);
        int counter = 0;
        int max = (int) (nodes.size()*((nodes.size()-1)/2.));
        while (nodes.size() > 1 & counter < max) {
            var node = nodes.removeFirst();
            var edgeIn = node.getFirstInEdge();
            var sourceNode = edgeIn.getSource();
            LinkedList<jloda.graph.Node> sisters = new LinkedList<>();
            for (var outEdge : sourceNode.outEdges()) {
                var sister = outEdge.getTarget();
                if (sister == node) continue;
                if (nodes.contains(sister)) {
                    sisters.add(sister);
                }
                else {
                    nodes.addLast(node);
                    break;
                }
            }
            if (sisters.size() == sourceNode.getOutDegree()-1) {
                for (var sisterNode : sisters) nodes.remove(sisterNode);
                nodes.addLast(sourceNode);
            }
            counter++;
        }
        return nodes.size() == 1;
    }

    public boolean monophyleticSelection() {
        LinkedList<jloda.graph.Node> selectedLeafNodes = new LinkedList<>();
        for (int taxonId : taxaSelectionModel.getSelectedItems()) {
            if (taxaBlock2phyloTreeIds.containsKey(taxonId) && tree.getTaxon2Node(taxaBlock2phyloTreeIds.get(taxonId)) != null) {
                selectedLeafNodes.add(tree.getTaxon2Node(taxaBlock2phyloTreeIds.get(taxonId)));
            }
        }
        return monophyletic(selectedLeafNodes);
    }

    public int getTreeId() {
        return id;
    }

    public Rectangle getSelectionRectangle() {
        return selectionRectangle;
    };

    public void setTreeName(String treeName) {
        nameLabel.setText(treeName);
        lastUpdate.set(System.currentTimeMillis());
    }

    public String getTreeName() {
        return nameLabel.getText();
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

    public ReadOnlyLongProperty lastUpdateProperty() {
        return lastUpdate;
    }
}
