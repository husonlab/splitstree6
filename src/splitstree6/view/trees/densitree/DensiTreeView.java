/*
 * DensiTreeView.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.PrintUtils;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.selecttraits.SelectTraits;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;
import splitstree6.view.format.taxmark.TaxonMark;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

public class DensiTreeView implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final DensiTreeViewController controller;
	private final DensiTreePresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty reticulated = new SimpleBooleanProperty(this, "reticulated", false);

	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final ObjectProperty<DensiTreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", DensiTreeDiagramType.TriangularPhylogram);
	private final BooleanProperty optionShowConsensus = new SimpleBooleanProperty(this, "optionShowConsensus", true);

	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation", LayoutOrientation.Rotate0Deg);
	private final DoubleProperty optionHorizontalZoomFactor = new SimpleDoubleProperty(this, "optionHorizontalZoomFactor", 1.0 / 1.2);
	private final DoubleProperty optionVerticalZoomFactor = new SimpleDoubleProperty(this, "optionVerticalZoomFactor", 1.0 / 1.2);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final BooleanProperty optionJitter = new SimpleBooleanProperty(this, "optionJitter", true);
	private final BooleanProperty optionAntiConsensus = new SimpleBooleanProperty(this, "optionAntiConsensus", false);
	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");


	{
		ProgramProperties.track(optionDiagram, DensiTreeDiagramType::valueOf, DensiTreeDiagramType.TriangularPhylogram);
		ProgramProperties.track(optionShowConsensus, true);
		ProgramProperties.track(optionJitter, true);
		ProgramProperties.track(optionAntiConsensus, false);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionShowConsensus.getName(), optionOrientation.getName(),
				optionHorizontalZoomFactor.getName(), optionVerticalZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionJitter.getName(), optionAntiConsensus.getName());
	}

	public DensiTreeView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<DensiTreeViewController>(DensiTreeViewController.class);
		controller = loader.getController();


		// this is the target area for the tree page:
		presenter = new DensiTreePresenter(mainWindow, this, targetBounds);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null) {
				targetBounds.bind(Bindings.createObjectBinding(() -> new BoundingBox(n.getLayoutBounds().getMinX(), n.getLayoutBounds().getMinY(), n.getLayoutBounds().getWidth(), n.getLayoutBounds().getHeight() - 80), n.layoutBoundsProperty()));
			}
		});

		setViewTab(viewTab);

		var taxLabelFormatter = new TaxonLabelFormat(mainWindow, undoManager);

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter, new TaxonMark(mainWindow, undoManager), new SelectTraits(mainWindow));

		trees.addListener((InvalidationListener) e -> {
			empty.set(trees.size() == 0);
		});

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));
		optionDiagramProperty().addListener(e -> mainWindow.setDirty(true));

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
	public javafx.scene.Node getImageNode() {
		return PrintUtils.createImage(controller.getInnerAnchorPane(), null);
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

	public boolean isOptionAntiConsensus() {
		return optionAntiConsensus.get();
	}

	public BooleanProperty optionAntiConsensusProperty() {
		return optionAntiConsensus;
	}

	public void setOptionAntiConsensus(boolean optionAntiConsensus) {
		this.optionAntiConsensus.set(optionAntiConsensus);
	}
}