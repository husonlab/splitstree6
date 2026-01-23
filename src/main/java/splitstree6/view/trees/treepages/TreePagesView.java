/*
 *  TreePagesView.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.layout.Averaging;
import splitstree6.layout.tree.PaneLabel;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.edgelabel.EdgeLabelFormat;
import splitstree6.view.format.edgelabel.LabelEdgesBy;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;
import splitstree6.view.format.taxmark.TaxonMark;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

import static splitstree6.main.SplitsTree6.setMinWidthHeightToZero;

public class TreePagesView implements IView {

	private final UndoManager undoManager = new UndoManager();

	private final TreePagesViewController controller;
	private final TreePagesViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);
	private final BooleanProperty reticulated = new SimpleBooleanProperty(this, "reticulated", false);

	private final IntegerProperty optionRows = new SimpleIntegerProperty(this, "optionRows", ProgramProperties.get("TreePagesRows", 1));
	private final IntegerProperty optionCols = new SimpleIntegerProperty(this, "optionCols", ProgramProperties.get("TreePagesCols", 1));

	private final ObjectProperty<TreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", TreeDiagramType.RectangularPhylogram);
	private final ObjectProperty<Averaging> optionAveraging = new SimpleObjectProperty<>(this, "optionAveraging");
	private final StringProperty optionOrientation = new SimpleStringProperty(this, "optionOrientation", "Rotate0Deg");

	private final IntegerProperty pageNumber = new SimpleIntegerProperty(this, "pageNumber", 1); // 1-based

	private final ObjectProperty<PaneLabel> optionTreeLabels = new SimpleObjectProperty<>(this, "optionTreeLabels");

	private final DoubleProperty optionZoomFactor = new SimpleDoubleProperty(this, "optionZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	private final ObjectProperty<LabelEdgesBy> optionLabelEdgesBy = new SimpleObjectProperty<>(this, "optionLabelEdgesBy", LabelEdgesBy.None);

	{
		ProgramProperties.track(optionDiagram, TreeDiagramType::valueOf, TreeDiagramType.RectangularPhylogram);
		ProgramProperties.track(optionLabelEdgesBy, LabelEdgesBy::valueOf, LabelEdgesBy.None);
		ProgramProperties.track(optionRows, 2);
		ProgramProperties.track(optionCols, 3);
		ProgramProperties.track(optionAveraging, Averaging::valueOf, Averaging.ChildAverage);
		ProgramProperties.track(optionTreeLabels, PaneLabel::valueOf, PaneLabel.Name);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionOrientation.getName(), optionRows.getName(), optionCols.getName(),
				pageNumber.getName(), optionZoomFactor.getName(), optionFontScaleFactor.getName(),
				optionTreeLabels.getName(), optionLabelEdgesBy.getName());
	}

	/**
	 * constructor
	 */
	public TreePagesView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<TreePagesViewController>(TreePagesViewController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		presenter = new TreePagesViewPresenter(mainWindow, this, targetBounds, getTrees());

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});

		setViewTab(viewTab);

		viewTab.emptyProperty().bind(empty);
		viewTabProperty().addListener((v, o, n) -> {
			if (o != null)
				o.emptyProperty().unbind();
			if (n != null)
				n.emptyProperty().bind(empty);
		});
		empty.bind(Bindings.isEmpty(getTrees()));

		var taxLabelFormatter = new TaxonLabelFormat(mainWindow, undoManager);

		var edgeLabelFormat = new EdgeLabelFormat(undoManager);
		edgeLabelFormat.optionLabelEdgesByProperty().bindBidirectional(optionLabelEdgesBy);

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter, new TaxonMark(mainWindow, undoManager),
				new Separator(Orientation.HORIZONTAL), edgeLabelFormat);

		viewTab.getAlgorithmBreadCrumbsToolBar().getInfoLabel().textProperty().bind(Bindings.createStringBinding(() -> {
					if (mainWindow.getWorkingTaxa() == null)
						return "";
					else return "n: %,d  trees: %,d".formatted(mainWindow.getWorkingTaxa().getNtax(), trees.size());
				}
				, mainWindow.workingTaxaProperty(), trees));

		if (setMinWidthHeightToZero) {
			for (var region : BasicFX.getAllRecursively(loader.getRoot(), Region.class)) {
				region.setMinWidth(0);
				region.setMinHeight(0);
			}
		}
	}

	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}

	public TreePagesViewController getController() {
		return controller;
	}

	public TreePagesViewPresenter getPresenter() {
		return presenter;
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

	public int getOptionRows() {
		return optionRows.get();
	}

	public IntegerProperty optionRowsProperty() {
		return optionRows;
	}

	public void setOptionRows(int optionRows) {
		this.optionRows.set(Math.max(1, optionRows));
	}

	public int getOptionCols() {
		return optionCols.get();
	}

	public IntegerProperty optionColsProperty() {
		return optionCols;
	}


	public void setOptionCols(int optionCols) {
		this.optionCols.set(Math.max(1, optionCols));
	}

	public int getPageNumber() {
		return pageNumber.get();
	}

	public IntegerProperty pageNumberProperty() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber.set(pageNumber);
	}

	public TreeDiagramType getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<TreeDiagramType> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(TreeDiagramType optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public Averaging getOptionAveraging() {
		return optionAveraging.get();
	}

	public ObjectProperty<Averaging> optionAveragingProperty() {
		return optionAveraging;
	}

	public void setOptionAveraging(Averaging optionAveraging) {
		this.optionAveraging.set(optionAveraging);
	}

	public String getOptionOrientation() {
		return optionOrientation.get();
	}

	public StringProperty optionOrientationProperty() {
		return optionOrientation;
	}

	public void setOptionOrientation(String optionOrientation) {
		this.optionOrientation.set(optionOrientation);
	}

	public Pane getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public void clear() {
		controller.getPagination().pageCountProperty().unbind();
		getTrees().clear();
		controller.getPagination().setPageCount(0);
	}

	@Override
	public String getName() {
		return name.get();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();
	}

	@Override
	public int size() {
		return trees.size();
	}

	public boolean getEmpty() {
		return empty.get();
	}

	public BooleanProperty emptyProperty() {
		return empty;
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

	public PaneLabel getOptionTreeLabels() {
		return optionTreeLabels.get();
	}

	public ObjectProperty<PaneLabel> optionTreeLabelsProperty() {
		return optionTreeLabels;
	}

	public void setOptionTreeLabels(PaneLabel optionPaneLabel) {
		this.optionTreeLabels.set(optionPaneLabel);
	}

	public LabelEdgesBy getOptionLabelEdgesBy() {
		return optionLabelEdgesBy.get();
	}

	public ObjectProperty<LabelEdgesBy> optionLabelEdgesByProperty() {
		return optionLabelEdgesBy;
	}

	public void setOptionLabelEdgesBy(LabelEdgesBy optionLabelEdgesBy) {
		this.optionLabelEdgesBy.set(optionLabelEdgesBy);
	}

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public Node getMainNode() {
		return controller.getPagination();
	}

	@Override
	public String getCitation() {
		return null;
	}
}
