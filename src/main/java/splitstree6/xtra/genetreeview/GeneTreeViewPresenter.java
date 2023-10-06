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

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.util.Duration;
import jloda.fx.util.ColorSchemeManager;
import jloda.fx.util.Print;

import jloda.fx.util.RunAfterAWhile;
import jloda.phylo.PhyloTree;
import jloda.util.FileLineIterator;
import jloda.util.progress.ProgressPercentage;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.io.readers.trees.NewickReader;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.utils.*;
import splitstree6.xtra.genetreeview.io.*;
import splitstree6.xtra.genetreeview.layout.*;
import splitstree6.xtra.genetreeview.model.*;
import splitstree6.xtra.genetreeview.util.*;

import java.io.*;
import java.util.*;

public class GeneTreeViewPresenter {

	private Group trees = new Group();
	private final Group treeSnapshots = new Group();
	private final double treeHeight = 250;
	private final double treeWidth = 220; // including taxa label space ~ 80 // TODO: make label space more flexible
	private MultipleFramesLayout currentLayout = new MultipleFramesLayout() {};
	private TreeDiagramType treeDiagramType = TreeDiagramType.RectangularCladogram;;
	private final Tooltip currentTreeToolTip = new Tooltip("");
	private final Tooltip previousTreeToolTip = new Tooltip("");
	private final Tooltip nextTreeToolTip = new Tooltip("");
	private final PerspectiveCamera camera = new PerspectiveCamera(true);
	private ColorBar colorBar = null;
	private final SelectionModelSet<Integer> treeSelectionModel = new SelectionModelSet<>();
	private final SelectionModelSet<Integer> taxaSelectionModel = new SelectionModelSet<>();
	private final HashMap<Integer,SelectionModelSet<Integer>> treeId2edgeSelectionModel = new HashMap<>();
	private final HashMap<Integer,TreeSheet> id2treeSheet = new HashMap<>();
	private final IntegerProperty treesCount = new SimpleIntegerProperty(0);
	private final IntegerProperty taxaCount = new SimpleIntegerProperty(0);
	private ChangeListener<Toggle> orderGroupListener = null;
	private final ChangeListener<Toggle> colorGroupListener;
	private final UndoRedoManager undoRedoManager = new UndoRedoManager();
	private double lastSliderValue = 1;
	private final Stabilizer stabilizer = new Stabilizer();
	private final ArrayList<String> recentFiles = new ArrayList<>();
	private final IntegerProperty recentFilesCount = new SimpleIntegerProperty(0);

	public GeneTreeViewPresenter(GeneTreeView geneTreeView) {

		var controller = geneTreeView.getController();
		Model model = geneTreeView.getModel();

		// Setting up a subScene with camera (further camera settings are done by the layout)
		var subScene = new SubScene(trees, controller.getCenterPane().getWidth(), controller.getCenterPane().getHeight(),
				true, SceneAntialiasing.BALANCED);

		subScene.widthProperty().bind(controller.getCenterPane().widthProperty());
		subScene.heightProperty().bind(controller.getCenterPane().heightProperty());

		subScene.setCamera(camera);
		controller.getCenterPane().getChildren().add(subScene);


		// MenuBar
		// File Menu
		controller.getOpenMenuItem().setOnAction(e -> openFile(geneTreeView.getStage(), controller, model, subScene));

		controller.getOpenRecentMenu().disableProperty().bind(recentFilesCount.isEqualTo(0));

		controller.getImportGeneNamesMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getImportGeneNamesMenuItem().setOnAction(e ->
				importGeneNames(geneTreeView.getStage(), model, controller));

		controller.getImportFeatureMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getImportFeatureMenuItem().setOnAction(e ->
				importFeature(geneTreeView.getStage(), model, controller));

		controller.getExportSubsetMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0));
		controller.getExportSubsetMenuItem().setOnAction(e ->
		{
			try {
				exportTreeSubset(geneTreeView.getStage(),controller,model);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});

		controller.getPrintMenuItem().setOnAction(e -> {
			WritableImage writableImage = new WritableImage((int) controller.getCenterPane().getWidth()*3,
					(int) controller.getCenterPane().getHeight()*3); // factor 3 for better resolution
			SnapshotParameters parameters = new SnapshotParameters();
			parameters.setTransform(javafx.scene.transform.Transform.scale(3,3));
			Image image = controller.getCenterPane().snapshot(parameters, writableImage);
			ImageView imageView = new ImageView(image);
			Print.print(geneTreeView.getStage(),imageView);
		});

		controller.getCloseMenuItem().setOnAction(e -> Platform.exit());

		// Edit Menu
		controller.getUndoMenuItem().setOnAction(e -> undoRedoManager.undo());
		controller.getUndoMenuItem().textProperty().bind(undoRedoManager.undoLabelProperty());
		controller.getUndoMenuItem().disableProperty().bind(undoRedoManager.canUndoProperty().not());
		controller.getRedoMenuItem().setOnAction(e -> undoRedoManager.redo());
		controller.getRedoMenuItem().textProperty().bind(undoRedoManager.redoLabelProperty());
		controller.getRedoMenuItem().disableProperty().bind(undoRedoManager.canRedoProperty().not());

		controller.getCopyTaxaMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getCopyTaxaMenuItem().setOnAction(e -> {
			StringBuilder taxa = new StringBuilder();
			if (taxaSelectionModel.size() == 0) {
				for (Taxon taxon : model.getTaxaBlock().getTaxa())
					taxa.append(taxon.getName()).append("\n");
			}
			else {
				for (int taxonId : taxaSelectionModel.getSelectedItems())
					taxa.append(model.getTaxaBlock().get(taxonId).getName()).append("\n");
			}
			ClipboardContent content = new ClipboardContent();
			content.putString(taxa.toString());
			Clipboard.getSystemClipboard().setContent(content);
		});

		controller.getCopyImageMenuItem().setOnAction(e -> {
			WritableImage writableImage = new WritableImage((int) controller.getCenterPane().getWidth()*2,
					(int) controller.getCenterPane().getHeight()*2); // factor 2 for better resolution
			SnapshotParameters parameters = new SnapshotParameters();
			//parameters.setFill(Color.TRANSPARENT); // for black background
			parameters.setTransform(javafx.scene.transform.Transform.scale(2,2));
			Image image = controller.getCenterPane().snapshot(parameters, writableImage);

			ClipboardContent content = new ClipboardContent();
			content.putImage(image);
			Clipboard.getSystemClipboard().setContent(content);
		});

		controller.getCopySelectedNewicksMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0));
		controller.getCopySelectedNewicksMenuItem().setOnAction(e -> {
			StringBuilder newicks = new StringBuilder();
			var temp = new File("temp.txt");
			Service<Void> exportTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new ExportTreesTask(temp, model, treeSelectionModel.getSelectedItems());
				}
			};
			controller.getProgressBar().visibleProperty().bind(exportTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(exportTreesService.progressProperty());
			exportTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Copying selected trees in newick format");
			});
			exportTreesService.setOnSucceeded(v -> {
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(temp)));
					String line;
					while ((line = br.readLine()) != null) {
						newicks.append(line).append("\n");
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
				var clipboardContent = new ClipboardContent();
				clipboardContent.putString(newicks.toString());
				Clipboard.getSystemClipboard().setContent(clipboardContent);
				System.out.println("Copying succeeded");
				controller.getProgressLabel().setText("");
			});
			exportTreesService.setOnFailed(u -> {
				System.out.println("Copying failed");
				controller.getProgressLabel().setText("Copying failed");
			});
			exportTreesService.restart();
		});

		controller.getCopySelectedTreesMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0));
		controller.getCopySelectedTreesMenuItem().setOnAction(e -> {
			var clipboardContent = new ClipboardContent();
			var gridPane = new GridPane();
			int minColumnNumber = 2;
			if (treeSelectionModel.size() > 6) {
				minColumnNumber = 3;
				if (treeSelectionModel.size() > 12) {
					minColumnNumber = 4;
					if (treeSelectionModel.size() > 28) minColumnNumber = 5;
				}
			}
			int columnNumber = minColumnNumber;
			for (int i = 5; i >= minColumnNumber; i--) {
				if (treeSelectionModel.size()%i == 0) {
					columnNumber = i;
					break;
				}
			}
			TreeSet<Integer> selectedTreeIds = new TreeSet<>(treeSelectionModel.getSelectedItems());
			for (int treeId : selectedTreeIds) treeSelectionModel.setSelected(treeId, false);
			int finalColumnNumber = columnNumber;
			Runnable copyImages = () -> {
				int columnCount = 0;
				int rowCount = 0;
				for (int treeId : selectedTreeIds) {
					int index = model.getGeneTreeSet().getPosition(treeId);
					ImageView treeSnap = (ImageView) treeSnapshots.getChildren().get(index);
					Image treeImage = treeSnap.getImage();
					WritableImage treeImageCopy = new WritableImage(treeImage.getPixelReader(),
							(int) treeImage.getWidth(), (int) treeImage.getHeight());
					var finalImage = new ImageView(treeImageCopy);
					gridPane.add(finalImage,columnCount,rowCount);
					columnCount++;
					if (columnCount% finalColumnNumber == 0) {
						rowCount++;
						columnCount = 0;
					}
					treeSelectionModel.select(treeId);
				}
				Image image = gridPane.snapshot(null,null);
				clipboardContent.putImage(image);
				Clipboard.getSystemClipboard().setContent(clipboardContent);
			};
			Platform.runLater(() -> RunAfterAWhile.applyInFXThread("ImageCopying", copyImages));
		});

		controller.getPasteMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getPasteMenuItem().setOnAction(event -> {
			var pastedTrees = pasteTrees(geneTreeView.getStage(), model, controller);
			if (pastedTrees != null) {
				Runnable undo = () -> {
					for (int i = pastedTrees.size()-1; i >= 0; i--)
						removeTree(pastedTrees.get(i), model, controller);
				};
				Runnable redo = () -> pasteTrees(pastedTrees, model, controller);
				undoRedoManager.add(new SimpleCommand("paste", undo, redo));
			}
		});

		controller.getDeleteSelectedMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0)
				.or(treesCount.isEqualTo(0)));
		controller.getDeleteSelectedMenuItem().setOnAction(e -> {
			TreeMap<Integer, GeneTree> deletedPosition2geneTree = new TreeMap<>(Collections.reverseOrder());
			ArrayList<GeneTree> deletedTrees = new ArrayList<>();
			for (var treeId : treeSelectionModel.getSelectedItems()) {
				var geneTree = model.getGeneTreeSet().getGeneTree(treeId);
				deletedTrees.add(geneTree);
				deletedPosition2geneTree.put(geneTree.getPosition(), geneTree);
			}
			for (int position : deletedPosition2geneTree.keySet()) {
				removeTree(deletedPosition2geneTree.get(position), model, controller);
			}
			Runnable undo = () -> pasteTrees(deletedTrees, model, controller);
			Runnable redo = () -> {
				for (int position : deletedPosition2geneTree.keySet()) {
					removeTree(deletedPosition2geneTree.get(position), model, controller);
				}
			};
			undoRedoManager.add(new SimpleCommand("delete", undo, redo));
		});

		controller.getFeatureOverviewMenuItem().disableProperty().bind(controller.getFeatureColoringSubMenu().disableProperty());
		controller.getFeatureOverviewMenuItem().setOnAction(e -> {
			new FeatureOverviewDialog(geneTreeView.getStage(), model.getGeneTreeSet());
		});

		// Selection Menu: Tree Selection
		controller.getSelectAllMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(treesCount)
				.or(treesCount.isEqualTo(0)));
		controller.getSelectAllMenuItem().setOnAction(e -> treeSelectionModel.selectAll(id2treeSheet.keySet()));

		controller.getSelectNoneMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0)
				.or(treesCount.isEqualTo(0)));
		controller.getSelectNoneMenuItem().setOnAction(e -> treeSelectionModel.clearSelection());

		controller.getSelectInverseMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getSelectInverseMenuItem().setOnAction(e -> {
			var formerSelection = treeSelectionModel.getSelectedItems();
			var newSelection = new HashSet<Integer>();
			for (int treeId : id2treeSheet.keySet()) {
				if (formerSelection.contains(treeId)) continue;
				newSelection.add(treeId);
			}
			treeSelectionModel.clearSelection();
			treeSelectionModel.selectAll(newSelection);
		});

		treeSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Integer>) c -> {
			if (c.wasAdded()) {
				int treeId = c.getElementAdded();
				TreeSheet treeSheet = id2treeSheet.get(treeId);
				treeSheet.setSelectedProperty(true);
				ColorBarBox colorBarBox = colorBar.getId2colorBarBox().get(treeId);
				colorBarBox.setSelectedProperty(true);
			} else if (c.wasRemoved()) {
				int treeId = c.getElementRemoved();
				TreeSheet treeSheet = id2treeSheet.get(treeId);
				treeSheet.setSelectedProperty(false);
				ColorBarBox colorBarBox = colorBar.getId2colorBarBox().get(treeId);
				colorBarBox.setSelectedProperty(false);
			}
		});

		// Selection Menu: Taxa Selection
		controller.getSelectAllTaxaMenuItem().disableProperty().bind(taxaSelectionModel.sizeProperty().isEqualTo(taxaCount)
				.or(taxaCount.isEqualTo(0)));
		controller.getSelectAllTaxaMenuItem().setOnAction(e -> {
			for (var taxon : model.getTaxaBlock().getTaxa()) {
				taxaSelectionModel.select(model.getTaxaBlock().indexOf(taxon));
			}
		});

		controller.getSelectNoTaxaMenuItem().disableProperty().bind(taxaSelectionModel.sizeProperty().isEqualTo(0)
				.or(taxaCount.isEqualTo(0)));
		controller.getSelectNoTaxaMenuItem().setOnAction(e -> taxaSelectionModel.clearSelection());

		controller.getSelectInverseTaxaMenuItem().disableProperty().bind(taxaCount.isEqualTo(0));
		controller.getSelectInverseTaxaMenuItem().setOnAction(e -> {
			var formerSelection = taxaSelectionModel.getSelectedItems();
			var newSelection = new HashSet<Integer>();
			for (var taxon : model.getTaxaBlock().getTaxa()) {
				int taxonId = model.getTaxaBlock().indexOf(taxon);
				if (formerSelection.contains(taxonId)) continue;
				newSelection.add(taxonId);
			}
			taxaSelectionModel.clearSelection();
			taxaSelectionModel.selectAll(newSelection);
		});

		taxaSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Integer>) c -> {
			if (c.wasAdded()) {
				int taxonId = c.getElementAdded();
				String taxonName = model.getTaxaBlock().getLabel(taxonId);
				for (int index = 0; index < trees.getChildren().size(); index++) {
					TreeSheet treeSheet = (TreeSheet)trees.getChildren().get(index);
					if (treeSheet.selectTaxon(taxonName, true)) {
						Runnable updateEdgeSelection = treeSheet::updateEdgeSelection;
						RunAfterAWhile.applyInFXThread(treeSheet.getTreeId(), updateEdgeSelection);
						if (controller.getColoringGroup().getSelectedToggle() == controller.getMonophyleticColoringMenuItem())
							updateColoring(controller.getColoringGroup(), controller.getColoringGroup().getSelectedToggle(),
									controller.getColoringGroup().getSelectedToggle(), controller, model, geneTreeView.getStage());
					}
				}
			} else if (c.wasRemoved()) {
				int taxonId = c.getElementRemoved();
				String taxonName = model.getTaxaBlock().getLabel(taxonId);
				for (int index = 0; index < trees.getChildren().size(); index++) {
					TreeSheet treeSheet = (TreeSheet)trees.getChildren().get(index);
					if (treeSheet.selectTaxon(taxonName, false)) {
						Runnable updateEdgeSelection = treeSheet::updateEdgeSelection;
						RunAfterAWhile.applyInFXThread(treeSheet.getTreeId(), updateEdgeSelection);
						if (controller.getColoringGroup().getSelectedToggle() == controller.getMonophyleticColoringMenuItem())
							updateColoring(controller.getColoringGroup(),controller.getColoringGroup().getSelectedToggle(),
									controller.getColoringGroup().getSelectedToggle(), controller, model, geneTreeView.getStage());
					}
				}
			}
		});

		// Layout Menu
		controller.getLayoutGroup().selectedToggleProperty().addListener((observableValue, oldValue, newValue) -> {
			updateLayout(controller.getLayoutGroup().getSelectedToggle(),controller);
			Runnable undo = () -> controller.getLayoutGroup().selectToggle(oldValue);
			Runnable redo = () -> controller.getLayoutGroup().selectToggle(newValue);
			undoRedoManager.add(new SimpleCommand("layout", undo, redo));
		});

		orderGroupListener = (observableValue, oldValue, newValue) -> {
			try {
				var oldTreeOrder = model.getGeneTreeSet().getTreeOrder();
				changeTreeOrder(model, controller.getOrderGroup(), oldValue, newValue, controller, subScene,
						geneTreeView.getStage());
				var newTreeOrder = model.getGeneTreeSet().getTreeOrder();
				Runnable undo = () -> {
					controller.getOrderGroup().selectedToggleProperty().removeListener(orderGroupListener);
					controller.getOrderGroup().selectToggle(oldValue);
					model.getGeneTreeSet().setTreeOrder(oldTreeOrder);
					initializeTreesLayout(model, controller, subScene);
					colorBar.reorder(oldTreeOrder);
					initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
							controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),
							model.getGeneTreeSet().getOrderedGeneNames());
					controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);
				};
				Runnable redo = () -> {
					controller.getOrderGroup().selectedToggleProperty().removeListener(orderGroupListener);
					controller.getOrderGroup().selectToggle(newValue);
					model.getGeneTreeSet().setTreeOrder(newTreeOrder);
					initializeTreesLayout(model, controller, subScene);
					colorBar.reorder(newTreeOrder);
					initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
							controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
					controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);
				};
				undoRedoManager.add(new SimpleCommand("order", undo, redo));
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
		controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);

		// View Menu
		controller.getTreeLayoutGroup().selectedToggleProperty().addListener((observableValue,oldValue,newValue) -> {
			updateTreeLayout(model, controller.getTreeLayoutGroup().getSelectedToggle(), controller, subScene);
			Runnable undo = () -> controller.getTreeLayoutGroup().selectToggle(oldValue);
			Runnable redo = () -> controller.getTreeLayoutGroup().selectToggle(newValue);
			undoRedoManager.add(new SimpleCommand("view", undo, redo));
		});

		colorGroupListener = (observableValue, oldValue, newValue) -> {
			updateColoring(controller.getColoringGroup(), oldValue, newValue, controller, model, geneTreeView.getStage());
			Runnable undo = () -> controller.getColoringGroup().selectToggle(oldValue);
			Runnable redo = () -> controller.getColoringGroup().selectToggle(newValue);
			undoRedoManager.add(new SimpleCommand("coloring", undo, redo));
		};
		controller.getColoringGroup().selectedToggleProperty().addListener(colorGroupListener);

		// Help Menu
		controller.getAboutMenuItem().setOnAction(e -> {
			// TODO: show some kind of information
		});

		// ToolBar (the zoom slider is handled by the layout)
		controller.getSearchGeneComboBox().disableProperty().bind(controller.getSlider().disableProperty());
		controller.getSearchGeneComboBox().setOnAction(e -> {
			String selectedItem = controller.getSearchGeneComboBox().getSelectionModel().getSelectedItem();
			if (selectedItem != null) {
				for (int i = 0; i < model.getGeneTreeSet().getOrderedGeneNames().size(); i++) {
					if (model.getGeneTreeSet().getOrderedGeneNames().get(i).equals(selectedItem)){
						double initialSliderValue = controller.getSlider().getValue();
						double targetValue = i+1;
						double distance = Math.abs(targetValue-initialSliderValue);
						double duration = Math.min(distance*300,3000);
						var keyValue = new KeyValue(controller.getSlider().valueProperty(), targetValue);
						var keyFrame = new KeyFrame(Duration.millis(duration),keyValue);
						var timeLine = new Timeline(keyFrame);
						timeLine.play();
						controller.getSearchGeneComboBox().getEditor().setText(selectedItem);
					}
				}
			}
		});


		// Navigation: Slider and buttons to go through the trees
		controller.getSlider().prefWidthProperty().bind(controller.getCenterPane().widthProperty().subtract(50)); // space 50 for buttons
		controller.getSlider().valueProperty().addListener((observableValue, oldValue, newValue) -> {
			currentLayout.updatePosition((double)oldValue, (double)newValue);
			updateButtonTooltips(controller.getSlider().getValue(), model.getGeneTreeSet().getOrderedGeneNames());
		});
		// For Stack layout: using snapshots whenever the slider is dragged for fluent performance
		controller.getSlider().setOnDragDetected((e -> {
			lastSliderValue = controller.getSlider().getValue(); // remember value for undo/redo
			if (currentLayout.getType() == LayoutType.Stack) {
				((StackLayout) currentLayout).setSliderDragged(true);
				subScene.setRoot(treeSnapshots);
			}
		}));
		controller.getSlider().setOnMouseReleased(e -> {
			if (currentLayout.getType() == LayoutType.Stack) {
				((StackLayout) currentLayout).setSliderDragged(false);
				currentLayout.updatePosition(1, controller.getSlider().getValue());
				subScene.setRoot(trees);
			}
			undoRedoManager.add(new PropertyCommand<>("navigation",
					controller.getSlider().valueProperty(), lastSliderValue, controller.getSlider().getValue()));
		});

		controller.getPreviousButton().setOnAction(e -> {
			double oldValue = controller.getSlider().getValue();
			focusOnPreviousTree(controller.getSlider());
			double newValue = controller.getSlider().getValue();
			Runnable undo = () -> controller.getSlider().setValue(oldValue);
			Runnable redo = () -> controller.getSlider().setValue(newValue);
			undoRedoManager.add(new SimpleCommand("navigation", undo, redo));
		});
		controller.getPreviousButton().setTooltip(previousTreeToolTip);
		controller.getPreviousButton().disableProperty().bind(controller.getSlider().disableProperty());

		controller.getNextButton().setOnAction(e -> {
			double oldValue = controller.getSlider().getValue();
			focusOnNextTree(controller.getSlider());
			double newValue = controller.getSlider().getValue();
			Runnable undo = () -> controller.getSlider().setValue(oldValue);
			Runnable redo = () -> controller.getSlider().setValue(newValue);
			undoRedoManager.add(new SimpleCommand("navigation", undo, redo));
		});
		controller.getNextButton().setTooltip(nextTreeToolTip);
		controller.getNextButton().disableProperty().bind(controller.getSlider().disableProperty());
	}

	private void openFile(Stage stage, GeneTreeViewController controller, Model model, SubScene subScene) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Open trees");
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Newick Trees (tre,tree,trees,new,nwk,treefile)",
				"*.tre","*.tree",".trees", ".new", ".nwk", ".treefile"));

		var file = fileChooser.showOpenDialog(stage);
		openFile(file, stage, controller, model, subScene);
	}

	private void openFile(File file, Stage stage, GeneTreeViewController controller, Model model, SubScene subScene) {
		if (file != null) {
			recentFiles.remove(file.getPath());
			recentFiles.add(0, file.getPath());
			while (recentFiles.size() > 10) recentFiles.remove(10);
			updateOpenRecentMenu(stage, controller, model, subScene);
			Service<Void> loadingTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new LoadTreesTask(file, model, stabilizer);
				}
			};
			controller.getProgressBar().visibleProperty().bind(loadingTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(loadingTreesService.progressProperty());
			loadingTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Loading trees ...");
			});
			loadingTreesService.setOnSucceeded(v -> {
				System.out.println("Loading succeeded");
				treesCount.set(model.getGeneTreeSet().size());
				taxaCount.set(model.getTaxaBlock().getNtax());
				updateInfoLabel(controller.getLabel());
				trees.getChildren().clear();
				treeSnapshots.getChildren().clear();
				treeSelectionModel.clearSelection();
				taxaSelectionModel.clearSelection();
				treeId2edgeSelectionModel.clear();
				colorBar = null;
				initializeSlider(model.getGeneTreeSet(), controller.getSlider());
				initializeColorBar(controller.getvBox(), model.getGeneTreeSet(), controller.getSlider());
				initializeTreesLayout(model, controller, subScene);
				initializeGeneSearch(controller.getSearchGeneComboBox(), model.getGeneTreeSet().getOrderedGeneNames());
				controller.getOrderGroup().selectedToggleProperty().removeListener(orderGroupListener);
				controller.getDefaultOrderMenuItem().setSelected(true);
				controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);
				controller.getFeatureOrderSubMenu().setDisable(true);
				controller.getFeatureOrderSubMenu().getItems().clear();
				controller.getColoringGroup().selectedToggleProperty().removeListener(colorGroupListener);
				controller.getNoColoringMenuItem().setSelected(true);
				controller.getColoringGroup().selectedToggleProperty().addListener(colorGroupListener);
				controller.getFeatureColoringSubMenu().setDisable(true);
				controller.getFeatureColoringSubMenu().getItems().clear();
				initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
						controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
				initializeTaxaList(controller.getTaxonOrderSubMenu(),controller.getOrderGroup(), model.getTaxaBlock());
			});
			loadingTreesService.setOnFailed(u -> {
				System.out.println("Loading trees failed");
				controller.getProgressLabel().setText("Loading trees failed");
			});
			loadingTreesService.restart();

		}
	}

	private void updateOpenRecentMenu(Stage stage, GeneTreeViewController controller, Model model, SubScene subScene) {
		controller.getOpenRecentMenu().getItems().clear();
		for (String filePath : recentFiles) {
			MenuItem recentFileMenuItem = new MenuItem(filePath);
			recentFileMenuItem.setOnAction(e -> openFile(new File(filePath), stage, controller, model, subScene));
			controller.getOpenRecentMenu().getItems().add(recentFileMenuItem);
		}
		recentFilesCount.set(recentFiles.size());
	}

	private void importGeneNames(Stage stage, Model model, GeneTreeViewController controller) {
		var geneNameParser = new GeneNameParser(stage, model);
		// If new names have been parsed to the model, names in treeSheets and snapshots need to be updated:
		geneNameParser.getParsedProperty().addListener((InvalidationListener) -> {
			for (int i = 0; i < model.getGeneTreeSet().size(); i++) {
				((TreeSheet)trees.getChildren().get(i)).setTreeName(model.getGeneTreeSet().getOrderedGeneNames().get(i));
			}
			initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
					controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
			colorBar.setNames(model.getGeneTreeSet().getOrderedGeneNames());
		});
	}

	private void importFeature(Stage stage, Model model, GeneTreeViewController controller) {
		var featureParser = new FeatureParser(stage, model);
		featureParser.getParsedProperty().addListener((InvalidationListener) -> {
			String featureName = featureParser.getParsedFeatureName();
			RadioMenuItem newFeatureOrderMenuItem = new RadioMenuItem();
			newFeatureOrderMenuItem.setText(featureName);
			newFeatureOrderMenuItem.setToggleGroup(controller.getOrderGroup());
			controller.getFeatureOrderSubMenu().getItems().add(newFeatureOrderMenuItem);
			controller.getFeatureOrderSubMenu().setDisable(false);

			RadioMenuItem newFeatureColoringMenuItem = new RadioMenuItem();
			newFeatureColoringMenuItem.setText(featureName);
			newFeatureColoringMenuItem.setToggleGroup(controller.getColoringGroup());
			controller.getFeatureColoringSubMenu().getItems().add(newFeatureColoringMenuItem);
			controller.getFeatureColoringSubMenu().setDisable(false);
			colorBar.addValuesToTooltip(featureName, model.getGeneTreeSet().getFeatureValues(featureName));
		});
	}

	private void exportTreeSubset(Stage stage, GeneTreeViewController controller, Model model) throws IOException {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Save tree subset");
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Newick Trees (tre,tree,trees,new,nwk,treefile)",
				"*.tre","*.tree",".trees", ".new", ".nwk", ".treefile"));

		var file = fileChooser.showSaveDialog(stage);
		if (file != null) {
			Service<Void> exportTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new ExportTreesTask(file, model, treeSelectionModel.getSelectedItems());
				}
			};
			controller.getProgressBar().visibleProperty().bind(exportTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(exportTreesService.progressProperty());
			exportTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Saving subset of trees in "+file.getName());
			});
			exportTreesService.setOnSucceeded(v -> {
				System.out.println("Export succeeded");
				controller.getProgressLabel().setText("");
			});
			exportTreesService.setOnFailed(u -> {
				System.out.println("Export failed");
				controller.getProgressLabel().setText("Saving subset of trees failed");
			});
			exportTreesService.restart();
		}
	}

	public void initializeSlider(GeneTreeSet geneTreeSet, Slider slider) {
		slider.setMin(1);
		slider.maxProperty().bind(treesCount);
		if (geneTreeSet.size() > 0) {
			currentTreeToolTip.setText(geneTreeSet.getGeneTree(1).getGeneName());
			previousTreeToolTip.setText(currentTreeToolTip.getText());
		}
		if (geneTreeSet.size() > 1)
			nextTreeToolTip.setText(geneTreeSet.getGeneTree(2).getGeneName());
		slider.setMinorTickCount(1);
		slider.setMajorTickUnit(5);
		slider.setValue(1);
	}

	private void initializeColorBar(VBox parent, GeneTreeSet geneTreeSet, Slider slider) {
		colorBar = new ColorBar(geneTreeSet, slider);
		parent.getChildren().remove(0);
		parent.getChildren().add(0,colorBar);
	}

	public void initializeTreesLayout(Model model, GeneTreeViewController controller, SubScene subScene) {
		trees.getChildren().clear();
		Service<Group> visualizeTreesService = new Service<>() {
			@Override
			protected Task<Group> createTask() {
				return new VisualizeTreesTask(model, treeWidth, treeHeight, treeDiagramType, taxaSelectionModel,
						treeId2edgeSelectionModel);
			}
		};
		controller.getProgressBar().visibleProperty().bind(visualizeTreesService.runningProperty());
		controller.getProgressBar().progressProperty().bind(visualizeTreesService.progressProperty());
		visualizeTreesService.setOnScheduled(v -> {
			controller.getProgressLabel().setText("Drawing trees ...");
		});
		visualizeTreesService.setOnSucceeded(v -> {
			controller.getCenterPane().getChildren().clear();
			trees = visualizeTreesService.getValue();
			treeSnapshots.getChildren().clear();
			for (var ignored : trees.getChildren()) treeSnapshots.getChildren().add(new Rectangle());
			for (Node node : trees.getChildren()) {
				TreeSheet treeSheet = (TreeSheet) node;
				int id = treeSheet.getTreeId();
				id2treeSheet.put(id, treeSheet);
				if (treeSelectionModel.getSelectedItems().contains(id)) {
					treeSheet.setSelectedProperty(true);
					colorBar.getId2colorBarBox().get(id).setSelectedProperty(true);
				}
				setupTreeSelectionAndSnapshots(treeSheet, id);
			}
			subScene.setRoot(trees);
			controller.getCenterPane().getChildren().add(subScene);
			updateLayout(controller.getLayoutGroup().getSelectedToggle(), controller);
			System.out.println("Visualizations succeeded");
			controller.getSlider().setDisable(false);
			controller.getProgressLabel().setText("");
		});
		visualizeTreesService.setOnFailed(u -> {
			System.out.println("Visualizing trees failed");
			controller.getProgressLabel().setText("Drawing trees failed");
		});
		visualizeTreesService.restart();
	}

	private void setupTreeSelectionAndSnapshots(TreeSheet treeSheet, int treeId) {
		treeSheet.layoutBoundsProperty().addListener((observable,oldValue,newValue) -> {
			if (newValue.getWidth()>0 & newValue.getHeight()>0) {
				Runnable updateSnapshot = () -> updateSnapshot(trees.getChildren().indexOf(treeSheet));
				RunAfterAWhile.applyInFXThread(treeSheet, updateSnapshot);
			}
		});
		treeSheet.lastUpdateProperty().addListener(((observableValue, oldValue, newValue) -> {
			if (trees.getChildren().contains(treeSheet)) {
				Runnable updateSnapshot = () -> updateSnapshot(trees.getChildren().indexOf(treeSheet));
				RunAfterAWhile.applyInFXThreadOrClearIfAlreadyWaiting(treeSheet, updateSnapshot);
			}
		}));
		makeTreeSelectable(treeId);
	}

	private void makeTreeSelectable(int treeId) {
		id2treeSheet.get(treeId).getSelectionRectangle().setOnMouseClicked(e -> {
			boolean selectedBefore = treeSelectionModel.getSelectedItems().contains(treeId);
			if (!e.isShiftDown()) {
				treeSelectionModel.clearSelection();
				if (!selectedBefore)
					treeSelectionModel.select(treeId);
			} else
				treeSelectionModel.setSelected(treeId, !selectedBefore);
		});
		colorBar.getId2colorBarBox().get(treeId).setOnMouseClicked(e -> {
			boolean selectedBefore = treeSelectionModel.getSelectedItems().contains(treeId);
			if (!e.isShiftDown()) {
				treeSelectionModel.clearSelection();
				if (!selectedBefore)
					treeSelectionModel.select(treeId);
			} else
				treeSelectionModel.setSelected(treeId, !selectedBefore);
		});
	}

	private void initializeGeneSearch(ComboBox<String> searchGeneComboBox, ObservableList<String> geneNames) {
		searchGeneComboBox.setItems(geneNames);
		new ComboBoxListener(searchGeneComboBox);
	}

	private void initializeTreeLists(Menu similarityColoringSubMenu, ToggleGroup coloringGroup,
									 Menu similarityOrderSubMenu, ToggleGroup orderGroup, ObservableList<String> geneNames) {
		similarityColoringSubMenu.getItems().clear();
		similarityOrderSubMenu.getItems().clear();
		for (var geneName : geneNames) {
			RadioMenuItem newColoringItem = new RadioMenuItem();
			newColoringItem.setText(geneName);
			newColoringItem.setToggleGroup(coloringGroup);
			similarityColoringSubMenu.getItems().add(newColoringItem);

			RadioMenuItem newOrderMenuItem = new RadioMenuItem();
			newOrderMenuItem.setText(geneName);
			newOrderMenuItem.setToggleGroup(orderGroup);
			similarityOrderSubMenu.getItems().add(newOrderMenuItem);
		}
		similarityColoringSubMenu.setDisable(false);
		similarityOrderSubMenu.setDisable(false);
	}

	private void initializeTaxaList(Menu taxonOrderSubMenu, ToggleGroup orderGroup, TaxaBlock taxaBlock) {
		taxonOrderSubMenu.getItems().clear();
		for (Taxon taxon : taxaBlock.getTaxa()) {
			String taxonName = taxon.getName();
			RadioMenuItem newItem = new RadioMenuItem();
			newItem.setText(taxonName);
			newItem.setToggleGroup(orderGroup);
			newItem.setUserData(taxonName);
			taxonOrderSubMenu.getItems().add(newItem);
		}
		taxonOrderSubMenu.setDisable(false);
	}

	private ArrayList<GeneTree> pasteTrees(Stage stage, Model model, GeneTreeViewController controller) {
		Clipboard clipboard = Clipboard.getSystemClipboard();
		try {
			String content;
			File file;
			var newickReader = new NewickReader();
			var newTreeBlock = new TreesBlock();
			if (clipboard.hasString()) {
				content = clipboard.getString();
				file = new File("temp.txt");
				FileWriter fw = new FileWriter(file);
				fw.write(content);
				fw.close();
				var iterator = new FileLineIterator(file);
				newickReader.read(new ProgressPercentage(), iterator, model.getTaxaBlock(), newTreeBlock);

			} else if (clipboard.hasFiles()) {
				file = clipboard.getFiles().get(0);
				newickReader.read(new ProgressPercentage(), file.getPath(), model.getTaxaBlock(), newTreeBlock);
			}
			else return null;

			for (PhyloTree phyloTree : newTreeBlock.getTrees()) {
				stabilizer.apply(phyloTree);
			}
			return pasteTrees(newTreeBlock.getTrees(), model, controller, stage);
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}

	private ArrayList<GeneTree> pasteTrees(ObservableList<PhyloTree> phyloTrees, Model model,
										   GeneTreeViewController controller, Stage stage) {
		ArrayList<GeneTree> pastedGeneTrees = new ArrayList<>(phyloTrees.size());
		for (PhyloTree phyloTree : phyloTrees) {
			int treeId = model.getGeneTreeSet().addTree(phyloTree);
			if (treeId == -1) continue;
			pastedGeneTrees.add(model.getGeneTreeSet().getGeneTree(treeId));
			int index = model.getGeneTreeSet().getPosition(treeId);

			if (colorBar == null)
				initializeColorBar(controller.getvBox(), model.getGeneTreeSet(), controller.getSlider());
			else {
				colorBar.addColorBox(phyloTree.getName(), treeId, index, null, null);
			}

			treeId2edgeSelectionModel.put(treeId, new SelectionModelSet<>());
			TreeSheet treeSheet = new TreeSheet(phyloTree, treeId, treeWidth, treeHeight, treeDiagramType,
					model.getTaxaBlock(), taxaSelectionModel, treeId2edgeSelectionModel.get(treeId));
			trees.getChildren().add(index, treeSheet);
			currentLayout.initializeNode(treeSheet, index);
			id2treeSheet.put(treeId, treeSheet);
			treeSnapshots.getChildren().add(index, new Rectangle());
			setupTreeSelectionAndSnapshots(treeSheet, treeId);
		}
		if (model.getGeneTreeSet().getAvailableFeatures().size() > 0) {
			for (var tree : pastedGeneTrees) {
				for (var feature : model.getGeneTreeSet().getAvailableFeatures()) {
					tree.addFeature(feature, null);
					colorBar.getId2colorBarBox().get(tree.getId()).addToTooltipOrReplace(feature, "NaN");
				}
			}
			var featureParser = new FeatureParserForPastedTrees(stage, model.getGeneTreeSet().getAvailableFeatures(), pastedGeneTrees.size());
			featureParser.getParsedProperty().addListener((InvalidationListener) -> {
				var parsedFeatures = featureParser.getParsedFeatureValues();
				for (int i = 0; i < pastedGeneTrees.size(); i++) {
					for (var featureName : parsedFeatures.keySet()) {
						var value = parsedFeatures.get(featureName).get(i);
						pastedGeneTrees.get(i).addFeature(featureName, value);
						colorBar.getId2colorBarBox().get(pastedGeneTrees.get(i).getId()).addToTooltipOrReplace(featureName, value);
					}
				}
				if (controller.getFeatureColoringSubMenu().getItems().contains((MenuItem)controller.getColoringGroup().getSelectedToggle()))
					updateColoring(controller.getColoringGroup(), controller.getColoringGroup().getSelectedToggle(),
							controller.getColoringGroup().getSelectedToggle(), controller, model, stage);
			});
		}
		treesCount.set(model.getGeneTreeSet().size());
		taxaCount.set(model.getTaxaBlock().getNtax());
		initializeTaxaList(controller.getTaxonOrderSubMenu(), controller.getOrderGroup(), model.getTaxaBlock());
		initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
				controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
		updateColoring(controller.getColoringGroup(), controller.getColoringGroup().getSelectedToggle(),
				controller.getColoringGroup().getSelectedToggle(), controller, model, stage);
		updateInfoLabel(controller.getLabel());
		return pastedGeneTrees;
	}

	private void pasteTrees(ArrayList<GeneTree> geneTrees, Model model, GeneTreeViewController controller) {
		TreeMap<Integer,GeneTree> position2geneTree = new TreeMap<>();
		for (var geneTree : geneTrees) position2geneTree.put(geneTree.getPosition(), geneTree);
		for (int position : position2geneTree.keySet()) {
			GeneTree geneTree = position2geneTree.get(position);
			boolean addedSuccessfully = model.getGeneTreeSet().addTree(geneTree);
			if (!addedSuccessfully) continue;
			int treeId = geneTree.getId();
			int index = model.getGeneTreeSet().getPosition(treeId);

			if (colorBar == null)
				initializeColorBar(controller.getvBox(), model.getGeneTreeSet(), controller.getSlider());
			else {
				colorBar.addColorBox(geneTree.getGeneName(), treeId, index, geneTree.getColor(), geneTree.getFurtherFeatures());
			}

			treeId2edgeSelectionModel.put(treeId, new SelectionModelSet<>());
			TreeSheet treeSheet = new TreeSheet(geneTree.getPhyloTree(), treeId, treeWidth, treeHeight, treeDiagramType,
					model.getTaxaBlock(), taxaSelectionModel, treeId2edgeSelectionModel.get(treeId));
			trees.getChildren().add(index, treeSheet);
			currentLayout.initializeNode(treeSheet, index);
			id2treeSheet.put(treeId, treeSheet);
			treeSnapshots.getChildren().add(index, new Rectangle());
			setupTreeSelectionAndSnapshots(treeSheet, treeId);
		}
		treesCount.set(model.getGeneTreeSet().size());
		taxaCount.set(model.getTaxaBlock().getNtax());
		initializeTaxaList(controller.getTaxonOrderSubMenu(), controller.getOrderGroup(), model.getTaxaBlock());
		initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
				controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
		updateInfoLabel(controller.getLabel());
		if (currentLayout.getType().equals(LayoutType.Carousel) & (treesCount.get() >= 50 || geneTrees.size() > 1 || geneTrees.get(0).getPosition() != treesCount.get()-1)) {
			((CarouselLayout)currentLayout).initializeLayout();
		}
		else if (currentLayout.getType().equals(LayoutType.Stack) & (geneTrees.size() > 1 || geneTrees.get(0).getPosition() != treesCount.get()-1))
			currentLayout.updatePosition(controller.getSlider().getValue(), controller.getSlider().getValue());
	}

	private void removeTree(GeneTree geneTree, Model model, GeneTreeViewController controller) {
		if (model.getGeneTreeSet().containsGeneTree(geneTree)) {
			int removedId = geneTree.getId();
			int removedPosition = geneTree.getPosition();
			geneTree.setColor(colorBar.getId2colorBarBox().get(removedId).getColor());
			trees.getChildren().remove(removedPosition);
			treeSelectionModel.setSelected(removedId,false);
			treeId2edgeSelectionModel.remove(removedId);
			treeSnapshots.getChildren().remove(removedPosition);
			model.getGeneTreeSet().removeTree(removedId);
			treesCount.set(treesCount.get()-1);
			// TODO: If taxa are unique in the removed tree, they should be removed from the TaxaBlock
			colorBar.removeColorBox(removedId);
			id2treeSheet.remove(removedId);
			initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
					controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
			updateInfoLabel(controller.getLabel());
			if (currentLayout.getType().equals(LayoutType.Carousel) & (treesCount.get() >= 50 | removedPosition != treesCount.get())) {
				((CarouselLayout)currentLayout).initializeLayout();
			}
			else if (currentLayout.getType().equals(LayoutType.Stack) & removedPosition != treesCount.get())
				currentLayout.updatePosition(controller.getSlider().getValue(), controller.getSlider().getValue());
		}
	}

	private Node createSnapshot(Node treeVis) {
		Bounds bounds = treeVis.getLayoutBounds();
		WritableImage writableImage = new WritableImage((int)Math.ceil(bounds.getWidth()*2),
				(int)Math.ceil(bounds.getHeight()*2)); // scaling with factor 2 for better resolution
		SnapshotParameters parameters = new SnapshotParameters();
		parameters.setFill(Color.TRANSPARENT);
		parameters.setTransform(javafx.scene.transform.Transform.scale(2,2));
		Image image = treeVis.snapshot(parameters,writableImage);
		ImageView snapShot = new ImageView(image);
		snapShot.setFitWidth(bounds.getWidth());
		snapShot.setFitHeight(bounds.getHeight());
		return snapShot;
	}

	private void updateSnapshot(int treeIndex) {
		if (treeIndex == -1) return;
		Node treeSheet = trees.getChildren().get(treeIndex);
		currentLayout.resetNode(treeSheet); // reset rotation, transformations and scaling for the snapshot

		Node snapShot = createSnapshot(treeSheet);

		// Transformation of treeSheet and snapshot according to the current layout and slider position
		currentLayout.initializeNode(snapShot, treeIndex);
		currentLayout.initializeNode(treeSheet, treeIndex);

		// Add / Replace snapshot in Group treeSnapshots
		if (treeSnapshots.getChildren().size()==trees.getChildren().size()) treeSnapshots.getChildren().remove(treeIndex);
		treeSnapshots.getChildren().add(treeIndex,snapShot);
	}

	private void updateLayout(Toggle selectedLayoutToggle, GeneTreeViewController controller) {
		if (selectedLayoutToggle.equals(controller.getStackMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new StackLayout(trees.getChildren(),
					treeSnapshots.getChildren(), treeWidth, treeHeight, controller.getCenterPane().widthProperty(),
					controller.getCenterPane().heightProperty(), camera, controller.getSlider(), controller.getZoomSlider());
		}
		else if (selectedLayoutToggle.equals(controller.getCarouselMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new CarouselLayout(trees.getChildren(), treeWidth,
					treeHeight, controller.getCenterPane().widthProperty(), controller.getCenterPane().heightProperty(),
					camera, controller.getSlider(), controller.getZoomSlider());
		}
		else System.out.println("No layout");
		controller.getSlider().setValue(controller.getSlider().getValue());
	}

	private void updateTreeLayout(Model model, Toggle selectedTreeLayoutToggle, GeneTreeViewController controller,
								  SubScene subScene) {
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
		if (model.getTreesBlock().getNTrees()>0) initializeTreesLayout(model, controller, subScene);
	}

	private void updateColoring(ToggleGroup coloringGroup, Toggle oldSelection, Toggle newSelection,
								GeneTreeViewController controller, Model model, Stage stage) {
		if (colorBar == null) return;
		if (newSelection == controller.getNoColoringMenuItem()) {
			colorBar.resetColoring();
		}
		else if (newSelection == controller.getMonophyleticColoringMenuItem()) {
			colorBar.resetColoring();
			for (int treeId : model.getGeneTreeSet().getTreeOrder()) {
				TreeSheet treeSheet = id2treeSheet.get(treeId);
				boolean monophyletic = treeSheet.monophyleticSelection();
				if (monophyletic) colorBar.setColor(treeId, Color.KHAKI);
			}
		}
		else if (controller.getSimilarityColoringSubMenu().getItems().contains((MenuItem)newSelection)) {
			String selectedTreeName = ((MenuItem) newSelection).getText();

			var selectionDialog = new SimilarityCalculationDialog(stage, selectedTreeName);
			selectionDialog.doneProperty().addListener((InvalidationListener) -> {
				String finalTreeName = selectionDialog.getFinalSelectedName();
				var getTreeSimilaritiesService = setUpSimilarityCalculation(finalTreeName,
						coloringGroup, colorGroupListener, oldSelection, model, controller);
				if (getTreeSimilaritiesService == null) return;
				getTreeSimilaritiesService.setOnSucceeded(v -> {
					System.out.println("Similarity calculation succeeded");
					LinkedHashMap<Integer,Integer> treeId2similarities = getTreeSimilaritiesService.getValue();
					setColors(treeId2similarities, model, coloringGroup, colorGroupListener, oldSelection, FeatureType.NUMERICAL);
					controller.getProgressLabel().setText("");
				});
				getTreeSimilaritiesService.restart();
			});
		}
		else if (controller.getFeatureColoringSubMenu().getItems().contains((MenuItem) newSelection)) {
			String featureName = ((MenuItem) newSelection).getText();
			HashMap<Integer,String> id2featureValues = model.getGeneTreeSet().getFeatureValues(featureName);
			FeatureType featureType = model.getGeneTreeSet().getFeatureType(featureName);
			HashMap<Integer,Double> finalValues = new HashMap<>();
			if (featureType == FeatureType.NUMERICAL) {
				try {
					for (var id : id2featureValues.keySet()) {
						if (Objects.equals(id2featureValues.get(id), "NaN")) finalValues.put(id, null);
						else finalValues.put(id, Double.parseDouble(id2featureValues.get(id)));
					}
				} catch (Exception e) {
					return;
				}
			}
			else if (featureType == FeatureType.CATEGORICAL) {
				HashMap<String,Double> value2category = new HashMap<>();
				double highestCategory = 0;
				for (int id : id2featureValues.keySet()) {
					String value = id2featureValues.get(id);
					double category;
					if (value2category.containsKey(value)) {
						category = value2category.get(value);
					}
					else if (value.equals("NaN")) {
						category = -1.;
						value2category.put(value, category);
					}
					else {
						category = highestCategory;
						value2category.put(value, category);
						highestCategory++;
					}
					finalValues.put(id, category);
				}
			}
			setColors(finalValues, model, coloringGroup, colorGroupListener, oldSelection, featureType);
		}
	}

	private void setColors(HashMap<Integer,? extends Number> treeId2FeatureValue, Model model, ToggleGroup toggleGroup,
						   ChangeListener<Toggle> toggleChangeListener, Toggle oldSelection, FeatureType featureType) {
		if (treeId2FeatureValue.size() == model.getGeneTreeSet().size()) {
			colorBar.resetColoring();
			ObservableList<Color> colors;
			if (featureType == FeatureType.NUMERICAL) {
				colors = ColorSchemeManager.getInstance().getColorScheme("White-Green");
				TreeSet<? extends Number> sorted = new TreeSet<>(treeId2FeatureValue.values());
				var max = sorted.last().doubleValue();
				var min = sorted.first().doubleValue();
				var range = max - min;
				if (range == 0) range = 1;
				for (int treeId : model.getGeneTreeSet().keySet()) {
					Color color;
					if (treeId2FeatureValue.get(treeId) == null) color = colorBar.getBackgroundColor();
					else {
						double relativeValue = (treeId2FeatureValue.get(treeId).doubleValue() - min) / range;
						color = colors.get((int) ((colors.size()-1)*relativeValue));
					}
					colorBar.setColor(treeId, color);
				}
			}
			else if (featureType == FeatureType.CATEGORICAL) {
				colors = ColorSchemeManager.getInstance().getColorScheme("Glasbey29");
				//System.out.println("Available colors: " + colors.size());
				for (int treeId : model.getGeneTreeSet().keySet()) {
					int category = (int) Math.round((double) treeId2FeatureValue.get(treeId));
					while (category > colors.size()-1) category -= colors.size();
					Color color;
					if (category == -1) color = colorBar.getBackgroundColor();
					else color = colors.get(category);
					colorBar.setColor(treeId, color);
				}
			}
			//for (String colorSchemeName : ColorSchemeManager.getInstance().getNames()) System.out.println(colorSchemeName);
		}
		else {
			toggleGroup.selectedToggleProperty().removeListener(toggleChangeListener);
			toggleGroup.selectToggle(oldSelection);
			toggleGroup.selectedToggleProperty().addListener(toggleChangeListener);
		}
	};

	private void changeTreeOrder(Model model, ToggleGroup orderGroup, Toggle oldSelection, Toggle newSelection,
								 GeneTreeViewController controller, SubScene subScene, Stage stage) throws IOException {
		if (newSelection == controller.getDefaultOrderMenuItem()) {
			model.getGeneTreeSet().resetTreeOrder();
			initializeTreesLayout(model, controller, subScene);
			colorBar.reorder(model.getGeneTreeSet().getTreeOrder());
			initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
					controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
		}
		else if (controller.getSimilarityOrderSubMenu().getItems().contains((MenuItem) newSelection)) {
			String selectedTreeName = ((MenuItem) newSelection).getText();

			var selectionDialog = new SimilarityCalculationDialog(stage, selectedTreeName);
			selectionDialog.doneProperty().addListener((InvalidationListener) -> {
				String finalTreeName = selectionDialog.getFinalSelectedName();
				Service<LinkedHashMap<Integer,Integer>> getTreeSimilaritiesService = setUpSimilarityCalculation(finalTreeName,
						orderGroup, orderGroupListener, oldSelection, model, controller);
				if (getTreeSimilaritiesService == null) return;
				getTreeSimilaritiesService.setOnSucceeded(v -> {
					System.out.println("Similarity calculation succeeded");
					LinkedHashMap<Integer,Integer> similarities = getTreeSimilaritiesService.getValue();
					TreeMap<Double,Integer> orderedTreeIds = new TreeMap<>(Collections.reverseOrder());
					for (int treeId : similarities.keySet()) {
						double similarity = (double) similarities.get(treeId);
						while (orderedTreeIds.containsKey(similarity)) similarity -= 0.0000000000001;
						orderedTreeIds.put(similarity, treeId);
					}
					updateTreeOrder(model, controller, subScene, orderedTreeIds);
					controller.getProgressLabel().setText("");
				});
				getTreeSimilaritiesService.restart();
			});
		}
		else if (controller.getTaxonOrderSubMenu().getItems().contains((MenuItem) newSelection)){
			String taxonName = ((String) orderGroup.getSelectedToggle().getUserData()).replace(" ","+");
			if (taxonName.contains("_")) {
				String[] taxonNames = taxonName.split("_");
				taxonName = taxonNames[0]+"+"+taxonNames[1];
			}
			System.out.println(taxonName);
			var geneOrderDialog = new GeneOrderDialog(stage,taxonName);
			geneOrderDialog.doneProperty().addListener((InvalidationListener) -> {
				String finalTaxonName = geneOrderDialog.getFinalSelectedName();
				if (finalTaxonName == null) {
					orderGroup.selectedToggleProperty().removeListener(orderGroupListener);
					orderGroup.selectToggle(oldSelection);
					orderGroup.selectedToggleProperty().addListener(orderGroupListener);
					return;
				}
				System.out.println(finalTaxonName);
				Service<TreeMap<Double,Integer>> getGeneOrderService = new Service<>() {
					@Override
					protected Task<TreeMap<Double,Integer>> createTask() {
						return new GetGeneOrderTask(model, finalTaxonName);
					}
				};
				controller.getProgressBar().visibleProperty().bind(getGeneOrderService.runningProperty());
				controller.getProgressBar().progressProperty().bind(getGeneOrderService.progressProperty());
				getGeneOrderService.setOnScheduled(v -> {
					controller.getProgressLabel().setText("Reordering trees ...");
				});
				getGeneOrderService.setOnSucceeded(v -> {
					System.out.println("Reordering succeeded");
					TreeMap<Double,Integer> orderedTreeIds = getGeneOrderService.getValue();
					updateTreeOrder(model, controller, subScene, orderedTreeIds);
					controller.getProgressLabel().setText("");
				});
				getGeneOrderService.setOnFailed(u -> {
					System.out.println("Reordering trees failed");
					controller.getProgressLabel().setText("Reordering trees failed");
					orderGroup.selectedToggleProperty().removeListener(orderGroupListener);
					orderGroup.selectToggle(oldSelection);
					orderGroup.selectedToggleProperty().addListener(orderGroupListener);
				});
				getGeneOrderService.restart();
			});
		}
		else if (controller.getFeatureOrderSubMenu().getItems().contains((MenuItem) newSelection)) {
			String selectedFeature = ((MenuItem) newSelection).getText();
			var featureType = model.getGeneTreeSet().getFeatureType(selectedFeature);
			var id2featureValues = model.getGeneTreeSet().getFeatureValues(selectedFeature);
			TreeMap<Double,Integer> orderedTreeIds = new TreeMap<>();
			// TODO: trees without a value for this feature should go to the end? (currently sorting not possible with missing values)
			if (featureType == FeatureType.NUMERICAL) {
				for (int treeId : model.getGeneTreeSet().keySet()) {
					double value;
					//if (id2featureValues.get(treeId) == null) value = ;
					value = Double.parseDouble(id2featureValues.get(treeId));
					while (orderedTreeIds.containsKey(value)) value += 0.0000000000001;
					orderedTreeIds.put(value, treeId);
				}
			}
			else if (featureType == FeatureType.CATEGORICAL) {
				double highestCategory = 1;
				HashMap<String,Double> value2category = new HashMap<>();
				for (int treeId : model.getGeneTreeSet().keySet()) {
					String value = id2featureValues.get(treeId);
					double category;
					if (value2category.containsKey(value)) {
						category = value2category.get(value);
					}
					else {
						category = highestCategory;
						value2category.put(value, category);
						highestCategory++;
					}
					while (orderedTreeIds.containsKey(category)) category += 0.0000000000001;
					orderedTreeIds.put(category, treeId);
				}
			}
			System.out.println(orderedTreeIds.size());
			updateTreeOrder(model, controller, subScene, orderedTreeIds);
		}
	}

	private void updateTreeOrder(Model model, GeneTreeViewController controller, SubScene subScene,
								 TreeMap<Double, Integer> orderedTreeIds) {
		if (orderedTreeIds.size() == model.getGeneTreeSet().size()) {
			model.getGeneTreeSet().setTreeOrder(orderedTreeIds);
			initializeTreesLayout(model, controller, subScene);
			colorBar.reorder(model.getGeneTreeSet().getTreeOrder());
			initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
					controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getGeneTreeSet().getOrderedGeneNames());
		}
	}

	private Service<LinkedHashMap<Integer,Integer>> setUpSimilarityCalculation(String finalTreeName, ToggleGroup toggleGroup,
																			   ChangeListener<Toggle> listener, Toggle oldSelection,
																			   Model model, GeneTreeViewController controller) {
		if (finalTreeName == null | model.getGeneTreeSet().getPhyloTree(finalTreeName) == null) {
			toggleGroup.selectedToggleProperty().removeListener(listener);
			toggleGroup.selectToggle(oldSelection);
			toggleGroup.selectedToggleProperty().addListener(listener);
			return null;
		}

		PhyloTree selectedTree = model.getGeneTreeSet().getPhyloTree(finalTreeName);

		Service<LinkedHashMap<Integer,Integer>> getTreeSimilaritiesService = new Service<>() {
			@Override
			protected Task<LinkedHashMap<Integer,Integer>> createTask() {
				return new SimilarityCalculationTask(model.getGeneTreeSet().getGeneTrees(), selectedTree);
			}
		};
		controller.getProgressBar().visibleProperty().bind(getTreeSimilaritiesService.runningProperty());
		controller.getProgressBar().progressProperty().bind(getTreeSimilaritiesService.progressProperty());
		getTreeSimilaritiesService.setOnScheduled(v -> {
			controller.getProgressLabel().setText("Calculating tree similarities ...");
		});
		getTreeSimilaritiesService.setOnFailed(u -> {
			System.out.println("Tree similarity calculation failed");
			controller.getProgressLabel().setText("Tree similarity calculation failed");
			toggleGroup.selectedToggleProperty().removeListener(listener);
			toggleGroup.selectToggle(oldSelection);
			toggleGroup.selectedToggleProperty().addListener(listener);
		});
		return getTreeSimilaritiesService;
	}

	public void updateButtonTooltips(double sliderValue, ObservableList<String> orderedGeneNames) {
		int index = (int)Math.round(sliderValue);
		currentTreeToolTip.setText(orderedGeneNames.get(index-1));
		if (index < orderedGeneNames.size()) {
			nextTreeToolTip.setText(orderedGeneNames.get(index));
		}
		else nextTreeToolTip.setText(currentTreeToolTip.getText());
		if (index>1) {
			previousTreeToolTip.setText(orderedGeneNames.get(index-2));
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

	private void updateInfoLabel(Label label) {
		label.setText("Trees: %,d, Taxa: %,d".formatted(treesCount.get(), taxaCount.get()));
	}
}
