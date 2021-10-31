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

package splitstree6.algorithms.trees.trees2sink;

import javafx.application.Platform;
import javafx.beans.property.*;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SinkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.viewers.multitreesviewer.MultiTreesViewer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * multiple tree display setup algorithm
 * Daniel Huson, 10.2021
 */
public class MultiTreeDisplay extends Trees2Sink {
	public enum Diagram {Unrooted, Circular, Rectangular, Triangular}

	public enum RootSide {Left, Right, Bottom, Top}

	private final ObjectProperty<Diagram> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", Diagram.Unrooted);
	private final ObjectProperty<RootSide> optionRootSide = new SimpleObjectProperty<>(this, "optionRootSide", RootSide.Left);

	private final StringProperty optionGrid = new SimpleStringProperty(this, "optionGrid", "1 x 1");
	private final IntegerProperty optionPageNumber = new SimpleIntegerProperty(this, "optionPageNumber", 1);

	private final ObjectProperty<MultiTreesViewer> viewer = new SimpleObjectProperty<>();

	@Override
	public List<String> listOptions() {
		return Arrays.asList(optionDiagram.getName(), optionRootSide.getName(), optionGrid.getName(), optionPageNumber.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, SinkBlock outputData) throws IOException {

		Platform.runLater(() -> {
			if (viewer.get() == null) {
				var mainWindow = getNode().getOwner().getMainWindow();
				var multiTreesViewer = new MultiTreesViewer(mainWindow);

				multiTreesViewer.optionDiagramProperty().bindBidirectional(optionDiagram);
				multiTreesViewer.optionRootSideProperty().bindBidirectional(optionRootSide);

				multiTreesViewer.optionGridProperty().bindBidirectional(optionGrid);
				multiTreesViewer.pageNumberProperty().bindBidirectional(optionPageNumber);

				mainWindow.addTabToMainTabPane(multiTreesViewer);
				viewer.set(multiTreesViewer);
			}
			var multiTreesViewer = viewer.get();
			multiTreesViewer.getTrees().clear();
			multiTreesViewer.getTrees().addAll(inputData.getTrees());
		});
	}

	public Diagram getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<Diagram> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(Diagram optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public RootSide getOptionRootSide() {
		return optionRootSide.get();
	}

	public ObjectProperty<RootSide> optionRootSideProperty() {
		return optionRootSide;
	}

	public void setOptionRootSide(RootSide optionRootSide) {
		this.optionRootSide.set(optionRootSide);
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
