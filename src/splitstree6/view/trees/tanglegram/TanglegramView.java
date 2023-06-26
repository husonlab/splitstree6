/*
 * TanglegramView.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.PrintUtils;
import jloda.fx.util.ProgramProperties;
import jloda.phylo.PhyloTree;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;
import splitstree6.view.format.taxmark.TaxonMark;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

/**
 * tanglegram view
 * Daniel Huson, 12.2021
 */
public class TanglegramView implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final TanglegramViewController controller;
	private final TanglegramViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);
	private final BooleanProperty reticulated = new SimpleBooleanProperty(this, "reticulated", false);

	private final IntegerProperty optionTree1 = new SimpleIntegerProperty(this, "optionTree1", 1); // 1-based
	private final ObjectProperty<TreeDiagramType> optionDiagram1 = new SimpleObjectProperty<>(this, "optionDiagram1", TreeDiagramType.RectangularPhylogram);
	private final ObjectProperty<HeightAndAngles.Averaging> optionAveraging1 = new SimpleObjectProperty<>(this, "optionAveraging1");

	private final IntegerProperty optionTree2 = new SimpleIntegerProperty(this, "optionTree2", 2); // 1-based
	private final ObjectProperty<TreeDiagramType> optionDiagram2 = new SimpleObjectProperty<>(this, "optionDiagram2", TreeDiagramType.RectangularPhylogram);
	private final ObjectProperty<HeightAndAngles.Averaging> optionAveraging2 = new SimpleObjectProperty<>(this, "optionAveraging2");

	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation", LayoutOrientation.Rotate0Deg);

	private final BooleanProperty optionShowTreeNames = new SimpleBooleanProperty(this, "optionShowTreeNames");
	private final BooleanProperty optionShowTreeInfo = new SimpleBooleanProperty(this, "optionShowTreeInfo");

	private final BooleanProperty optionShowInternalLabels = new SimpleBooleanProperty(this, "optionShowInternalLabels");

	private final DoubleProperty optionHorizontalZoomFactor = new SimpleDoubleProperty(this, "optionHorizontalZoomFactor", 1.0);

	private final DoubleProperty optionVerticalZoomFactor = new SimpleDoubleProperty(this, "optionVerticalZoomFactor", 1.0);

	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	{
		ProgramProperties.track(optionAveraging1, HeightAndAngles.Averaging::valueOf, HeightAndAngles.Averaging.ChildAverage);
		ProgramProperties.track(optionAveraging2, HeightAndAngles.Averaging::valueOf, HeightAndAngles.Averaging.ChildAverage);
		ProgramProperties.track(optionShowTreeNames, true);
		ProgramProperties.track(optionShowTreeInfo, true);
		ProgramProperties.track(optionShowInternalLabels, true);
	}

	public List<String> listOptions() {
		return List.of(optionTree1.getName(), optionDiagram1.getName(), optionAveraging1.getName(),
				optionTree2.getName(), optionDiagram2.getName(), optionAveraging2.getName(), optionOrientation.getName(),
				optionHorizontalZoomFactor.getName(), optionVerticalZoomFactor.getName(), optionFontScaleFactor.getName(),
				optionShowTreeNames.getName(), optionShowTreeInfo.getName(), optionShowInternalLabels.getName());
	}

	public TanglegramView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);

		getTrees().addListener((InvalidationListener) e -> {
			var t1 = Math.min(getOptionTree1(), getTrees().size());
			var t2 = Math.min(getOptionTree2(), getTrees().size());
			if (t1 == t2) {
				if (t2 < getTrees().size())
					t2++;
				else if (t1 > 1)
					t1--;
			}
			setOptionTree1(t1);
			setOptionTree2(t2);
		});

		var loader = new ExtendedFXMLLoader<TanglegramViewController>(TanglegramViewController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>();
		presenter = new TanglegramViewPresenter(mainWindow, this, targetBounds, getTrees());

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});
		setViewTab(viewTab);

		empty.bind(Bindings.isEmpty(getTrees()));

		controller.getFormatVBox().getChildren().addAll(new TaxonLabelFormat(mainWindow, undoManager), new TaxonMark(mainWindow, undoManager));

		viewTab.getAlgorithmBreadCrumbsToolBar().getInfoLabel().textProperty().bind(Bindings.createStringBinding(() -> "taxa: %,d  trees: %,d".formatted(mainWindow.getWorkingTaxa().getNtax(), trees.size()), mainWindow.workingTaxaProperty(), trees));
	}

	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}

	public UndoManager getUndoManager() {
		return undoManager;
	}

	public TanglegramViewController getController() {
		return controller;
	}

	public TanglegramViewPresenter getPresenter() {
		return presenter;
	}

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
	}

	public String getName() {
		return name.get();
	}

	public StringProperty nameProperty() {
		return name;
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

	public boolean isEmpty() {
		return empty.get();
	}

	public BooleanProperty emptyProperty() {
		return empty;
	}

	@Override
	public Node getMainNode() {
		return controller.getInnerAnchorPane();
	}

	public TreeDiagramType getOptionDiagram1() {
		return optionDiagram1.get();
	}

	public ObjectProperty<TreeDiagramType> optionDiagram1Property() {
		return optionDiagram1;
	}

	public HeightAndAngles.Averaging getOptionAveraging1() {
		return optionAveraging1.get();
	}

	public ObjectProperty<HeightAndAngles.Averaging> optionAveraging1Property() {
		return optionAveraging1;
	}

	public void setOptionAveraging1(HeightAndAngles.Averaging optionAveraging1) {
		this.optionAveraging1.set(optionAveraging1);
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

	public TreeDiagramType getOptionDiagram2() {
		return optionDiagram2.get();
	}

	public ObjectProperty<TreeDiagramType> optionDiagram2Property() {
		return optionDiagram2;
	}

	public HeightAndAngles.Averaging getOptionAveraging2() {
		return optionAveraging2.get();
	}

	public ObjectProperty<HeightAndAngles.Averaging> optionAveraging2Property() {
		return optionAveraging2;
	}

	public void setOptionAveraging2(HeightAndAngles.Averaging optionAveraging2) {
		this.optionAveraging2.set(optionAveraging2);
	}

	public int getOptionTree1() {
		return optionTree1.get();
	}

	public IntegerProperty optionTree1Property() {
		return optionTree1;
	}

	public void setOptionTree1(int optionTree1) {
		this.optionTree1.set(optionTree1);
	}

	public int getOptionTree2() {
		return optionTree2.get();
	}

	public IntegerProperty optionTree2Property() {
		return optionTree2;
	}

	public void setOptionTree2(int optionTree2) {
		this.optionTree2.set(optionTree2);
	}

	public BooleanProperty optionShowTreeNamesProperty() {
		return optionShowTreeNames;
	}

	public boolean isOptionShowTreeNames() {
		return optionShowTreeNames.get();
	}

	public void setOptionShowTreeNames(boolean optionShowTreeNames) {
		this.optionShowTreeNames.set(optionShowTreeNames);
	}

	public boolean isOptionShowTreeInfo() {
		return optionShowTreeInfo.get();
	}

	public BooleanProperty optionShowTreeInfoProperty() {
		return optionShowTreeInfo;
	}

	public void setOptionShowTreeInfo(boolean optionShowTreeInfo) {
		this.optionShowTreeInfo.set(optionShowTreeInfo);
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

	public boolean isOptionShowInternalLabels() {
		return optionShowInternalLabels.get();
	}

	public BooleanProperty optionShowInternalLabelsProperty() {
		return optionShowInternalLabels;
	}

	public void setOptionShowInternalLabels(boolean optionShowInternalLabels) {
		this.optionShowInternalLabels.set(optionShowInternalLabels);
	}

	@Override
	public void clear() {
		getTrees().clear();
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
	public int size() {
		return (controller.getTree1CBox().getValue() != null ? 1 : 0) + (controller.getTree2CBox().getValue() != null ? 1 : 0);
	}

	@Override
	public String getCitation() {
		return "Scornavacca et al, 2011; " +
			   "Celine Scornavacca, Franziska Zickmann and Daniel H. Huson. Tanglegrams for rooted phylogenetic trees and networks. " +
			   "Bioinformatics, 27(13):i248–i256, 2011";
	}
}
