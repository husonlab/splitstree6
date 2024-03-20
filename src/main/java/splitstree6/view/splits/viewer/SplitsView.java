/*
 * SplitsView.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.FuzzyBoolean;
import jloda.fx.util.ProgramProperties;
import splitstree6.data.SplitsBlock;
import splitstree6.layout.splits.LabelSplitsBy;
import splitstree6.layout.splits.LoopView;
import splitstree6.layout.splits.SplitsDiagramType;
import splitstree6.layout.splits.SplitsRooting;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.PaneLabel;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.selecttraits.SelectTraits;
import splitstree6.view.format.splits.SplitsFormat;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;
import splitstree6.view.format.taxmark.TaxonMark;
import splitstree6.view.format.traits.TraitsFormat;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.List;

public class SplitsView implements IView {
	public static final Color OUTLINE_FILL_COLOR = Color.DARKGRAY;

	private final UndoManager undoManager = new UndoManager();

	private final SelectionModel<Integer> splitSelectionModel = new SetSelectionModel<>();

	private final SplitsViewController controller;
	private final SplitsViewPresenter presenter;

	private final SplitsFormat splitsFormatter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObjectProperty<SplitsBlock> splitsBlock = new SimpleObjectProperty<>(this, "splitsBlock");
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final ObjectProperty<SplitsDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", SplitsDiagramType.Splits);
	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation", LayoutOrientation.Rotate0Deg);

	private final ObjectProperty<SplitsRooting> optionRooting = new SimpleObjectProperty<>(this, "optionRooting", SplitsRooting.None);
	private final DoubleProperty optionRootAngle = new SimpleDoubleProperty(this, "optionRootAngle");

	private final DoubleProperty optionZoomFactor = new SimpleDoubleProperty(this, "optionZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final ObjectProperty<PaneLabel> optionPaneLabel = new SimpleObjectProperty<>(this, "optionTreeLabels");

	private final ObjectProperty<Color> optionOutlineFill = new SimpleObjectProperty<>(this, "optionOutlineFill");

	private final ObjectProperty<LabelSplitsBy> optionLabelSplitsBy = new SimpleObjectProperty<>(this, "optionLabelSplitsBy", LabelSplitsBy.None);

	private final ObjectProperty<String[]> optionActiveTraits = new SimpleObjectProperty<>(this, "optionActiveTraits", new String[0]);
	private final ObjectProperty<FuzzyBoolean> optionTraitLegend = new SimpleObjectProperty<FuzzyBoolean>(this, "optionTraitLegend", FuzzyBoolean.False);
	private final IntegerProperty optionTraitSize = new SimpleIntegerProperty(this, "optionTraitSize", 64);

	private final ObjectProperty<String[]> optionEdits = new SimpleObjectProperty<>(this, "optionEdits", new String[0]);

	private final BooleanProperty optionShowQRCode = new SimpleBooleanProperty(this, "optionShowQRCode");

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	// setup properties:
	{
		ProgramProperties.track(optionDiagram, SplitsDiagramType::valueOf, SplitsDiagramType.Splits);
		ProgramProperties.track(optionRootAngle, 160.0);
		ProgramProperties.track(optionOutlineFill, OUTLINE_FILL_COLOR);
		ProgramProperties.track(optionPaneLabel, PaneLabel::valueOf, PaneLabel.ScaleBarNone);
		ProgramProperties.track(optionShowQRCode, false);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionOrientation.getName(), optionRooting.getName(), optionZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionRootAngle.getName(), optionOutlineFill.getName(), optionEdits.getName(),
				optionActiveTraits.getName(), optionTraitLegend.getName(), optionTraitSize.getName(), optionPaneLabel.getName(),
				optionLabelSplitsBy.getName(), optionShowQRCode.getName());
	}

	public SplitsView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<SplitsViewController>(SplitsViewController.class);
		controller = loader.getController();

		// BasicFX.reportChanges(optionDiagram);

		final ObservableMap<Integer, RichTextLabel> taxonLabelMap = FXCollections.observableHashMap();
		final ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeShapeMap = FXCollections.observableHashMap();
		final ObservableMap<Integer, ArrayList<Shape>> splitShapeMap = FXCollections.observableHashMap();
		final ObservableList<LoopView> loopViews = FXCollections.observableArrayList();

		presenter = new SplitsViewPresenter(mainWindow, this, targetBounds, splitsBlock, taxonLabelMap, nodeShapeMap, splitShapeMap, loopViews);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});

		setViewTab(viewTab);

		splitsFormatter = new SplitsFormat(undoManager, splitSelectionModel, nodeShapeMap, splitShapeMap, optionDiagram, optionOutlineFill, optionEditsProperty());
		optionLabelSplitsBy.bindBidirectional(splitsFormatter.optionLabelSplitsByProperty());

		splitsBlock.addListener((v, o, n) -> {
			splitsFormatter.getPresenter().updateMenus(n);
			if (optionLabelSplitsBy.get() == LabelSplitsBy.None && n != null && n.hasConfidenceValues()) {
				optionLabelSplitsBy.set(LabelSplitsBy.Confidence);
			}
		});

		var traitsFormatter = new TraitsFormat(mainWindow, undoManager);
		traitsFormatter.setNodeShapeMap(nodeShapeMap);

		traitsFormatter.optionActiveTraitsProperty().bindBidirectional(optionActiveTraits);
		traitsFormatter.optionTraitLegendProperty().bindBidirectional(optionTraitLegend);
		traitsFormatter.optionTraitSizeProperty().bindBidirectional(optionTraitSize);
		traitsFormatter.getLegend().scaleProperty().bind(optionZoomFactorProperty());
		traitsFormatter.setRunAfterUpdateNodes(presenter::updateLabelLayout);
		presenter.updateCounterProperty().addListener(e -> traitsFormatter.updateNodes());

		controller.getFormatVBox().getChildren().addAll(new TaxonLabelFormat(mainWindow, undoManager), new TaxonMark(mainWindow, undoManager), traitsFormatter, new SelectTraits(mainWindow), splitsFormatter);

		AnchorPane.setLeftAnchor(traitsFormatter.getLegend(), 5.0);
		AnchorPane.setTopAnchor(traitsFormatter.getLegend(), 35.0);
		controller.getInnerAnchorPane().getChildren().add(traitsFormatter.getLegend());
		DraggableLabel.makeDraggable(traitsFormatter.getLegend());

		viewTab.emptyProperty().bind(empty);
		viewTabProperty().addListener((v, o, n) -> {
			if (o != null)
				o.emptyProperty().unbind();
			if (n != null)
				n.emptyProperty().bind(empty);
		});

		splitsBlock.addListener((v, o, n) -> {
			empty.set(n == null || n.size() == 0);
			splitSelectionModel.clearSelection();
		});

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));
		optionRootingProperty().addListener(e -> mainWindow.setDirty(true));
		optionRootAngleProperty().addListener(e -> mainWindow.setDirty(true));
		optionDiagramProperty().addListener(e -> mainWindow.setDirty(true));
		optionOutlineFillProperty().addListener(e -> mainWindow.setDirty(true));

		optionDiagramProperty().addListener(e -> mainWindow.updateMethodsTab());

		viewTab.getAlgorithmBreadCrumbsToolBar().getInfoLabel().textProperty().bind(Bindings.createStringBinding(() -> "n: %,d s: %,d".formatted(mainWindow.getWorkingTaxa().getNtax(), getSplitsBlock() == null ? 0 : getSplitsBlock().size()), mainWindow.workingTaxaProperty(), splitsBlockProperty()));
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

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
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
	public Node getMainNode() {
		return controller.getInnerAnchorPane();
	}

	@Override
	public SplitsViewPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getCitation() {
		return null;
	}

	public PaneLabel getOptionPaneLabel() {
		return optionPaneLabel.get();
	}

	public ObjectProperty<PaneLabel> optionPaneLabelProperty() {
		return optionPaneLabel;
	}

	public void setOptionPaneLabel(PaneLabel optionPaneLabel) {
		this.optionPaneLabel.set(optionPaneLabel);
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

	public ObjectProperty<String[]> optionActiveTraitsProperty() {
		return optionActiveTraits;
	}

	public boolean isOptionShowQRCode() {
		return optionShowQRCode.get();
	}

	public BooleanProperty optionShowQRCodeProperty() {
		return optionShowQRCode;
	}

	public LabelSplitsBy getOptionLabelSplitsBy() {
		return optionLabelSplitsBy.get();
	}

	public ObjectProperty<LabelSplitsBy> optionLabelSplitsByProperty() {
		return optionLabelSplitsBy;
	}

	public void setOptionLabelSplitsBy(LabelSplitsBy optionLabelSplitsBy) {
		this.optionLabelSplitsBy.set(optionLabelSplitsBy);
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

	public SplitsFormat getSplitsFormatter() {
		return splitsFormatter;
	}
}
