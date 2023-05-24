/*
 *  GeneTreeViewPresenter.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.Menu;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.*;

public class GeneTreeViewPresenter {

	Group trees = new Group();
	Group treeSnapshots = new Group();
	double treeHeight = 200;
	double treeWidth = 200;
	LayoutType layoutType = LayoutType.Stack;
	MultipleFramesLayout currentLayout = new MultipleFramesLayout() {};
	TreeDiagramType treeDiagramType = TreeDiagramType.RectangularCladogram;;
	Tooltip currentTreeToolTip = new Tooltip("");
	Tooltip previousTreeToolTip = new Tooltip("");
	Tooltip nextTreeToolTip = new Tooltip("");
	PerspectiveCamera camera = new PerspectiveCamera(true);
	ObservableList<Color> heatmapColors = FXCollections.observableArrayList();
	Service<Void> loadingTreesService;
	Service<Group> visualizeTreesService;
	//ArrayList<Integer> treeOrder;

	public GeneTreeViewPresenter(GeneTreeView geneTreeView) {

		var controller = geneTreeView.getController();
		Model model = geneTreeView.getModel();
		var centerPaneWidth = controller.getCenterPane().getPrefWidth();
		var centerPaneHeight = controller.getCenterPane().getPrefHeight();

		// Setting up a subScene with camera
		var subScene = new SubScene(trees, 600, 600, true, SceneAntialiasing.BALANCED);
		subScene.widthProperty().bind(controller.getCenterPane().widthProperty());
		subScene.heightProperty().bind(controller.getCenterPane().heightProperty());
		camera.setFarClip(1000);
		camera.setNearClip(100);
		subScene.setCamera(camera);
		controller.getCenterPane().getChildren().add(subScene);


		// MenuBar
		controller.getOpenMenuItem().setOnAction(e -> {
			openFile(geneTreeView.getStage(),controller,model);
			subScene.setRoot(trees);
			controller.getSlider().setValue(controller.getSlider().getValue());
		});

		/*model.lastUpdateProperty().addListener(a -> {
			controller.getLabel().setText("Taxa: %,d, Trees: %,d".formatted(model.getTaxaBlock().getNtax(),
					model.getTreesBlock().getNTrees()));
		});*/

		controller.getImportFeaturesMenuItem().setOnAction(e ->
				importFeatures(geneTreeView.getStage(),controller,model));

		controller.getCloseMenuItem().setOnAction(e -> Platform.exit());

		controller.getLayoutGroup().selectedToggleProperty().addListener((InvalidationListener) -> {
			updateLayout(centerPaneWidth,centerPaneHeight,controller.getLayoutGroup().getSelectedToggle(),controller);
			subScene.setRoot(trees);
		});

		controller.getTreeLayoutGroup().selectedToggleProperty().addListener((InvalidationListener) -> {
			updateTreeLayout(model.getTreesBlock(),centerPaneWidth, centerPaneHeight,
					controller.getTreeLayoutGroup().getSelectedToggle(),controller);
			subScene.setRoot(trees);
		});

		controller.getOrderGroup().selectedToggleProperty().addListener((InvalidationListener) -> {
			changeTreeOrder(model,controller.getOrderGroup().getSelectedToggle(),controller);
		});


		// ToolBar (the zoom slider is handled by the layout)
		controller.getSearchGeneComboBox().disableProperty().bind(controller.getSlider().disableProperty());
		controller.getSearchGeneComboBox().setOnAction(e -> {
			String selectedItem = controller.getSearchGeneComboBox().getSelectionModel().getSelectedItem();
			if (selectedItem != null) {
				for (int i=1; i<=model.getTreesBlock().size(); i++) {
					if (model.getTreesBlock().getTree(i).getName().equals(selectedItem)){
						controller.getSlider().setValue(i);
						controller.getSearchGeneComboBox().getEditor().setText(selectedItem);
					}
				}
			}
		});


		// Slider and buttons to go through the trees
		controller.getSlider().prefWidthProperty().bind(controller.getCenterPane().widthProperty().subtract(50));
		controller.getSlider().valueProperty().addListener((observableValue, oldValue, newValue) -> {
			currentLayout.updatePosition((double)oldValue,(double)newValue, centerPaneWidth, treeWidth);
			updateTooltip(newValue.doubleValue(),model.getTreesBlock());
		});
		controller.getSlider().setOnMouseDragged(e -> {
			currentLayout.setSliderDragged(true);
			if (currentLayout.getType() == LayoutType.Stack) subScene.setRoot(treeSnapshots);
		});
		controller.getSlider().setOnMouseReleased(e -> {
			currentLayout.setSliderDragged(false);
			if (currentLayout.getType() == LayoutType.Stack) {
				currentLayout.updatePosition(1,controller.getSlider().getValue(),centerPaneWidth, treeWidth);
				subScene.setRoot(trees);
			}
		});

		controller.getPreviousButton().setOnAction(e -> focusOnPreviousTree(controller.getSlider()));
		controller.getPreviousButton().setTooltip(previousTreeToolTip);
		controller.getPreviousButton().disableProperty().bind(controller.getSlider().disableProperty());

		controller.getNextButton().setOnAction(e -> focusOnNextTree(controller.getSlider()));
		controller.getNextButton().setTooltip(nextTreeToolTip);
		controller.getNextButton().disableProperty().bind(controller.getSlider().disableProperty());

		/*controller.getProgressIndicator().layoutXProperty().bind(controller.getCenterPane().widthProperty()
				.subtract(controller.getProgressIndicator().getPrefWidth()/2));
		controller.getProgressIndicator().layoutYProperty().bind(controller.getCenterPane().widthProperty()
				.subtract(controller.getProgressIndicator().getPrefHeight()/2));*/
	}

	private void openFile(Stage stage, GeneTreeViewController controller, Model model) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Open trees");

		var file = fileChooser.showOpenDialog(stage);
		if (file != null) {
			loadingTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new LoadTreesTask(file,model);
				}
			};
			controller.getProgressBar().visibleProperty().bind(loadingTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(loadingTreesService.progressProperty());
			loadingTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Loading trees ...");
			});
			loadingTreesService.setOnSucceeded(v -> {
				System.out.println("Loading succeeded");

				controller.getLabel().setText("Taxa: %,d, Trees: %,d".formatted(model.getTaxaBlock().getNtax(),
						model.getTreesBlock().getNTrees()));

				initializeSlider(model.getTreesBlock(), controller.getSlider());
				initializeTreesLayout(model.getTreesBlock(), controller.getCenterPane().getPrefWidth(),
						controller.getCenterPane().getPrefHeight(), controller);
				initializeGeneSearch(controller.getSearchGeneComboBox(),model.getOrderedGeneNames());
				initializeTaxaList(controller.getTaxonOrderSubMenu(),controller.getOrderGroup(),model.getTaxaBlock());
				initializeColorBar(controller.getHeatmapBox(),model.getTreesBlock(), controller.getSlider());
				controller.getImportFeaturesMenuItem().setDisable(false);
			});
			loadingTreesService.setOnFailed(u -> {
				System.out.println("Loading trees failed");
				controller.getProgressLabel().setText("Loading trees failed");
			});
			loadingTreesService.restart();
		}
	}

	private void importFeatures(Stage stage, GeneTreeViewController controller, Model model) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Import features");

		var file = fileChooser.showOpenDialog(stage);
		if (file != null) {
			Service<Void> importFeaturesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new ImportFeaturesTask(file,model);
				}
			};
			controller.getProgressBar().visibleProperty().bind(importFeaturesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(importFeaturesService.progressProperty());
			loadingTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Importing features ...");
			});
			loadingTreesService.setOnSucceeded(v -> {
				System.out.println("Import succeeded");

			});
			loadingTreesService.setOnFailed(u -> {
				System.out.println("Importing features failed");
				controller.getProgressLabel().setText("Importing features failed");
			});
			loadingTreesService.restart();
		}
	}

	public void initializeSlider(TreesBlock treesBlock, Slider slider) {
		slider.setMin(1);
		slider.setMax(treesBlock.getNTrees());
		slider.disableProperty().setValue(false);
		//Tooltip.install(slider, currentTreeToolTip);
		currentTreeToolTip.setText(treesBlock.getTree(1).getName());
		previousTreeToolTip.setText(currentTreeToolTip.getText());
		nextTreeToolTip.setText(treesBlock.getTree(2).getName());
		slider.setMinorTickCount(1);
		slider.setMajorTickUnit(5);
		//slider.setShowTickMarks(true);
		slider.setValue(1);
	}

	public void initializeTreesLayout(TreesBlock treesBlock, double paneWidth, double paneHeight,
									  GeneTreeViewController controller) {
		trees.getChildren().clear();
		treeSnapshots.getChildren().clear();
		visualizeTreesService = new Service<>() {
			@Override
			protected Task<Group> createTask() {
				return new VisualizeTreesTask(treesBlock, treeWidth, treeHeight, treeDiagramType);
			}
		};
		controller.getProgressBar().visibleProperty().bind(visualizeTreesService.runningProperty());
		controller.getProgressBar().progressProperty().bind(visualizeTreesService.progressProperty());
		//visualizeTreesService.start();
		visualizeTreesService.setOnScheduled(v -> {
			controller.getProgressLabel().setText("Drawing trees ...");
		});
		visualizeTreesService.setOnSucceeded(v -> {
			System.out.println("Visualizations succeeded");
			trees = visualizeTreesService.getValue();
			createSnapshots(); // can not be done in a service as a JavaFX thread needed
			updateLayout(paneWidth,paneHeight,controller.getLayoutGroup().getSelectedToggle(),controller);
			controller.getProgressLabel().setText("");
		});
		visualizeTreesService.setOnFailed(u -> {
			System.out.println("Visualizing trees failed");
			controller.getProgressLabel().setText("Drawing trees failed");
		});
		visualizeTreesService.restart();
	}

	private void createSnapshots() {
		for (int treeIndex = 0; treeIndex<trees.getChildren().size(); treeIndex++) {
			Node treeVis = trees.getChildren().get(treeIndex);
			Bounds bounds = treeVis.getLayoutBounds();
			WritableImage writableImage = new WritableImage((int)Math.round(bounds.getWidth()*2),
					(int)Math.round(bounds.getHeight()*2));
			SnapshotParameters parameters = new SnapshotParameters();
			parameters.setTransform(javafx.scene.transform.Transform.scale(2,2));
			Image image = treeVis.snapshot(parameters,writableImage);
			ImageView imageView = new ImageView(image);
			imageView.setFitWidth(bounds.getWidth());
			imageView.setFitHeight(bounds.getHeight());
			treeSnapshots.getChildren().add(treeIndex,imageView);
		}
	}

	private void initializeGeneSearch(ComboBox<String> searchGeneComboBox, ObservableList<String> geneNames) {
		searchGeneComboBox.setItems(geneNames);
		new ComboBoxListener(searchGeneComboBox);
	}

	private void initializeTaxaList(Menu taxonOrderSubMenu, ToggleGroup orderGroup, TaxaBlock taxaBlock) {
		for (Taxon taxon : taxaBlock.getTaxa()) {
			String taxonName = taxon.getName();
			RadioMenuItem newItem = new RadioMenuItem();
			newItem.setText(taxonName);
			newItem.setToggleGroup(orderGroup);
			taxonOrderSubMenu.getItems().add(newItem);
		}
		taxonOrderSubMenu.setDisable(false);
	}

	private void initializeColorBar(HBox heatmapBox, TreesBlock treesBlock, Slider slider) {
		heatmapBox.getChildren().clear();

		int nTrees = treesBlock.getNTrees();
		Color[] colorList = new Color[nTrees];
		for (int i = 0; i<nTrees; i++) {
			colorList[i] = Color.LIGHTGOLDENRODYELLOW;
		}
		assert false;
		heatmapColors.addAll(colorList);

		Node sliderKnob = slider.lookup(".thumb");
		double knobRadius = sliderKnob.getLayoutBounds().getWidth() / 2;

		var leftPane = new Pane();
		HBox.setMargin(leftPane, Insets.EMPTY);
		HBox.setHgrow(leftPane, Priority.NEVER);
		heatmapBox.getChildren().add(leftPane);
		var boxWidth = new SimpleDoubleProperty();
		for (int i = 0; i<nTrees; i++) {
			ColorBarBox colorBarBox = new ColorBarBox(treesBlock.getTree(i+1).getName(),heatmapColors.get(i));
			heatmapBox.getChildren().add(colorBarBox);
			if (i==nTrees-1) boxWidth.bind(colorBarBox.widthProperty());
		}
		var rightPane = new Pane();
		HBox.setMargin(rightPane,Insets.EMPTY);
		HBox.setHgrow(rightPane, Priority.NEVER);
		heatmapBox.getChildren().add(rightPane);

		leftPane.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
		rightPane.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
		heatmapBox.setVisible(true);
	}

	private void updateLayout(double paneWidth, double paneHeight, Toggle selectedLayoutToggle,
							  GeneTreeViewController controller) {
		if (selectedLayoutToggle.equals(controller.getStackMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new StackLayout(trees.getChildren(),
					treeSnapshots.getChildren(), treeWidth, treeHeight, camera, paneHeight,paneWidth,
					controller.getSlider(), controller.getZoomSlider());
			layoutType = LayoutType.Stack;
		}
		else if (selectedLayoutToggle.equals(controller.getCarouselMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new CarouselLayout(trees.getChildren(), treeWidth,
					treeHeight, camera,paneHeight, paneWidth, controller.getSlider(), controller.getZoomSlider());
			layoutType = LayoutType.Carousel;
		}
		else System.out.println("No layout");
		controller.getSlider().setValue(controller.getSlider().getValue());
	}

	private void updateTreeLayout(TreesBlock treesBlock, double paneWidth, double paneHeight,
								  Toggle selectedTreeLayoutToggle, GeneTreeViewController controller) {
		if (selectedTreeLayoutToggle.equals(controller.getRectangularCladoMenuItem()))
			treeDiagramType = TreeDiagramType.RectangularCladogram;
		else if (selectedTreeLayoutToggle.equals(controller.getRectangularPhyloMenuItem()))
			treeDiagramType = TreeDiagramType.RectangularPhylogram;
		else if (selectedTreeLayoutToggle.equals(controller.getRadialCladoMenuItem()))
			treeDiagramType = TreeDiagramType.RadialCladogram;
		else if (selectedTreeLayoutToggle.equals(controller.getRadialPhyloMenuItem()))
			treeDiagramType = TreeDiagramType.RadialPhylogram;
		else if (selectedTreeLayoutToggle.equals(controller.getCircularCladoMenuItem()))
			treeDiagramType = TreeDiagramType.CircularCladogram;
		else if (selectedTreeLayoutToggle.equals(controller.getCircularPhyloMenuItem()))
			treeDiagramType = TreeDiagramType.CircularPhylogram;
		else if (selectedTreeLayoutToggle.equals(controller.getTriangularCladoMenuItem()))
			treeDiagramType = TreeDiagramType.TriangularCladogram;
		if (treesBlock.getNTrees()>0) initializeTreesLayout(treesBlock,paneWidth,paneHeight, controller);
	}

	private void changeTreeOrder(Model model, Toggle selectedOrder, GeneTreeViewController controller) {
		if (selectedOrder == controller.getDefaultOrderMenuItem()) {
			model.resetTreeOrder();
			initializeTreesLayout(model.getTreesBlock(),controller.getCenterPane().getPrefWidth(),
					controller.getCenterPane().getPrefHeight(),controller);
		}
		else if (selectedOrder.equals(controller.getTopologyOrderMenuItem())) {
			// TODO: order trees by topological similarity with the currently selected tree
		}
		else if (selectedOrder.equals(controller.getFeatureOrderMenuItem())) {
			// TODO: order trees by feature similarity with the currently selected tree -> numerical features only
		}
		else { // remaining option: order as in taxon ...
			int toggleIndex = controller.getOrderGroup().getToggles().indexOf(selectedOrder)-3;
			String taxonName = controller.getTaxonOrderSubMenu().getItems().get(toggleIndex).getText();
			System.out.println(taxonName);
			// TODO: order trees as in the selected taxon
		}
	}

	public void updateTooltip(double sliderValue, TreesBlock treesBlock) {
		int index = (int)Math.round(sliderValue);
		var currentTree = treesBlock.getTree(index);
		currentTreeToolTip.setText(currentTree.getName());
		if (index < treesBlock.size()-1) {
			var nextTree = treesBlock.getTree(index+1);
			nextTreeToolTip.setText(nextTree.getName());
		}
		else nextTreeToolTip.setText(currentTreeToolTip.getText());
		if (index>1) {
			var previousTree = treesBlock.getTree(index-1);
			previousTreeToolTip.setText(previousTree.getName());
		}
		else previousTreeToolTip.setText(currentTreeToolTip.getText());
	}

	private void focusOnPreviousTree(Slider slider) {
		if (slider.getValue() < 2) {
			slider.setValue(1);
			return;
		}
		if (Math.abs(slider.getValue() - Math.round(slider.getValue())) < 0.05) slider.setValue(Math.round(slider.getValue()-1));
		else slider.setValue((int)slider.getValue());
	}

	private void focusOnNextTree(Slider slider) {
		if (slider.getValue() > slider.getMax()-1) {
			slider.setValue(slider.getMax());
			return;
		}
		if (Math.abs(slider.getValue() - Math.round(slider.getValue())) < 0.05) slider.setValue(Math.round(slider.getValue()+1));
		else slider.setValue((int)slider.getValue()+1);
	}
}
