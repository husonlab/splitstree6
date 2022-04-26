/*
 *  AlignmentView.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.alignment;

import javafx.beans.property.*;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.PrintUtils;
import jloda.fx.util.ResourceManagerFX;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

/**
 * displays the input alignment
 * Daniel Huson, 4.2022
 */
public class AlignmentView implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final AlignmentViewController controller;
	private final AlignmentViewPresenter presenter;

	private final SelectionModel<Integer> sitesSelectionModel = new SetSelectionModel<>();

	private final ObjectProperty<ColorScheme> optionColorScheme = new SimpleObjectProperty<>(this, "optionColorScheme", ColorScheme.None);
	private final DoubleProperty optionUnitWidth = new SimpleDoubleProperty(this, "optionUnitWidth", 18);
	private final DoubleProperty optionUnitHeight = new SimpleDoubleProperty(this, "optionUnitHeight", 18);


	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	public List<String> listOptions() {
		return List.of(optionColorScheme.getName(), optionUnitWidth.getName(), optionUnitHeight.getName());
	}

	public AlignmentView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<AlignmentViewController>(AlignmentViewController.class);
		controller = loader.getController();

		presenter = new AlignmentViewPresenter(mainWindow, this);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null) {
				targetBounds.bind(n.layoutBoundsProperty());
				n.setGraphic(ResourceManagerFX.getIconAsImageView("Alignment16.gif", 16));
			}
		});

		empty.bind(mainWindow.emptyProperty());

		setViewTab(viewTab);

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));
	}

	@Override
	public String getName() {
		return name.get();
	}

	@Override
	public Node getRoot() {
		return controller.getRoot();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();
	}

	@Override
	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
	}

	@Override
	public int size() {
		return controller.getTaxaListView().getItems().size();
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return PrintUtils.createImage(controller.getInnerAnchorPane(), null);
	}

	@Override
	public void clear() {
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getCitation() {
		return null;
	}

	public SelectionModel<Integer> getSitesSelectionModel() {
		return sitesSelectionModel;
	}

	public ColorScheme getOptionColorScheme() {
		return optionColorScheme.get();
	}

	public ObjectProperty<ColorScheme> optionColorSchemeProperty() {
		return optionColorScheme;
	}

	public void setOptionColorScheme(ColorScheme optionColorScheme) {
		this.optionColorScheme.set(optionColorScheme);
	}

	public double getOptionUnitWidth() {
		return optionUnitWidth.get();
	}

	public DoubleProperty optionUnitWidthProperty() {
		return optionUnitWidth;
	}

	public void setOptionUnitWidth(double optionUnitWidth) {
		this.optionUnitWidth.set(optionUnitWidth);
	}

	public double getOptionUnitHeight() {
		return optionUnitHeight.get();
	}

	public DoubleProperty optionUnitHeightProperty() {
		return optionUnitHeight;
	}

	public void setOptionUnitHeight(double optionUnitHeight) {
		this.optionUnitHeight.set(optionUnitHeight);
	}

	public AlignmentViewController getController() {
		return controller;
	}
}
