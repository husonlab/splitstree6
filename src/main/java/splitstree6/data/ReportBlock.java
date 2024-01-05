/*
 *  ReportBlock.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.data;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.fx.icons.MaterialIcons;
import jloda.util.StringUtils;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.Workflow;

/**
 * reportFairProportions block for textual output
 * Daniel Huson, 2.2023
 */
public class ReportBlock extends DataBlock {
	public static final String BLOCK_NAME = "Report";

	private final StringProperty inputBlockName = new SimpleStringProperty();
	private final ObservableList<String> lines = FXCollections.observableArrayList();

	private InvalidationListener invalidationListener;

	private ViewTab viewTab;

	private final ChangeListener<Boolean> validListener;

	{
		validListener = (v, o, n) -> {
			if (n && getView() != null)
				getView().getUndoManager().clear();
		};
	}

	public void setNode(DataNode node) {
		super.setNode(node);
		if (getNode().getOwner() != null) {
			var mainWindow = ((Workflow) getNode().getOwner()).getMainWindow();

			if (viewTab == null) {
				Platform.runLater(() -> {
					viewTab = new ViewTab(mainWindow, getNode(), false);
					var displayTextView = new DisplayTextView(mainWindow, getName(), false);
					viewTab.setView(displayTextView);
					viewTab.setGraphic(MaterialIcons.graphic("dataset"));

					displayTextView.setViewTab(getViewTab());
					displayTextView.setOptionText(StringUtils.toString(getLines(), "\n"));
					mainWindow.addTabToMainTabPane(viewTab);
				});
			}

			invalidationListener = e -> {
				if (getNode().getParents().isEmpty()) { // have removed the node from the workflow
					if (viewTab != null)
						mainWindow.removeTabFromMainTabPane(viewTab);
				} else { // have added the node to the workflow, e.g. after undo delete
					var view = (viewTab != null ? viewTab.getView() : null);
					if (view != null) {
						if (viewTab != null) {
							mainWindow.removeTabFromMainTabPane(viewTab);
						}
						viewTab = new ViewTab(mainWindow, getNode(), false);
						mainWindow.addTabToMainTabPane(viewTab);
						viewTab.setView(view);
						viewTab.setGraphic(MaterialIcons.graphic("dataset"));
					}
					setInputBlockName(getNode().getParents().get(0).getName());
				}
			};
			node.getParents().addListener(new WeakInvalidationListener(invalidationListener));
			node.validProperty().addListener(new WeakChangeListener<>(validListener));
		}
	}

	public DisplayTextView getView() {
		return viewTab == null ? null : (DisplayTextView) viewTab.getView();
	}

	public ViewTab getViewTab() {
		return viewTab;
	}


	@Override
	public int size() {
		return getLines().size();
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

	@Override
	public void updateShortDescription() {
		setShortDescription("an analysis result");
	}

	public ObservableList<String> getLines() {
		return lines;
	}

	public void addLine(String line) {
		lines.add(line);
	}

	public void setText(String text) {
		clear();
		text.lines().forEach(this::addLine);
	}

	public void clear() {
		lines.clear();
	}

	public String getInputBlockName() {
		return inputBlockName.get();
	}

	public StringProperty inputBlockNameProperty() {
		return inputBlockName;
	}

	public void setInputBlockName(String inputBlockName) {
		this.inputBlockName.set(inputBlockName);
	}
}

