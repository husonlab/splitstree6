/*
 *  AlignmentViewController.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import jloda.fx.control.CopyableLabel;
import jloda.fx.icons.MaterialIcons;
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
	private MenuButton colorSchemeMenuButton;

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
	private MenuItem selectMajorityGapOrMissingMenuItem;

	@FXML
	private MenuItem selectAllNonInformativeMenuItem;

	@FXML
	private MenuItem selectAllMenuItem;

	@FXML
	private MenuItem selectNoneMenuItem;

	@FXML
	private MenuItem invertSelectionMenuItem;

	@FXML
	private MenuItem selectCompatibleMenuItem;

	@FXML
	private MenuItem selectIncompatibleMenuItem;

	@FXML
	public Menu setsMenu;

	@FXML
	private ScrollBar horizontalScrollBar;

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
	private ScrollBar verticalScrollBar;


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

	@FXML
	private Group canvasGroup;

	@FXML
	private Group imageGroup;

	private final NumberAxis axis = new NumberAxis();

	private final Pane rightTopPane = new Pane();

	private final CopyableLabel copyableSelectionLabel = new CopyableLabel("Selection");

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(colorSchemeMenuButton, "palette");

		MaterialIcons.setIcon(selectSitesMenu, "checklist_rtl");
		MaterialIcons.setIcon(filterMenu, "filter_alt");

		MaterialIcons.setIcon(zoomToFitButton, "crop_free");
		MaterialIcons.setIcon(expandHorizontallyButton, "unfold_more", "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(contractHorizontallyButton, "unfold_less", "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(expandVerticallyButton, "unfold_more");
		MaterialIcons.setIcon(contractVerticallyButton, "unfold_less");

		rightTopStackPane.getChildren().add(axis);
		axis.prefWidthProperty().bind(rightTopStackPane.widthProperty());
		axis.setLowerBound(1);
		axis.setUpperBound(1);
		axis.setAutoRanging(false);
		axis.setTickUnit(100);
		axis.setTickLabelFormatter(new StringConverter<>() {
			@Override
			public String toString(Number number) {
				return "%,d".formatted(number.intValue());
			}

			@Override
			public Number fromString(String string) {
				return Double.valueOf(string);
			}
		});

		rightTopPane.setMouseTransparent(true);
		rightTopPane.prefWidthProperty().bind(rightTopStackPane.widthProperty());
		rightTopPane.prefHeightProperty().bind(rightTopStackPane.heightProperty());
		rightTopStackPane.getChildren().add(rightTopPane);

		scrollPane.getContent().setOnScroll(Event::consume);
		scrollPane.setPannable(false);

		var pos = toolBar.getItems().indexOf(selectionLabel);
		toolBar.getItems().add(pos, copyableSelectionLabel);
		toolBar.getItems().remove(selectionLabel);

		splitPane.widthProperty().addListener((v, o, n) -> {
			if (n.doubleValue() > 0 && o.doubleValue() > 0) {
				splitPane.setDividerPositions(splitPane.getDividerPositions()[0] / n.doubleValue() * o.doubleValue());
			}
		});

		splitPane.setDividerPositions(0.15);

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

	public MenuButton getColorSchemeMenuButton() {
		return colorSchemeMenuButton;
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

	public Menu getSetsMenu() {
		return setsMenu;
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

	public MenuItem getSelectMajorityGapOrMissingMenuItem() {
		return selectMajorityGapOrMissingMenuItem;
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

	public MenuItem getInvertSelectionMenuItem() {
		return invertSelectionMenuItem;
	}

	public MenuItem getSelectCompatibleMenuItem() {
		return selectCompatibleMenuItem;
	}

	public MenuItem getSelectIncompatibleMenuItem() {
		return selectIncompatibleMenuItem;
	}

	public ScrollBar getHorizontalScrollBar() {
		return horizontalScrollBar;
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

	public ScrollBar getVerticalScrollBar() {
		return verticalScrollBar;
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

	public MenuButton getFilterMenu() {
		return filterMenu;
	}

	public Group getCanvasGroup() {
		return canvasGroup;
	}

	public Group getImageGroup() {
		return imageGroup;
	}

	public static class ListCellWithIcon<T> extends javafx.scene.control.ListCell<T> {
		private final String name;

		ListCellWithIcon(String name) {
			this.name = name;
			MaterialIcons.setIcon(this, "name");
		}

		@Override
		protected void updateItem(T item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || item == null) {
				setGraphic(null);
				setText(null);
			} else {
				MaterialIcons.setIcon(this, "name");
			}
		}
	}
}
