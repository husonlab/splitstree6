/*
 * ShowTrees.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2view;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import jloda.fx.icons.MaterialIcons;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.view.trees.densitree.DensiTreeView;
import splitstree6.view.trees.tanglegram.TanglegramView;
import splitstree6.view.trees.treepages.TreePagesView;
import splitstree6.view.trees.treeview.TreeView;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

import java.io.IOException;
import java.util.List;

/**
 * trees viewer selection
 * Daniel Huson, 11.2021
 */
public class ShowTrees extends Trees2View {
	public enum ViewType {TreeView, TreePages, Tanglegram, DensiTree}

	private final ObjectProperty<ViewType> optionView = new SimpleObjectProperty<>(this, "optionView", ViewType.TreeView);
	private final ChangeListener<Boolean> validListener;

	@Override
	public List<String> listOptions() {
		return List.of(optionView.getName());
	}

	public ShowTrees() {
		super();
		validListener = (v, o, n) -> {
			if (getNode() != null && getNode().getPreferredChild() != null && ((DataNode) getNode().getPreferredChild()).getDataBlock() instanceof ViewBlock viewBlock) {
				if (viewBlock.getView() != null)
					viewBlock.getView().getRoot().setDisable(!n);
			}
		};
	}

	@Override
	public void setNode(AlgorithmNode node) {
		if (getNode() != null)
			getNode().validProperty().removeListener(validListener);
		super.setNode(node);
		if (getNode() != null) {
			getNode().validProperty().addListener(validListener);
		}
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, ViewBlock viewBlock) throws IOException {
		viewBlock.setInputBlockName(TreesBlock.BLOCK_NAME);
		Platform.runLater(() -> viewBlock.getViewTab().setGraphic(MaterialIcons.graphic("wysiwyg")));

		// if a view already is set in the tab, simply update its data, otherwise set it up and put it into the tab:
		if (viewBlock.getView() != null)
			viewBlock.getView().clear();

		switch (getOptionView()) {
			case TreePages -> {
				Platform.runLater(() -> {
					var mainWindow = getNode().getOwner().getMainWindow();
					var view = new TreePagesView(mainWindow, ViewType.TreePages.name(), viewBlock.getViewTab());
					viewBlock.setView(view);
				});

				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof TreePagesView view) {
						view.getUndoManager().clear();
						view.getTrees().setAll(inputData.getTrees());
						view.setReticulated(inputData.isReticulated());
					}
				});
			}
			case Tanglegram -> {
				Platform.runLater(() -> {
					var mainWindow = getNode().getOwner().getMainWindow();
					var view = new TanglegramView(mainWindow, ViewType.Tanglegram.name(), viewBlock.getViewTab());
					viewBlock.setView(view);
				});

				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof TanglegramView view) {
						view.getUndoManager().clear();
						view.getTrees().setAll(inputData.getTrees());
						view.setReticulated(inputData.isReticulated());
					}
				});
			}
			case TreeView -> {
				Platform.runLater(() -> {
					var mainWindow = getNode().getOwner().getMainWindow();
					viewBlock.setView(new TreeView(mainWindow, ViewType.TreeView.name(), viewBlock.getViewTab()));
				});

				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof TreeView view) {
						view.getUndoManager().clear();
						view.getTrees().setAll(inputData.getTrees());
						view.setReticulated(inputData.isReticulated());
					}
				});

			}
			case DensiTree -> {
				Platform.runLater(() -> {
					var mainWindow = getNode().getOwner().getMainWindow();
					viewBlock.setView(new DensiTreeView(mainWindow, ViewType.DensiTree.name(), viewBlock.getViewTab()));
				});

				Platform.runLater(() -> {
					if (viewBlock.getView() instanceof DensiTreeView view) {
						view.getUndoManager().clear();
						view.getTrees().setAll(inputData.getTrees());
						view.setReticulated(inputData.isReticulated());
					}
				});
			}
		}
		viewBlock.updateShortDescription();
	}

	public ViewType getOptionView() {
		return optionView.get();
	}

	public ObjectProperty<ViewType> optionViewProperty() {
		return optionView;
	}

	public void setOptionView(ViewType optionView) {
		this.optionView.set(optionView);
	}
}
