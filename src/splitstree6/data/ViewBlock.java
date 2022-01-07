/*
 *  ViewBlock.java Copyright (C) 2021 Daniel H. Huson
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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.fx.window.NotificationManager;
import jloda.util.StringUtils;
import splitstree6.options.OptionIO;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.IView;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.Workflow;

import java.io.IOException;


public class ViewBlock extends DataBlock {
	private final StringProperty inputBlockName = new SimpleStringProperty();
	private final StringProperty initializationLines = new SimpleStringProperty("");

	private InvalidationListener invalidationListener;

	private ViewTab viewTab;

	@Override
	public void setNode(DataNode node) {
		super.setNode(node);
		if (node.getOwner() != null) {
			var mainWindow = ((Workflow) node.getOwner()).getMainWindow();
			if (viewTab == null)
				Platform.runLater(() -> viewTab = new ViewTab(mainWindow, node, false));

			invalidationListener = e -> {
				if (node.getParents().size() == 0) { // have removed the node from the workflow
					if (viewTab != null)
						mainWindow.removeTabFromMainTabPane(viewTab);
				} else { // have added the node to the workflow, e.g. after undo delete
					var view = (viewTab != null ? viewTab.getView() : null);
					if (view != null) {
						if (viewTab != null)
							mainWindow.removeTabFromMainTabPane(viewTab);
						viewTab = new ViewTab(mainWindow, node, false);
						mainWindow.addTabToMainTabPane(viewTab);
						viewTab.setView(view);
					}
				}
			};
			node.getParents().addListener(new WeakInvalidationListener(invalidationListener));
		}
	}

	public ViewTab getViewTab() {
		return viewTab;
	}

	@Override
	public int size() {
		return getView() == null ? 0 : getView().size();
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public ViewBlock newInstance() {
		return (ViewBlock) super.newInstance();
	}

	public static final String BLOCK_NAME = "VIEW";

	@Override
	public void updateShortDescription() {
		setShortDescription("a " + StringUtils.fromCamelCase(getName()) + " visualization");
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
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

	public IView getView() {
		return viewTab == null ? null : viewTab.getView();
	}

	public ReadOnlyObjectProperty<IView> viewProperty() {
		return viewTab.viewProperty();
	}

	@Override
	public void clear() {
		if (getView() != null)
			getView().clear();
	}

	public void setView(IView view) {
		viewTab.setView(view);
		setName(view.getName());
		updateShortDescription();
		if (getNode() != null)
			getNode().setTitle(getName());

		// process an option lines that may have been provided
		try {
			OptionIO.parseOptions(initializationLinesProperty(), view);
		} catch (IOException e) {
			NotificationManager.showError("Error parsing options");
		} finally {
			initializationLinesProperty().set("");
		}
	}

	public String getInitializationLines() {
		return initializationLines.get();
	}

	public StringProperty initializationLinesProperty() {
		return initializationLines;
	}

	public void setInitializationLines(String initializationLines) {
		this.initializationLines.set(initializationLines);
	}

	public String getCitation() {
		return getView() == null ? null : getView().getCitation();
	}
}
