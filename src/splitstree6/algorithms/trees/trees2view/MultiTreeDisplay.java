/*
 *  MultiTreeDisplay.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2view;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import jloda.util.ProgramProperties;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.view.trees.multitree_old.MultiTreesView;
import splitstree6.view.trees.multitree_old.TreePane;

import java.io.IOException;
import java.util.List;

/**
 * multiple tree display setup algorithm
 * Daniel Huson, 10.2021
 */
public class MultiTreeDisplay extends Trees2View {

	private final ObjectProperty<TreePane.Diagram> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", TreePane.Diagram.Rectangular);
	private final ObjectProperty<TreePane.RootSide> optionRootSide = new SimpleObjectProperty<>(this, "optionRootSide", TreePane.RootSide.Left);
	private final BooleanProperty optionToScale = new SimpleBooleanProperty(this, "optionToScale", true);

	private final StringProperty optionGrid = new SimpleStringProperty(this, "optionGrid", "1 x 1");
	private final IntegerProperty optionPageNumber = new SimpleIntegerProperty(this, "optionPageNumber", 1);

	private final ObjectProperty<MultiTreesView> viewer = new SimpleObjectProperty<>();

	private InvalidationListener invalidationListener;

	@Override
	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionRootSide.getName(), optionToScale.getName(), optionGrid.getName(), optionPageNumber.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, ViewBlock outputData) throws IOException {

		Platform.runLater(() -> {
			if (viewer.get() == null) {
				var mainWindow = getNode().getOwner().getMainWindow();
				var multiTreesViewer = new MultiTreesView(mainWindow, getNode().titleProperty());

				multiTreesViewer.optionDiagramProperty().bindBidirectional(optionDiagram);

				multiTreesViewer.optionRootSideProperty().bindBidirectional(optionRootSide);

				multiTreesViewer.optionToScaleProperty().bindBidirectional(optionToScale);

				var value = ProgramProperties.get("TreeGridDimensions", optionGrid.getValue());
				multiTreesViewer.optionGridProperty().bindBidirectional(optionGrid);
				multiTreesViewer.setOptionGrid(value);
				multiTreesViewer.optionGridProperty().addListener((v, o, n) -> ProgramProperties.put("TreeGridDimensions", n));

				multiTreesViewer.pageNumberProperty().bindBidirectional(optionPageNumber);

				mainWindow.addTabToMainTabPane(multiTreesViewer);
				viewer.set(multiTreesViewer);
			}
			var multiTreesViewer = viewer.get();
			multiTreesViewer.getTrees().clear();
			multiTreesViewer.getTrees().addAll(inputData.getTrees());
		});

		if (invalidationListener == null) {
			var mainWindow = getNode().getOwner().getMainWindow();
			var workflow = mainWindow.getWorkflow();
			invalidationListener = e -> {
				Platform.runLater(() -> {
					if (!workflow.nodes().contains(getNode())) {
						mainWindow.removeTabFromMainTabPane(viewer.get());
					} else if (!mainWindow.getController().getMainTabPane().getTabs().contains(viewer.get())) {
						mainWindow.addTabToMainTabPane(viewer.get());
					}
				});
			};
			workflow.nodes().addListener(new WeakInvalidationListener(invalidationListener));
		}
	}

	public TreePane.Diagram getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<TreePane.Diagram> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(TreePane.Diagram optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public TreePane.RootSide getOptionRootSide() {
		return optionRootSide.get();
	}

	public ObjectProperty<TreePane.RootSide> optionRootSideProperty() {
		return optionRootSide;
	}

	public void setOptionRootSide(TreePane.RootSide optionRootSide) {
		this.optionRootSide.set(optionRootSide);
	}

	public boolean isOptionToScale() {
		return optionToScale.get();
	}

	public BooleanProperty optionToScaleProperty() {
		return optionToScale;
	}

	public void setOptionToScale(boolean optionToScale) {
		this.optionToScale.set(optionToScale);
	}

	public String getOptionGrid() {
		return optionGrid.get();
	}

	public StringProperty optionGridProperty() {
		return optionGrid;
	}

	public void setOptionGrid(String optionGrid) {
		this.optionGrid.set(optionGrid);
	}

	public int getOptionPageNumber() {
		return optionPageNumber.get();
	}

	public IntegerProperty optionPageNumberProperty() {
		return optionPageNumber;
	}

	public void setOptionPageNumber(int optionPageNumber) {
		this.optionPageNumber.set(optionPageNumber);
	}
}
