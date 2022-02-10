/*
 * TreePagesView.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.IView;
import splitstree6.view.format.taxlabels.TaxLabelFormatter;
import splitstree6.view.trees.layout.ComputeHeightAndAngles;
import splitstree6.view.trees.layout.TreeDiagramType;
import splitstree6.window.MainWindow;

import java.util.Arrays;
import java.util.List;

public class TreePagesView implements IView {
	public enum TreeLabels {
		None, Name, Info;

		public String label() {
			return switch (this) {
				case None -> "-";
				case Name -> "n";
				case Info -> "i";
			};
		}

		public static String[] labels() {
			return Arrays.stream(values()).map(TreeLabels::label).toArray(String[]::new);
		}

		public static TreeLabels valueOfLabel(String label) {
			return switch (label) {
				case "-" -> None;
				case "n" -> Name;
				case "i" -> Info;
				default -> None;
			};
		}
	}

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

	private final ObjectProperty<TreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram");
	private final ObjectProperty<ComputeHeightAndAngles.Averaging> optionAveraging = new SimpleObjectProperty<>(this, "optionAveraging");
	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation");

	private final IntegerProperty pageNumber = new SimpleIntegerProperty(this, "pageNumber", 1); // 1-based

	private final ObjectProperty<TreeLabels> optionTreeLabels = new SimpleObjectProperty<>(this, "optionTreeLabels");

	private final DoubleProperty optionZoomFactor = new SimpleDoubleProperty(this, "optionZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	{
		ProgramProperties.track(optionRows, 1);
		ProgramProperties.track(optionCols, 1);
		ProgramProperties.track(optionDiagram, TreeDiagramType::valueOf, TreeDiagramType.RectangularPhylogram);
		ProgramProperties.track(optionAveraging, ComputeHeightAndAngles.Averaging::valueOf, ComputeHeightAndAngles.Averaging.ChildAverage);
		ProgramProperties.track(optionOrientation, LayoutOrientation::valueOf, LayoutOrientation.Rotate0Deg);
		ProgramProperties.track(optionTreeLabels, TreeLabels::valueOf, TreeLabels.Name);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionOrientation.getName(), optionRows.getName(), optionCols.getName(),
				pageNumber.getName(), optionZoomFactor.getName(), optionFontScaleFactor.getName(),
				optionTreeLabels.getName());
	}

	/**
	 * constructor
	 *
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

		empty.bind(Bindings.isEmpty(getTrees()));

		var taxLabelFormatter = new TaxLabelFormatter(mainWindow, undoManager);

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter);
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

	public ComputeHeightAndAngles.Averaging getOptionAveraging() {
		return optionAveraging.get();
	}

	public ObjectProperty<ComputeHeightAndAngles.Averaging> optionAveragingProperty() {
		return optionAveraging;
	}

	public void setOptionAveraging(ComputeHeightAndAngles.Averaging optionAveraging) {
		this.optionAveraging.set(optionAveraging);
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

	public Pane getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public void clear() {
		getTrees().clear();
		controller.getPagination().pageCountProperty().unbind();
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

	public TreeLabels getOptionTreeLabels() {
		return optionTreeLabels.get();
	}

	public ObjectProperty<TreeLabels> optionTreeLabelsProperty() {
		return optionTreeLabels;
	}

	public void setOptionTreeLabels(TreeLabels optionTreeLabels) {
		this.optionTreeLabels.set(optionTreeLabels);
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
	public Node getImageNode() {
		return controller.getPagination();
	}

	@Override
	public String getCitation() {
		return "Huson et al 2012;D.H. Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
	}
}
