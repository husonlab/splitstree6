/*
 * ViewTab.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.viewtab;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.IView;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

/**
 * tab to be shown in main tab-pane
 * Daniel Huson, 11.2021
 */
public class ViewTab extends Tab implements IDisplayTab {
	private UndoManager undoManager;
	private final MainWindow mainWindow;
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final ObjectProperty<Bounds> layoutBounds = new SimpleObjectProperty<>(new BoundingBox(0, 0, 0, 0));

	private final ObjectProperty<IView> view = new SimpleObjectProperty<>();

	private final AlgorithmBreadCrumbsToolBar algorithmBreadCrumbsToolBar;

	/**
	 * constructor
	 */
	public ViewTab(MainWindow mainWindow, DataNode dataNode, boolean closable) {
		this.mainWindow = mainWindow;
		setText("ViewTab");
		setClosable(closable);
		setOnCloseRequest(v -> mainWindow.removeTabFromMainTabPane(this));

		algorithmBreadCrumbsToolBar = dataNode == null ? null : new AlgorithmBreadCrumbsToolBar(mainWindow, dataNode);

		tabPaneProperty().addListener((v, o, n) -> {
			if (n == null)
				layoutBounds.unbind();
			else {
				layoutBounds.bind(n.layoutBoundsProperty());
			}
		});

		viewProperty().addListener((v, o, n) -> {
			if (n != null) {
				setContent(n.getRoot());
				undoManager = n.getUndoManager();
			} else {
				setContent(null);
				undoManager = null;
			}
			mainWindow.getPresenter().updateUndoRedo();
		});
		mainWindow.addTabToMainTabPane(this);
	}

	public UndoManager getUndoManager() {
		return undoManager;
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}

	public boolean isEmpty() {
		return empty.get();
	}

	public BooleanProperty emptyProperty() {
		return empty;
	}

	public void setEmpty(boolean empty) {
		this.empty.set(empty);
	}

	public Bounds getLayoutBounds() {
		return layoutBounds.get();
	}

	public ObjectProperty<Bounds> layoutBoundsProperty() {
		return layoutBounds;
	}

	public IView getView() {
		return view.get();
	}

	public ObjectProperty<IView> viewProperty() {
		return view;
	}

	public void setView(IView view) {
		this.view.set(view);
		if (view != null) {
			view.setViewTab(this);
			this.setText(view.getName());
		}
	}

	@Override
	public Node getImageNode() {
		return getView() == null ? null : getView().getImageNode();
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return getView() == null ? null : getView().getPresenter();
	}

	public AlgorithmBreadCrumbsToolBar getAlgorithmBreadCrumbsToolBar() {
		return algorithmBreadCrumbsToolBar;
	}

	public void clear() {}
}

