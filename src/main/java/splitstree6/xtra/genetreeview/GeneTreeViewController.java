/*
 *  GeneTreeViewController.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class GeneTreeViewController {

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private Menu openRecentMenu;

	@FXML
	private MenuItem importGeneNamesMenuItem;

	@FXML
	private MenuItem importFeatureMenuItem;

	@FXML
	private MenuItem exportSubsetMenuItem;

	@FXML
	private MenuItem printMenuItem;

	@FXML
	private MenuItem closeMenuItem;

	@FXML
	private MenuItem undoMenuItem;

	@FXML
	private MenuItem redoMenuItem;

	@FXML
	private MenuItem copyTaxaMenuItem;

	@FXML
	private MenuItem copyImageMenuItem;

	@FXML
	private MenuItem copySelectedNewicksMenuItem;

	@FXML
	private MenuItem copySelectedTreesMenuItem;

	@FXML
	private MenuItem pasteMenuItem;

	@FXML
	private MenuItem deleteSelectedMenuItem;

	@FXML
	private MenuItem selectAllMenuItem;

	@FXML
	private MenuItem selectNoneMenuItem;

	@FXML
	private MenuItem selectInverseMenuItem;

	@FXML
	private MenuItem selectAllTaxaMenuItem;

	@FXML
	private MenuItem selectNoTaxaMenuItem;

	@FXML
	private MenuItem selectInverseTaxaMenuItem;

	@FXML
	private ToggleGroup layoutGroup;

	@FXML
	private RadioMenuItem stackMenuItem;

	@FXML
	private RadioMenuItem carouselMenuItem;

	@FXML
	private ToggleGroup orderGroup;

	@FXML
	private RadioMenuItem defaultOrderMenuItem;

	@FXML
	private Menu taxonOrderSubMenu;

	@FXML
	private Menu similarityOrderSubMenu;

	@FXML
	private Menu featureOrderSubMenu;

	@FXML
	private ToggleGroup treeLayoutGroup;

	@FXML
	private RadioMenuItem circularCladoMenuItem;

	@FXML
	private RadioMenuItem circularPhyloMenuItem;

	@FXML
	private RadioMenuItem radialCladoMenuItem;

	@FXML
	private RadioMenuItem radialPhyloMenuItem;

	@FXML
	private RadioMenuItem rectangularCladoMenuItem;

	@FXML
	private RadioMenuItem rectangularPhyloMenuItem;

	@FXML
	private RadioMenuItem triangularCladoMenuItem;

	@FXML
	private ToggleGroup coloringGroup;

	@FXML
	private RadioMenuItem noColoringMenuItem;

	@FXML
	private RadioMenuItem monophyleticColoringMenuItem;

	@FXML
	private Menu similarityColoringSubMenu;

	@FXML
	private Menu featureColoringSubMenu;

	@FXML
	private MenuItem featureOverviewMenuItem;

	@FXML
	private MenuItem aboutMenuItem;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Slider zoomSlider;

	@FXML
	private ComboBox<String> searchGeneComboBox;

	@FXML
	private Pane centerPane;

	@FXML
	private VBox vBox;

	@FXML
	private Slider slider;

	@FXML
	private Button nextButton;

	@FXML
	private Button previousButton;

	@FXML
	private Label label;

	@FXML
	private ProgressBar progressBar;

	@FXML
	private Label progressLabel;

	@FXML
	private void initialize() {
		label.setText("");
		centerPane.setStyle("-fx-border-color: -fx-box-border; -fx-border-insets: -1;");
	}

	// File Menu
	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public Menu getOpenRecentMenu() {
		return openRecentMenu;
	}

	public MenuItem getImportGeneNamesMenuItem() {
		return importGeneNamesMenuItem;
	}

	public MenuItem getImportFeatureMenuItem() {
		return importFeatureMenuItem;
	}

	public MenuItem getExportSubsetMenuItem() {
		return exportSubsetMenuItem;
	}

	public MenuItem getPrintMenuItem() {
		return printMenuItem;
	}

	public MenuItem getCloseMenuItem() {
		return closeMenuItem;
	}

	// Edit Menu
	public MenuItem getUndoMenuItem() {
		return undoMenuItem;
	}

	public MenuItem getRedoMenuItem() {
		return redoMenuItem;
	}

	public MenuItem getCopyTaxaMenuItem() {
		return copyTaxaMenuItem;
	}

	public MenuItem getCopyImageMenuItem() {
		return copyImageMenuItem;
	}

	public MenuItem getCopySelectedNewicksMenuItem() {
		return copySelectedNewicksMenuItem;
	}

	public MenuItem getCopySelectedTreesMenuItem() {
		return copySelectedTreesMenuItem;
	}

	public MenuItem getPasteMenuItem() {
		return pasteMenuItem;
	}

	public MenuItem getDeleteSelectedMenuItem() {
		return deleteSelectedMenuItem;
	}

	// Selection Menu
	public MenuItem getSelectAllMenuItem() {
		return selectAllMenuItem;
	}

	public MenuItem getSelectNoneMenuItem() {
		return selectNoneMenuItem;
	}

	public MenuItem getSelectInverseMenuItem() {
		return selectInverseMenuItem;
	}

	public MenuItem getSelectAllTaxaMenuItem() {
		return selectAllTaxaMenuItem;
	}

	public MenuItem getSelectNoTaxaMenuItem() {
		return selectNoTaxaMenuItem;
	}

	public MenuItem getSelectInverseTaxaMenuItem() {
		return selectInverseTaxaMenuItem;
	}

	// Layout Menu
	public ToggleGroup getLayoutGroup() {
		return layoutGroup;
	}

	public RadioMenuItem getStackMenuItem() {
		return stackMenuItem;
	}

	public RadioMenuItem getCarouselMenuItem() {
		return carouselMenuItem;
	}

	public ToggleGroup getOrderGroup() {
		return orderGroup;
	}

	public RadioMenuItem getDefaultOrderMenuItem() {
		return defaultOrderMenuItem;
	}

	public Menu getTaxonOrderSubMenu() {
		return taxonOrderSubMenu;
	}

	public Menu getSimilarityOrderSubMenu() {
		return similarityOrderSubMenu;
	}

	public Menu getFeatureOrderSubMenu() {
		return featureOrderSubMenu;
	}

	// View Menu
	public ToggleGroup getTreeLayoutGroup() {
		return treeLayoutGroup;
	}

	public RadioMenuItem getCircularCladoMenuItem() {
		return circularCladoMenuItem;
	}

	public RadioMenuItem getCircularPhyloMenuItem() {
		return circularPhyloMenuItem;
	}

	public RadioMenuItem getRadialCladoMenuItem() {
		return radialCladoMenuItem;
	}

	public RadioMenuItem getRadialPhyloMenuItem() {
		return radialPhyloMenuItem;
	}

	public RadioMenuItem getRectangularCladoMenuItem() {
		return rectangularCladoMenuItem;
	}

	public RadioMenuItem getRectangularPhyloMenuItem() {
		return rectangularPhyloMenuItem;
	}

	public RadioMenuItem getTriangularCladoMenuItem() {
		return triangularCladoMenuItem;
	}

	public ToggleGroup getColoringGroup() {
		return coloringGroup;
	}

	public RadioMenuItem getNoColoringMenuItem() {
		return noColoringMenuItem;
	}

	public RadioMenuItem getMonophyleticColoringMenuItem() {
		return monophyleticColoringMenuItem;
	}

	public Menu getSimilarityColoringSubMenu() {
		return similarityColoringSubMenu;
	}

	public Menu getFeatureColoringSubMenu() {
		return featureColoringSubMenu;
	}

	public MenuItem getFeatureOverviewMenuItem() {
		return featureOverviewMenuItem;
	}

	// Help Menu
	public MenuItem getAboutMenuItem() {
		return aboutMenuItem;
	}

	// ToolBar
	public ToolBar getToolBar() {
		return toolBar;
	}

	public Slider getZoomSlider() {
		return zoomSlider;
	}

	public ComboBox<String> getSearchGeneComboBox() {
		return searchGeneComboBox;
	}

	// Center Pane for Visualizations
	public Pane getCenterPane() {
		return centerPane;
	}

	// Navigation Controls
	public VBox getvBox() {
		return vBox;
	} // vBox as container for colorBar, buttons and slider

	public Slider getSlider() {
		return slider;
	}

	public void setSlider(Slider slider) {
		this.slider = slider;
	}

	public Button getNextButton() {
		return nextButton;
	}

	public Button getPreviousButton() {
		return previousButton;
	}

	// Bottom labels and progress indicator
	public Label getLabel() {
		return label;
	}

	public ProgressBar getProgressBar() {
		return progressBar;
	}

	public Label getProgressLabel() {
		return progressLabel;
	}
}
