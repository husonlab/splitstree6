/*
 * DensiTreeView.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.phylo.PhyloTree;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.densitree.EdgesFormat;
import splitstree6.view.format.selecttraits.SelectTraits;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;
import splitstree6.view.format.taxmark.TaxonMark;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

public class DensiTreeView implements IView {
	public static final Color DEFAULT_LIGHTMODE_EDGE_COLOR = Color.BLACK.deriveColor(1, 1, 1, 0.05);
	public static final Color DEFAULT_DARKMODE_EDGE_COLOR = Color.WHITE.deriveColor(1, 1, 1, 0.05);
	public static final Color DEFAULT_OTHER_COLOR = Color.DARKRED.deriveColor(1, 1, 1, 0.05);
	public static final double DEFAULT_STROKE_WIDTH = 0.5;

	private static boolean startup = true;

	private final UndoManager undoManager = new UndoManager();

	private final DensiTreeViewController controller;
	private final DensiTreeViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty reticulated = new SimpleBooleanProperty(this, "reticulated", false);

	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final ObjectProperty<DensiTreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram");

	private final ObjectProperty<HeightAndAngles.Averaging> optionAveraging = new SimpleObjectProperty<>(this, "optionAveraging");

	private final BooleanProperty optionRerootAndRescale = new SimpleBooleanProperty(this, "optionRerootAndRescale");

	private final BooleanProperty optionShowTrees = new SimpleBooleanProperty(this, "optionShowTrees", true);
	private final BooleanProperty optionHideFirst10PercentTrees = new SimpleBooleanProperty(this, "optionHideFirst10PercentTrees", true);

	private final BooleanProperty optionShowConsensus = new SimpleBooleanProperty(this, "optionShowConsensus", true);

	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation");
	private final DoubleProperty optionHorizontalZoomFactor = new SimpleDoubleProperty(this, "optionHorizontalZoomFactor", 1.0 / 1.2);
	private final DoubleProperty optionVerticalZoomFactor = new SimpleDoubleProperty(this, "optionVerticalZoomFactor", 1.0 / 1.2);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final BooleanProperty optionJitter = new SimpleBooleanProperty(this, "optionJitter", false);
	private final BooleanProperty optionColorIncompatibleEdges = new SimpleBooleanProperty(this, "optionColorIncompatibleEdges", true);

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	private final DoubleProperty optionStrokeWidth = new SimpleDoubleProperty(this, "optionStrokeWidth");
	private final ObjectProperty<Color> optionEdgeColor = new SimpleObjectProperty<>(this, "optionEdgeColor");
	private final ObjectProperty<Color> optionOtherColor = new SimpleObjectProperty<>(this, "optionOtherColor");

	{
		ProgramProperties.track(optionDiagram, DensiTreeDiagramType::valueOf, DensiTreeDiagramType.TriangularPhylogram);
		ProgramProperties.track(optionOrientation, LayoutOrientation::valueOf, LayoutOrientation.Rotate0Deg);
		ProgramProperties.track(optionAveraging, HeightAndAngles.Averaging::valueOf, HeightAndAngles.Averaging.ChildAverage);
		ProgramProperties.track(optionRerootAndRescale, false);
		ProgramProperties.track(optionShowTrees, true);
		ProgramProperties.track(optionHideFirst10PercentTrees, true);
		ProgramProperties.track(optionShowConsensus, true);
		ProgramProperties.track(optionJitter, false);
		ProgramProperties.track(optionColorIncompatibleEdges, false);
		if (startup) {
			startup = false;
			optionDiagram.set(DensiTreeDiagramType.TriangularPhylogram);
			optionOrientation.set(LayoutOrientation.Rotate0Deg);
			optionAveraging.set(HeightAndAngles.Averaging.ChildAverage);
			optionRerootAndRescale.set(false);
			optionShowTrees.set(true);
			optionHideFirst10PercentTrees.set(true);
			optionShowConsensus.set(true);
			optionJitter.set(false);
			optionColorIncompatibleEdges.set(true);
		}

		ProgramProperties.track(optionStrokeWidth, DEFAULT_STROKE_WIDTH);
		ProgramProperties.track(optionEdgeColor, MainWindowManager.isUseDarkTheme() ? DEFAULT_DARKMODE_EDGE_COLOR : DEFAULT_LIGHTMODE_EDGE_COLOR);
		ProgramProperties.track(optionOtherColor, DEFAULT_DARKMODE_EDGE_COLOR);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionOrientation.getName(), optionAveraging.getName(), optionRerootAndRescale.getName(), optionShowTrees.getName(), optionHideFirst10PercentTrees.getName(), optionShowConsensus.getName(), optionOrientation.getName(),
				optionHorizontalZoomFactor.getName(), optionVerticalZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionJitter.getName(), optionColorIncompatibleEdges.getName());
	}

	public DensiTreeView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<DensiTreeViewController>(DensiTreeViewController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		presenter = new DensiTreeViewPresenter(mainWindow, this, targetBounds);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null) {
				targetBounds.bind(Bindings.createObjectBinding(() -> new BoundingBox(n.getLayoutBounds().getMinX(), n.getLayoutBounds().getMinY(), n.getLayoutBounds().getWidth(), n.getLayoutBounds().getHeight() - 80), n.layoutBoundsProperty()));
			}
		});

		setViewTab(viewTab);

		var taxLabelFormatter = new TaxonLabelFormat(mainWindow, undoManager);

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter, new TaxonMark(mainWindow, undoManager), new SelectTraits(mainWindow), new EdgesFormat(this));

		trees.addListener((InvalidationListener) e -> {
			empty.set(trees.isEmpty());
		});

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));

		optionDiagram.addListener(e -> mainWindow.setDirty(true));
		optionOrientation.addListener(e -> mainWindow.setDirty(true));
		optionAveraging.addListener(e -> mainWindow.setDirty(true));

		// one of the two should always be selected:
		optionShowTrees.addListener((v, o, n) -> {
			if (!n && !optionShowConsensus.get())
				Platform.runLater(() -> optionShowConsensus.set(true));
		});
		optionShowConsensus.addListener((v, o, n) -> {
			if (!n && !optionShowTrees.get())
				Platform.runLater(() -> optionShowTrees.set(true));
		});
		optionHideFirst10PercentTrees.addListener((v, o, n) -> {
			if (n)
				Platform.runLater(() -> optionShowTrees.set(true));
		});

		viewTab.getAlgorithmBreadCrumbsToolBar().getInfoLabel().textProperty().bind(Bindings.createStringBinding(() -> "taxa: %,d  trees: %,d".formatted(mainWindow.getWorkingTaxa().getNtax(), trees.size()), mainWindow.workingTaxaProperty(), trees));
	}

	@Override
	public void clear() {
	}

	@Override
	public String getName() {
		return name.get();
	}

	@Override
	public javafx.scene.Node getRoot() {
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
		return getTrees().size();
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
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getCitation() {
		return "Bouckaert 2010; Remco R. Bouckaert. DensiTree: making sense of sets of phylogenetic trees, Bioinformatics 26(1):1372–1373 (2010).";
	}

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
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

	public DensiTreeDiagramType getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<DensiTreeDiagramType> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(DensiTreeDiagramType optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}


	public HeightAndAngles.Averaging getOptionAveraging() {
		return optionAveraging.get();
	}

	public ObjectProperty<HeightAndAngles.Averaging> optionAveragingProperty() {
		return optionAveraging;
	}

	public void setOptionAveraging(HeightAndAngles.Averaging optionAveraging) {
		this.optionAveraging.set(optionAveraging);
	}

	public boolean isOptionRerootAndRescale() {
		return optionRerootAndRescale.get();
	}

	public BooleanProperty optionRerootAndRescaleProperty() {
		return optionRerootAndRescale;
	}

	public void setOptionRerootAndRescale(boolean optionRerootAndRescale) {
		this.optionRerootAndRescale.set(optionRerootAndRescale);
	}

	public boolean isOptionShowTrees() {
		return optionShowTrees.get();
	}

	public BooleanProperty optionShowTreesProperty() {
		return optionShowTrees;
	}

	public void setOptionShowTrees(boolean optionShowTrees) {
		this.optionShowTrees.set(optionShowTrees);
	}

	public boolean isOptionHideFirst10PercentTrees() {
		return optionHideFirst10PercentTrees.get();
	}

	public BooleanProperty optionHideFirst10PercentTreesProperty() {
		return optionHideFirst10PercentTrees;
	}

	public void setOptionHideFirst10PercentTrees(boolean optionHideFirst10PercentTrees) {
		this.optionHideFirst10PercentTrees.set(optionHideFirst10PercentTrees);
	}

	public boolean isOptionShowConsensus() {
		return optionShowConsensus.get();
	}

	public BooleanProperty optionShowConsensusProperty() {
		return optionShowConsensus;
	}

	public void setOptionShowConsensus(boolean optionShowConsensus) {
		this.optionShowConsensus.set(optionShowConsensus);
	}

	public double getOptionHorizontalZoomFactor() {
		return optionHorizontalZoomFactor.get();
	}

	public DoubleProperty optionHorizontalZoomFactorProperty() {
		return optionHorizontalZoomFactor;
	}

	public void setOptionHorizontalZoomFactor(double optionHorizontalZoomFactor) {
		this.optionHorizontalZoomFactor.set(optionHorizontalZoomFactor);
	}

	public double getOptionVerticalZoomFactor() {
		return optionVerticalZoomFactor.get();
	}

	public DoubleProperty optionVerticalZoomFactorProperty() {
		return optionVerticalZoomFactor;
	}

	public void setOptionVerticalZoomFactor(double optionVerticalZoomFactor) {
		this.optionVerticalZoomFactor.set(optionVerticalZoomFactor);
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

	public Bounds getTargetBounds() {
		return targetBounds.get();
	}

	public ObjectProperty<Bounds> targetBoundsProperty() {
		return targetBounds;
	}

	public DensiTreeViewController getController() {
		return controller;
	}

	public ObservableList<PhyloTree> getTrees() {
		return trees;
	}

	public boolean isReticulated() {
		return reticulated.get();
	}

	public BooleanProperty reticulatedProperty() {
		return reticulated;
	}

	public void setReticulated(boolean reticulated) {
		this.reticulated.set(reticulated);
	}


	public boolean isOptionJitter() {
		return optionJitter.get();
	}

	public BooleanProperty optionJitterProperty() {
		return optionJitter;
	}

	public boolean getOptionColorIncompatibleEdges() {
		return optionColorIncompatibleEdges.get();
	}

	public BooleanProperty optionColorIncompatibleEdgesProperty() {
		return optionColorIncompatibleEdges;
	}

	public void setOptionColorIncompatibleEdges(boolean optionColorIncompatibleEdges) {
		this.optionColorIncompatibleEdges.set(optionColorIncompatibleEdges);
	}

	public double getOptionStrokeWidth() {
		return optionStrokeWidth.get();
	}

	public DoubleProperty optionStrokeWidthProperty() {
		return optionStrokeWidth;
	}

	public void setOptionStrokeWidth(double optionStrokeWidth) {
		this.optionStrokeWidth.set(optionStrokeWidth);
	}

	public Color getOptionEdgeColor() {
		return optionEdgeColor.get();
	}

	public ObjectProperty<Color> optionEdgeColorProperty() {
		return optionEdgeColor;
	}

	public void setOptionEdgeColor(Color optionEdgeColor) {
		this.optionEdgeColor.set(optionEdgeColor);
	}

	public Color getOptionOtherColor() {
		return optionOtherColor.get();
	}

	public ObjectProperty<Color> optionOtherColorProperty() {
		return optionOtherColor;
	}

	public void setOptionOtherColor(Color optionOtherColor) {
		this.optionOtherColor.set(optionOtherColor);
	}
}