/*
 *  Next.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.next;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ProgramExecutorService;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.IView;
import splitstree6.window.MainWindow;

public class Next implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>();

	private final Pagination pagination = new Pagination();
	private final StackPane stackPane = new StackPane(pagination);

	private final IntegerProperty size = new SimpleIntegerProperty(100);
	private final BooleanProperty empty = new SimpleBooleanProperty(false);

	public Next(MainWindow mainWindow, ViewTab viewTab) {
		ObjectProperty<PageFactory> pageFactory = new SimpleObjectProperty<>();
		pagination.pageFactoryProperty().bind(pageFactory);

		var rows = new SimpleIntegerProperty(4);
		var cols = new SimpleIntegerProperty(4);

		var numberOfPages = size.divide(rows.multiply(cols).subtract(1)).add(1);
		pagination.pageCountProperty().bind(numberOfPages);

		var box = new SimpleObjectProperty<>(new Dimension2D(0, 0));

		final ChangeListener<Bounds> changeListener = (v, o, n) -> {
			if (n.getWidth() > 0 && n.getHeight() > 0) {
				box.set(new Dimension2D(n.getWidth() / cols.get() - 5, (n.getHeight() - 70) / rows.get() - 5));
			}
		};

		this.viewTab.addListener((v, o, n) -> {
			if (o != null)
				o.layoutBoundsProperty().removeListener(changeListener);
			if (n != null) {
				n.layoutBoundsProperty().addListener(changeListener);
				changeListener.changed(null, null, n.getLayoutBounds());
			}
		});
		setViewTab(viewTab);

		pageFactory.set(new PageFactory(rows, cols, size, box));
	}

	@Override
	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);

	}

	private static class PageFactory implements Callback<Integer, Node> {
		private final IntegerProperty rows;
		private final IntegerProperty cols;
		private final IntegerProperty itemCount;
		private final ObjectProperty<Dimension2D> dimensions;
		private GridPane gridPane;
		private int page;

		public PageFactory(IntegerProperty rows, IntegerProperty cols, IntegerProperty itemCount, ObjectProperty<Dimension2D> dimensions) {
			this.rows = rows;
			this.cols = cols;
			this.itemCount = itemCount;
			this.dimensions = dimensions;

			gridPane = new GridPane();
			gridPane.setHgap(5);
			gridPane.setVgap(5);

			dimensions.addListener(e -> RunAfterAWhile.apply(this, this::update));
		}

		private void update() {
			System.err.println("Update");

			Platform.runLater(() -> gridPane.getChildren().clear());
			var start = page * rows.get() * cols.get();
			var top = Math.min(itemCount.get(), start + rows.get() * cols.get());
			var r0 = 0;
			var c0 = 0;
			for (int which0 = start; which0 < top; which0++) {
				final int which = which0;
				final int r = r0;
				final int c = c0;
				ProgramExecutorService.submit(() -> {
					var pane = new StackPane();
					if (dimensions.get().getWidth() > 0 && dimensions.get().getHeight() > 0) {
						pane.setPrefSize(dimensions.get().getWidth(), dimensions.get().getHeight());
						pane.setMinSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);
						pane.setMaxSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);

						var rectangle = new Rectangle(5, 5, pane.getPrefWidth() - 10, pane.getPrefHeight() - 10);
						rectangle.setFill(Color.YELLOW);
						rectangle.setStroke(Color.GREEN);
						pane.getChildren().add(rectangle);
						pane.getChildren().add(new Label("which " + which));
						GridPane.setRowIndex(pane, r);
						GridPane.setColumnIndex(pane, c);

						pane.widthProperty().addListener((v, o, n) -> {
							if (n.doubleValue() == 0)
								System.err.println("Zero!");
						});
					}
					Platform.runLater(() -> gridPane.getChildren().add(pane));
				});
				if (++c0 == cols.get()) {
					r0++;
					c0 = 0;
				}
			}
		}

		@Override
		public Node call(Integer page) {
			this.page = page;
			gridPane = new GridPane();
			gridPane.setHgap(5);
			gridPane.setVgap(5);
			update();
			return gridPane;
		}
	}


	@Override
	public String getName() {
		return "Next";
	}

	@Override
	public Node getRoot() {
		return stackPane;
	}

	@Override
	public void setupMenuItems() {

	}

	@Override
	public int size() {
		return size.get();
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ObservableValue<Boolean> emptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return stackPane;
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return null;
	}
}
