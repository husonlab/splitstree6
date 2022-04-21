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
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;
import splitstree6.data.parts.Taxon;

public class AlignmentViewController {

    @FXML
    private BorderPane borderPane;

    @FXML
    private Canvas canvas;

    @FXML
    private ScrollPane centerPane;

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
    private MenuItem selectConstantMenuItem;

    @FXML
    private MenuItem selectGapMenuItem;

    @FXML
    private MenuItem selectAllNonInformativeMenuItem;

    @FXML
    private MenuItem selectAllMenuItem;

    @FXML
    private MenuItem selectNoneMenuItem;


    @FXML
    private ToggleButton findToggleButton;

    @FXML
    private ToggleButton formatToggleButton;

    @FXML
    private VBox formatVBox;

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
    private ToggleButton settingsToggleButton;

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
    private ScrollBar vScrollBar;

    private final NumberAxis axis = new NumberAxis();

    @FXML
    private void initialize() {
        rightTopStackPane.getChildren().add(axis);
        axis.prefWidthProperty().bind(rightTopStackPane.widthProperty());
        axis.setLowerBound(1);
        axis.setUpperBound(1);
        axis.setAutoRanging(false);
        axis.setTickUnit(100);

        centerPane.setFitToWidth(true);
        centerPane.setFitToHeight(true);

        hScrollBar.widthProperty().addListener((v, o, n) -> canvas.setWidth(n.doubleValue() - 16));
        vScrollBar.heightProperty().addListener((v, o, n) -> canvas.setHeight(n.doubleValue()));

        outerAnchorPane.getChildren().remove(formatVBox);
        outerAnchorPane.getChildren().add(formatVBox);

        formatToggleButton.setSelected(false);
        formatVBox.visibleProperty().bind(formatToggleButton.selectedProperty());
        formatVBox.visibleProperty().addListener((v, o, n) -> {
            for (var titledPane : BasicFX.getAllRecursively(formatVBox, TitledPane.class)) {
                if (!titledPane.isDisable())
                    titledPane.setExpanded(n);
            }
        });

        splitPane.widthProperty().addListener((v, o, n) -> {
            if (n.doubleValue() > 0 && o.doubleValue() > 0) {
                splitPane.setDividerPositions(splitPane.getDividerPositions()[0] / n.doubleValue() * o.doubleValue());
            }
        });

        DraggableLabel.makeDraggable(formatVBox);
    }

    public BorderPane getBorderPane() {
        return borderPane;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public ScrollPane getCenterPane() {
        return centerPane;
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

    public MenuItem getSelectConstantMenuItem() {
        return selectConstantMenuItem;
    }

    public MenuItem getSelectGapMenuItem() {
        return selectGapMenuItem;
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

    public ToggleButton getFormatToggleButton() {
        return formatToggleButton;
    }

    public VBox getFormatVBox() {
        return formatVBox;
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

    public ToggleButton getSettingsToggleButton() {
        return settingsToggleButton;
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
}
