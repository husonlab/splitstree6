/*
 *  ViewTab.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.tab;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.control.Tab;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import splitstree6.view.IView;
import splitstree6.window.MainWindow;

/**
 * tab to be shown in main tab-pane
 * Daniel Huson, 11.2021
 */
public class ViewTab extends Tab {
	private final UndoManager undoManager = new UndoManager();
	private final MainWindow mainWindow;
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final ObjectProperty<Bounds> layoutBounds = new SimpleObjectProperty<>(new BoundingBox(0, 0, 0, 0));

	private final ObjectProperty<IView> view = new SimpleObjectProperty<>();

	/**
	 * constructor
	 */
	public ViewTab(MainWindow mainWindow, String name, boolean closable) {
		this.mainWindow = mainWindow;

		setText(name);
		setClosable(closable);
		setOnCloseRequest(v -> mainWindow.removeTabFromMainTabPane(this));

		tabPaneProperty().addListener((v, o, n) -> {
			if (n == null)
				layoutBounds.unbind();
			else
				layoutBounds.bind(n.layoutBoundsProperty());
		});

		viewProperty().addListener((v, o, n) -> {
			setContent(n.getRoot());
		});

		{
			var pane = new Pane();
			pane.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> getLayoutBounds().getWidth(), layoutBounds));
			pane.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> getLayoutBounds().getHeight() - 32, layoutBounds));

			BasicFX.reportChanges(pane.widthProperty());
			BasicFX.reportChanges(pane.heightProperty());

			{
				var rectangle = new Rectangle(2, 2, pane.getPrefWidth() - 4, pane.getPrefHeight() - 4);
				rectangle.widthProperty().bind(pane.prefWidthProperty().subtract(4));
				rectangle.heightProperty().bind(pane.prefHeightProperty().subtract(4));
				rectangle.setFill(Color.BLUE);

				pane.getChildren().add(rectangle);
			}
			{
				var rectangle = new Rectangle(20, 20, pane.getPrefWidth() - 40, pane.getPrefHeight() - 40);
				rectangle.widthProperty().bind(pane.prefWidthProperty().subtract(40));
				rectangle.heightProperty().bind(pane.prefHeightProperty().subtract(40));
				rectangle.setFill(Color.YELLOW);

				pane.getChildren().add(rectangle);
			}
			setContent(pane);
		}

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
	}
}

