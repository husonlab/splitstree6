/*
 *  AlignmentViewController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.alignment;

import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import jloda.fx.control.CopyableLabel;
import splitstree6.data.parts.Taxon;

public class AlignmentViewController {
    @FXML
    private MenuButton filterMenu;

    @FXML
    private BorderPane borderPane;

    @FXML
    private Canvas canvas;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private MenuButton selectSitesMenu;

    @FXML
    private ComboBox<ColorScheme> colorSchemeCBox;

    @FXML
    private MenuItem selectCodon0MenuItem;

    @FXML
    private MenuItem selectCodon1MenuItem;

    @FXML
    private MenuItem selectCodon2MenuItem;

    @FXML
    private MenuItem selectSynapomorphiesMenuItem;

    @FXML
    private MenuItem selectConstantMenuItem;

    @FXML
    private MenuItem selectGapMenuItem;

    @FXML
    private MenuItem selectMissingMenuItem;

    @FXML
    private MenuItem selectAllNonInformativeMenuItem;

    @FXML
    private MenuItem selectAllMenuItem;

    @FXML
    private MenuItem selectNoneMenuItem;


    @FXML
    private ToggleButton findToggleButton;


    @FXML
    private ScrollBar hScrollBar;

    @FXML
    private AnchorPane innerAnchorPane;

    @FXML
    private Pane leftBottomPane;

    @FXML
    private AnchorPane outerAnchorPane;

    @FXML
    private StackPane rightTopStackPane;

    @FXML
    private AnchorPane root;

    @FXML
    private SplitPane splitPane;

    @FXML
    private ListView<Taxon> taxaListView;

    @FXML
    private ToolBar toolBar;

    @FXML
    private VBox vBox;

    @FXML
    private Button expandVerticallyButton;

    @FXML
    private Button contractVerticallyButton;

    @FXML
    private Button expandHorizontallyButton;

    @FXML
    private Button contractHorizontallyButton;

    @FXML
    private Button zoomToFitButton;

    @FXML
    private StackPane stackPane;

    @FXML
    private Pane selectionPane;

    @FXML
    private ScrollBar vScrollBar;


    @FXML
    private Group siteSelectionGroup;

    @FXML
    private MenuItem disableSelectedSitesMenuItem;

    @FXML
    private MenuItem enableSelectedSitesOnlyMenuItem;

    @FXML
    private MenuItem enableSelectedSitesMenuItem;

    @FXML
    private MenuItem disableSelectedTaxaMenuItem;

    @FXML
    private MenuItem enableAllSitesMenuItem;

    @FXML
    private MenuItem enableAllTaxaMenuItem;

    @FXML
    private MenuItem enableSelectedTaxaOnlyMenuItem;

    @FXML
    private MenuItem enableSelectedTaxaMenuItem;

    @FXML
    private Label selectionLabel;

    @FXML
    private Group taxaSelectionGroup;

    private final NumberAxis axis = new NumberAxis();

    private final Pane rightTopPane = new Pane();

    private final CopyableLabel copyableSelectionLabel = new CopyableLabel("Selection");

    @FXML
    private void initialize() {
        rightTopStackPane.getChildren().add(axis);
        axis.prefWidthProperty().bind(rightTopStackPane.widthProperty());
        axis.setLowerBound(1);
        axis.setUpperBound(1);
        axis.setAutoRanging(false);
        axis.setTickUnit(100);

        rightTopPane.setMouseTransparent(true);
        rightTopPane.prefWidthProperty().bind(rightTopStackPane.widthProperty());
        rightTopPane.prefHeightProperty().bind(rightTopStackPane.heightProperty());
        rightTopStackPane.getChildren().add(rightTopPane);

        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        var pos = toolBar.getItems().indexOf(selectionLabel);
        toolBar.getItems().add(pos, copyableSelectionLabel);
        toolBar.getItems().remove(selectionLabel);

        hScrollBar.widthProperty().addListener((v, o, n) -> canvas.setWidth(n.doubleValue() - 16));
        vScrollBar.heightProperty().addListener((v, o, n) -> canvas.setHeight(n.doubleValue()));

        splitPane.widthProperty().addListener((v, o, n) -> {
            if (n.doubleValue() > 0 && o.doubleValue() > 0) {
                splitPane.setDividerPositions(splitPane.getDividerPositions()[0] / n.doubleValue() * o.doubleValue());
            }
        });

        filterMenu.disableProperty().bind(enableAllTaxaMenuItem.disableProperty().and(enableSelectedTaxaOnlyMenuItem.disableProperty())
                .and(enableSelectedTaxaMenuItem.disableProperty())
                .and(disableSelectedTaxaMenuItem.disableProperty()).and(enableAllSitesMenuItem.disableProperty())
                .and(enableSelectedSitesMenuItem.disableProperty())
                .and(enableSelectedSitesOnlyMenuItem.disableProperty()).and(disableSelectedSitesMenuItem.disableProperty()));

        // never want gray in the list of selected taxa, because we are using gray to indicate inactive items
        taxaListView.setStyle("-fx-selection-bar-non-focused: -fx-focus-color;");
    }

    public BorderPane getBorderPane() {
        return borderPane;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public Pane getSelectionPane() {
        return selectionPane;
    }

    public ScrollPane getScrollPane() {
        return scrollPane;
    }

    public MenuButton getChooseColumnsMenu() {
        return selectSitesMenu;
    }

    public ComboBox<ColorScheme> getColorSchemeCBox() {
        return colorSchemeCBox;
    }

    public MenuButton getSelectSitesMenu() {
        return selectSitesMenu;
    }

    public MenuItem getSelectCodon0MenuItem() {
        return selectCodon0MenuItem;
    }

    public MenuItem getSelectCodon1MenuItem() {
        return selectCodon1MenuItem;
    }

    public MenuItem getSelectCodon2MenuItem() {
        return selectCodon2MenuItem;
    }

    public MenuItem getSelectSynapomorphiesMenuItem() {
        return selectSynapomorphiesMenuItem;
    }

    public MenuItem getSelectConstantMenuItem() {
        return selectConstantMenuItem;
    }

    public MenuItem getSelectGapMenuItem() {
        return selectGapMenuItem;
    }

    public MenuItem getSelectMissingMenuItem() {
        return selectMissingMenuItem;
    }

    public MenuItem getSelectAllNonInformativeMenuItem() {
        return selectAllNonInformativeMenuItem;
    }

    public MenuItem getSelectAllMenuItem() {
        return selectAllMenuItem;
    }

    public MenuItem getSelectNoneMenuItem() {
        return selectNoneMenuItem;
    }

    public ToggleButton getFindToggleButton() {
        return findToggleButton;
    }


    public ScrollBar gethScrollBar() {
        return hScrollBar;
    }

    public AnchorPane getInnerAnchorPane() {
        return innerAnchorPane;
    }

    public Pane getLeftBottomPane() {
        return leftBottomPane;
    }

    public AnchorPane getOuterAnchorPane() {
        return outerAnchorPane;
    }

    public StackPane getRightTopStackPane() {
        return rightTopStackPane;
    }

    public AnchorPane getRoot() {
        return root;
    }

    public SplitPane getSplitPane() {
        return splitPane;
    }

    public ListView<Taxon> getTaxaListView() {
        return taxaListView;
    }

    public ToolBar getToolBar() {
        return toolBar;
    }

    public VBox getvBox() {
        return vBox;
    }

    public ScrollBar getvScrollBar() {
        return vScrollBar;
    }

    public NumberAxis getAxis() {
        return axis;
    }

    public Button getExpandVerticallyButton() {
        return expandVerticallyButton;
    }

    public Button getContractVerticallyButton() {
        return contractVerticallyButton;
    }

    public Button getExpandHorizontallyButton() {
        return expandHorizontallyButton;
    }

    public Button getContractHorizontallyButton() {
        return contractHorizontallyButton;
    }

    public Button getZoomToFitButton() {
        return zoomToFitButton;
    }

    public Group getSiteSelectionGroup() {
        return siteSelectionGroup;
    }

    public Group getTaxaSelectionGroup() {
        return taxaSelectionGroup;
    }

    public Pane getRightTopPane() {
        return rightTopPane;
    }

    public MenuItem getDisableSelectedSitesMenuItem() {
        return disableSelectedSitesMenuItem;
    }

    public MenuItem getEnableSelectedSitesOnlyMenuItem() {
        return enableSelectedSitesOnlyMenuItem;
    }

    public MenuItem getDisableSelectedTaxaMenuItem() {
        return disableSelectedTaxaMenuItem;
    }

    public MenuItem getEnableAllSitesMenuItem() {
        return enableAllSitesMenuItem;
    }

    public MenuItem getEnableAllTaxaMenuItem() {
        return enableAllTaxaMenuItem;
    }

    public MenuItem getEnableSelectedTaxaOnlyMenuItem() {
        return enableSelectedTaxaOnlyMenuItem;
    }

    public MenuItem getEnableSelectedSitesMenuItem() {
        return enableSelectedSitesMenuItem;
    }

    public MenuItem getEnableSelectedTaxaMenuItem() {
        return enableSelectedTaxaMenuItem;
    }

    public CopyableLabel getSelectionLabel() {
        return copyableSelectionLabel;
    }
}
