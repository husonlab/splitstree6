/*
 *  SplitsView.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.beans.property.*;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.util.ProgramProperties;
import splitstree6.data.SplitsBlock;
import splitstree6.data.parts.Compatibility;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.IView;
import splitstree6.view.trees.treepages.LayoutOrientation;
import splitstree6.window.MainWindow;

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

	private final BooleanProperty optionUseWeights = new SimpleBooleanProperty(this, "optionUseWeights");

	private final DoubleProperty optionZoomFactor = new SimpleDoubleProperty(this, "optionZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	// setup properties:
	{
		ProgramProperties.track(optionDiagram, SplitsDiagramType::valueOf, SplitsDiagramType.Outline);
		ProgramProperties.track(optionOrientation, LayoutOrientation::valueOf, LayoutOrientation.Rotate0Deg);
		ProgramProperties.track(optionRooting, SplitsRooting::valueOf, SplitsRooting.None);
		ProgramProperties.track(optionUseWeights, true);
		ProgramProperties.track(optionRootAngle, 160.0);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionOrientation.getName(), optionRooting.getName(), optionUseWeights.getName(), optionZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionRootAngle.getName());
	}

	public SplitsView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<SplitsViewController>(SplitsViewController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		presenter = new SplitsViewPresenter(mainWindow, this, targetBounds, splitsBlock);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});

		setViewTab(viewTab);

		splitsBlock.addListener((v, o, n) -> {
			empty.set(n == null || n.size() == 0);
			if (n != null && getOptionDiagram() == SplitsDiagramType.Outline && n.getCompatibility() != Compatibility.compatible && n.getCompatibility() != Compatibility.cyclic)
				setOptionDiagram(SplitsDiagramType.Splits);
			splitSelectionModel.clearSelection();
		});
	}

	@Override
	public void clear() {
		getSplitsBlock().clear();
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
		return null;
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

	public SplitsRooting getOptionRooting() {
		return optionRooting.get();
	}

	public ObjectProperty<SplitsRooting> optionRootingProperty() {
		return optionRooting;
	}

	public void setOptionRooting(SplitsRooting optionRooting) {
		this.optionRooting.set(optionRooting);
	}

	public double getOptionRootAngle() {
		return optionRootAngle.get();
	}

	public DoubleProperty optionRootAngleProperty() {
		return optionRootAngle;
	}

	public void setOptionRootAngle(double optionRootAngle) {
		this.optionRootAngle.set(optionRootAngle);
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

	public boolean isOptionUseWeights() {
		return optionUseWeights.get();
	}

	public BooleanProperty optionUseWeightsProperty() {
		return optionUseWeights;
	}

	public void setOptionUseWeights(boolean optionUseWeights) {
		this.optionUseWeights.set(optionUseWeights);
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