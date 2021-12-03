/*
 *  TanglegramViewPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.scene.control.SelectionMode;
import javafx.scene.paint.Color;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.Searcher;
import jloda.phylo.PhyloTree;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.trees.treepages.ComboBoxUtils;
import splitstree6.view.trees.treepages.TreePane;
import splitstree6.window.MainWindow;

import java.util.function.Function;

import static splitstree6.view.trees.treepages.ComputeTreeEmbedding.Diagram.*;
import static splitstree6.view.trees.treepages.TreePane.Orientation.*;

/**
 * tanglegram view presenter
 * Daniel Huson, 12.2021
 */
public class TanglegramViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TanglegramView tanglegramView;
	private final TanglegramViewController controller;

	private final FindToolBar findToolBar;

	private final ObjectProperty<Dimension2D> boxDimensions = new SimpleObjectProperty<>(new Dimension2D(0, 0));

	public TanglegramViewPresenter(MainWindow mainWindow, TanglegramView tanglegramView, ObjectProperty<Bounds> targetBounds, ObservableList<PhyloTree> trees) {
		this.mainWindow = mainWindow;
		this.tanglegramView = tanglegramView;

		controller = tanglegramView.getController();

		var tree1 = new SimpleObjectProperty<PhyloTree>();
		tree1.addListener((v, o, n) -> controller.getTree1CBox().setValue(tree1.get()));
		tree1.bind(Bindings.createObjectBinding(() -> tanglegramView.getOptionTree1() >= 1 && tanglegramView.getOptionTree1() <= trees.size() ? trees.get(tanglegramView.getOptionTree1() - 1) : null, tanglegramView.optionTree1Property(), trees));

		var tree1Pane = new TanglegramTreePane(tanglegramView, mainWindow.getWorkflow().getWorkingTaxaBlock(), mainWindow.getTaxonSelectionModel(), tree1, boxDimensions, tanglegramView.optionDiagram1Property(), tanglegramView.optionOrientationProperty());
		controller.getLeftPane().getChildren().add(tree1Pane);

		var tree2 = new SimpleObjectProperty<PhyloTree>();
		tree2.addListener((v, o, n) -> controller.getTree2CBox().setValue(tree2.get()));
		tree2.bind(Bindings.createObjectBinding(() -> tanglegramView.getOptionTree2() >= 1 && tanglegramView.getOptionTree2() <= trees.size() ? trees.get(tanglegramView.getOptionTree2() - 1) : null, tanglegramView.optionTree2Property(), trees));

		var orientation2Property = new SimpleObjectProperty<TreePane.Orientation>();
		orientation2Property.bind(Bindings.createObjectBinding(() -> tanglegramView.getOptionOrientation() == Rotate0Deg ? FlipRotate0Deg : Rotate180Deg, tanglegramView.optionOrientationProperty()));

		var tree2Pane = new TanglegramTreePane(tanglegramView, mainWindow.getWorkflow().getWorkingTaxaBlock(), mainWindow.getTaxonSelectionModel(), tree2, boxDimensions, tanglegramView.optionDiagram2Property(), orientation2Property);
		controller.getRightPane().getChildren().add(tree2Pane);

		{
			var connectors = new Connectors(mainWindow, controller.getMiddlePane(), controller.getLeftPane(), controller.getRightPane(), new SimpleObjectProperty<>(Color.DARKGRAY), new SimpleDoubleProperty(1.0));
			tree1Pane.setRunAfterUpdate(connectors::update);
			tree2Pane.setRunAfterUpdate(connectors::update);
			tanglegramView.optionZoomFactorProperty().addListener(e -> connectors.update());
			tanglegramView.optionFontScaleFactorProperty().addListener(e -> connectors.update());
		}

		controller.getDiagram1CBox().setButtonCell(ComboBoxUtils.createDiagramComboBoxListCell());
		controller.getDiagram1CBox().setCellFactory(ComboBoxUtils.createDiagramComboxBoxCallback());
		controller.getDiagram1CBox().getItems().addAll(RectangularPhylogram, RectangularCladogram, TriangularCladogram);
		controller.getDiagram1CBox().setValue(tanglegramView.getOptionDiagram1());
		controller.getDiagram1CBox().valueProperty().addListener((v, o, n) -> tanglegramView.optionDiagram1Property().set(n));

		controller.getDiagram2CBox().setButtonCell(ComboBoxUtils.createDiagramComboBoxListCell());
		controller.getDiagram2CBox().setCellFactory(ComboBoxUtils.createDiagramComboxBoxCallback());
		controller.getDiagram2CBox().getItems().addAll(RectangularPhylogram, RectangularCladogram, TriangularCladogram);
		controller.getDiagram2CBox().setValue(tanglegramView.getOptionDiagram2());
		controller.getDiagram2CBox().valueProperty().addListener((v, o, n) -> tanglegramView.optionDiagram2Property().set(n));

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createOrientationComboBoxListCell());
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createOrientationComboBoxCallback());
		controller.getOrientationCBox().getItems().addAll(Rotate0Deg, FlipRotate180Deg);
		controller.getOrientationCBox().setValue(tanglegramView.getOptionOrientation());
		controller.getOrientationCBox().valueProperty().addListener((v, o, n) -> tanglegramView.optionOrientationProperty().set(n));

		controller.getShowTreeNamesToggleButton().selectedProperty().bindBidirectional(tanglegramView.optionShowTreeNamesProperty());

		controller.getTree1CBox().setItems(trees);
		controller.getTree1CBox().disableProperty().bind(Bindings.isEmpty(trees));
		if (tanglegramView.getOptionTree1() <= trees.size())
			controller.getTree1CBox().setValue(trees.get(tanglegramView.getOptionTree1() - 1));
		controller.getTree1CBox().valueProperty().addListener((v, o, n) -> {
			if (n != null)
				tanglegramView.optionTree1Property().set(trees.indexOf(n) + 1);
		});

		controller.getTree2CBox().setItems(trees);
		controller.getTree2CBox().disableProperty().bind(Bindings.isEmpty(trees));
		if (tanglegramView.getOptionTree2() <= trees.size())
			controller.getTree1CBox().setValue(trees.get(tanglegramView.getOptionTree2() - 1));
		controller.getTree2CBox().valueProperty().addListener((v, o, n) -> {
			if (n != null)
				tanglegramView.optionTree2Property().set(trees.indexOf(n) + 1);
		});

		trees.addListener((InvalidationListener) e -> {
			if (controller.getTree1CBox().getValue() == null) {
				if (tanglegramView.getOptionTree1() >= 1 && tanglegramView.getOptionTree1() <= trees.size())
					controller.getTree1CBox().setValue(trees.get(tanglegramView.getOptionTree1() - 1));
				else if (trees.size() >= 1)
					controller.getTree1CBox().setValue(trees.get(0));
			}
			if (controller.getTree2CBox().getValue() == null) {
				if (tanglegramView.getOptionTree2() >= 1 && tanglegramView.getOptionTree2() <= trees.size())
					controller.getTree2CBox().setValue(trees.get(tanglegramView.getOptionTree2() - 1));
				else if (trees.size() >= 2)
					controller.getTree2CBox().setValue(trees.get(1));
			}
		});

		// this assumes that the width of the middle pane never changes
		targetBounds.addListener((v, o, n) -> boxDimensions.set(new Dimension2D(0.5 * (n.getWidth() - 5) - controller.getMiddlePane().getWidth(), n.getHeight() - 200)));

		Function<Integer, Taxon> t2taxon = t -> mainWindow.getActiveTaxa().get(t);

		findToolBar = new FindToolBar(mainWindow.getStage(), new Searcher<>(mainWindow.getActiveTaxa(), t -> mainWindow.getTaxonSelectionModel().isSelected(t2taxon.apply(t)),
				(t, s) -> mainWindow.getTaxonSelectionModel().setSelected(t2taxon.apply(t), s), new SimpleObjectProperty<>(SelectionMode.MULTIPLE), t -> t2taxon.apply(t).getNameAndDisplayLabel("===="), null));
		findToolBar.setShowFindToolBar(false);

		controller.getvBox().getChildren().add(findToolBar);
		controller.getFindButton().setOnAction(e -> {
			if (findToolBar.isShowFindToolBar())
				findToolBar.setShowFindToolBar(false);
			else
				findToolBar.setShowFindToolBar(true);
		});
	}

	@Override
	public void setupMenuItems() {
		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> tanglegramView.setOptionFontScaleFactor(1.2 * tanglegramView.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(tanglegramView.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> tanglegramView.setOptionFontScaleFactor((1.0 / 1.2) * tanglegramView.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(tanglegramView.emptyProperty());

		mainWindow.getController().getZoomInMenuItem().setOnAction(e -> tanglegramView.setOptionZoomFactor(1.1 * tanglegramView.getOptionZoomFactor()));
		mainWindow.getController().getZoomInMenuItem().disableProperty().bind(tanglegramView.emptyProperty().or(tanglegramView.optionZoomFactorProperty().greaterThan(1.0 / 1.1)));
		mainWindow.getController().getZoomOutMenuItem().setOnAction(e -> tanglegramView.setOptionZoomFactor((1.0 / 1.1) * tanglegramView.getOptionZoomFactor()));
		mainWindow.getController().getZoomOutMenuItem().disableProperty().bind(tanglegramView.emptyProperty());

		mainWindow.getController().getFindMenuItem().setOnAction(controller.getFindButton().getOnAction());
		mainWindow.getController().getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainWindow.getController().getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		mainWindow.getController().getPrintMenuItem().setOnAction(controller.getPrintButton().getOnAction());
		mainWindow.getController().getPrintMenuItem().disableProperty().bind(controller.getPrintButton().disableProperty());

		mainWindow.getController().getSelectAllMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()));
		mainWindow.getController().getSelectNoneMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().clearSelection());
		mainWindow.getController().getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

	}
}
