/*
 * SplitsView.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.view.splits.viewer;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.util.ProgramProperties;
import splitstree6.data.SplitsBlock;
import splitstree6.layout.splits.algorithms.EqualAngle;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.splits.SplitsFormatter;
import splitstree6.view.format.taxlabels.TaxLabelFormatter;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.List;

public class SplitsView implements IView {

	private final UndoManager undoManager = new UndoManager();

	private final SelectionModel<Integer> splitSelectionModel = new SetSelectionModel<>();

	private final SplitsViewController controller;
	private final SplitsViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObjectProperty<SplitsBlock> splitsBlock = new SimpleObjectProperty<>(this, "splitsBlock");
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final ObjectProperty<SplitsDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram");
	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation");

	private final ObjectProperty<SplitsRooting> optionRooting = new SimpleObjectProperty<>(this, "optionRooting");
	private final DoubleProperty optionRootAngle = new SimpleDoubleProperty(this, "optionRootAngle");

	private final BooleanProperty optionShowConfidence = new SimpleBooleanProperty(this, "optionShowConfidence", true);

	private final DoubleProperty optionZoomFactor = new SimpleDoubleProperty(this, "optionZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final ObjectProperty<Color> optionOutlineFill = new SimpleObjectProperty<>(this, "optionOutlineFill");

	private final ObjectProperty<String[]> optionEdits = new SimpleObjectProperty<>(this, "optionEdits", new String[0]);

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	// create properties:
	{
		ProgramProperties.track(optionDiagram, SplitsDiagramType::valueOf, SplitsDiagramType.Outline);
		ProgramProperties.track(optionOrientation, LayoutOrientation::valueOf, LayoutOrientation.Rotate0Deg);
		ProgramProperties.track(optionRooting, SplitsRooting::valueOf, SplitsRooting.None);
		ProgramProperties.track(optionRootAngle, 160.0);
		ProgramProperties.track(optionOutlineFill, Color.SILVER);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionOrientation.getName(), optionRooting.getName(), optionZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionRootAngle.getName(), optionOutlineFill.getName(), optionEdits.getName(), optionShowConfidence.getName());
	}

	public SplitsView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<SplitsViewController>(SplitsViewController.class);
		controller = loader.getController();

		final ObservableMap<jloda.graph.Node, Shape> nodeShapeMap = FXCollections.observableHashMap();
		final ObservableMap<Integer, ArrayList<Shape>> splitShapeMap = FXCollections.observableHashMap();
		final ObservableList<LoopView> loopViews = FXCollections.observableArrayList();

		// this is the target area for the tree page:
		presenter = new SplitsViewPresenter(mainWindow, this, targetBounds, splitsBlock, nodeShapeMap, splitShapeMap, loopViews);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});

		setViewTab(viewTab);

		var taxLabelFormatter = new TaxLabelFormatter(mainWindow, undoManager);

		var splitsFormatter = new SplitsFormatter(undoManager, splitSelectionModel, nodeShapeMap, splitShapeMap, optionDiagram, optionOutlineFill, optionEditsProperty());

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter, new Separator(Orientation.HORIZONTAL), splitsFormatter);

		splitsBlock.addListener((v, o, n) -> {
			empty.set(n == null || n.size() == 0);
			splitSelectionModel.clearSelection();
		});

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));
		optionRootingProperty().addListener(e -> mainWindow.setDirty(true));
		optionRootAngleProperty().addListener(e -> mainWindow.setDirty(true));
		optionDiagramProperty().addListener(e -> mainWindow.setDirty(true));
		optionOutlineFillProperty().addListener(e -> mainWindow.setDirty(true));
	}

	@Override
	public void clear() {
	}

	@Override
	public String getName() {
		return name.get();
	}

	@Override
	public Node getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();
	}

	@Override
	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}


	@Override
	public int size() {
		return getSplitsBlock() == null ? 0 : getSplitsBlock().size();
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return controller.getInnerAnchorPane();
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getCitation() {
		return EqualAngle.getCitation();
	}

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
	}

	public SplitsDiagramType getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<SplitsDiagramType> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(SplitsDiagramType optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public LayoutOrientation getOptionOrientation() {
		return optionOrientation.get();
	}

	public ObjectProperty<LayoutOrientation> optionOrientationProperty() {
		return optionOrientation;
	}

	public void setOptionOrientation(LayoutOrientation optionOrientation) {
		this.optionOrientation.set(optionOrientation);
	}


	public String[] getOptionEdits() {
		return optionEdits.get();
	}

	public ObjectProperty<String[]> optionEditsProperty() {
		return optionEdits;
	}

	public void setOptionEdits(String[] optionEdits) {
		this.optionEdits.set(optionEdits);
	}

	public SplitsRooting getOptionRooting() {
		return optionRooting.get();
	}

	public ObjectProperty<SplitsRooting> optionRootingProperty() {
		return optionRooting;
	}

	public double getOptionRootAngle() {
		return optionRootAngle.get();
	}

	public DoubleProperty optionRootAngleProperty() {
		return optionRootAngle;
	}

	public double getOptionZoomFactor() {
		return optionZoomFactor.get();
	}

	public DoubleProperty optionZoomFactorProperty() {
		return optionZoomFactor;
	}

	public void setOptionZoomFactor(double optionZoomFactor) {
		this.optionZoomFactor.set(optionZoomFactor);
	}

	public double getOptionFontScaleFactor() {
		return optionFontScaleFactor.get();
	}

	public DoubleProperty optionFontScaleFactorProperty() {
		return optionFontScaleFactor;
	}

	public void setOptionFontScaleFactor(double optionFontScaleFactor) {
		this.optionFontScaleFactor.set(optionFontScaleFactor);
	}

	public Color getOptionOutlineFill() {
		return optionOutlineFill.get();
	}

	public ObjectProperty<Color> optionOutlineFillProperty() {
		return optionOutlineFill;
	}

	public boolean isOptionShowConfidence() {
		return optionShowConfidence.get();
	}

	public BooleanProperty optionShowConfidenceProperty() {
		return optionShowConfidence;
	}

	public void setOptionShowConfidence(boolean optionShowConfidence) {
		this.optionShowConfidence.set(optionShowConfidence);
	}

	public Bounds getTargetBounds() {
		return targetBounds.get();
	}

	public ObjectProperty<Bounds> targetBoundsProperty() {
		return targetBounds;
	}

	public SplitsViewController getController() {
		return controller;
	}

	public SplitsBlock getSplitsBlock() {
		return splitsBlock.get();
	}

	public ObjectProperty<SplitsBlock> splitsBlockProperty() {
		return splitsBlock;
	}

	public void setSplitsBlock(SplitsBlock splitsBlock) {
		this.splitsBlock.set(splitsBlock);
	}

	public SelectionModel<Integer> getSplitSelectionModel() {
		return splitSelectionModel;
	}
}
