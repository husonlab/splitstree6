/*
 *  AlignmentViewPresenter.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.util.Single;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.window.MainWindowController;
import splitstree6.workflow.DataNode;

import java.util.ArrayList;

/**
 * alignment view presenter
 * Daniel Huson, 4.2022
 */
public class AlignmentViewPresenter implements IDisplayTabPresenter {
	private final InvalidationListener invalidationListener;
	private final InvalidationListener updateAxisScrollBarCanvasListener;
	private final InvalidationListener updateCanvasListener;
	private final InvalidationListener taxonSelectionListener;

	private final AlignmentViewController controller;
	private final MainWindowController mainWindowController;
	private final SelectionModel<Integer> siteSelectionModel;

	private boolean colorSchemeSet = false;


	public AlignmentViewPresenter(MainWindow mainWindow, AlignmentView alignmentView) {
		var workflow = mainWindow.getWorkflow();
		controller = alignmentView.getController();
		mainWindowController = mainWindow.getController();
		siteSelectionModel = alignmentView.getSiteSelectionModel();

		controller.getColorSchemeCBox().getItems().addAll(ColorScheme.values());
		controller.getColorSchemeCBox().valueProperty().bindBidirectional(alignmentView.optionColorSchemeProperty());

		controller.getTaxaListView().getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		var inSelectionUpdate = new Single<>(false);

		controller.getTaxaListView().getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super Taxon>) e -> {
			if (!inSelectionUpdate.get()) {
				try {
					inSelectionUpdate.set(true);

					var toAdd = new ArrayList<Taxon>();
					var toRemove = new ArrayList<Taxon>();

					while (e.next()) {
						if (e.getAddedSize() > 0)
							toAdd.addAll(e.getAddedSubList());
						if (e.getRemovedSize() > 0)
							toRemove.addAll(e.getRemoved());
					}
					Platform.runLater(() -> {
						mainWindow.getTaxonSelectionModel().clearSelection(toRemove);
						mainWindow.getTaxonSelectionModel().selectAll(toAdd);
					});
				} finally {
					inSelectionUpdate.set(false);
				}
			}
		});

		var workingTaxaNode = new SimpleObjectProperty<DataNode<?>>(this, "workingTaxaNode");
		var workingTaxa = new SimpleObjectProperty<TaxaBlock>(this, "workingTaxa");
		var workingCharactersNode = new SimpleObjectProperty<DataNode<?>>(this, "workingCharactersNode");
		var workingCharacters = new SimpleObjectProperty<CharactersBlock>(this, "workingCharacters");
		var nucleotideData = new SimpleBooleanProperty(this, "nucleotideData");


		updateCanvasListener = e -> Platform.runLater(() -> {
			updateTaxaCellFactory(controller.getTaxaListView(), alignmentView.getOptionUnitHeight());
			DrawAlignment.updateCanvas(controller.getCanvas(), workingTaxa.get(), workingCharacters.get(), alignmentView.getOptionColorScheme(), alignmentView.getOptionUnitWidth(),
					alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis());
			DrawAlignment.updateTaxaSelection(controller.getCanvas(), controller.getTaxaSelectionGroup(), workingTaxa.get(), workingCharacters.get(), alignmentView.getOptionUnitWidth(),
					alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), mainWindow.getTaxonSelectionModel());
			DrawAlignment.updateSiteSelection(controller.getCanvas(), controller.getSiteSelectionGroup(), workingTaxa.get(), workingCharacters.get(), alignmentView.getOptionUnitWidth(),
					alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), siteSelectionModel);
		});

		updateAxisScrollBarCanvasListener = e -> {
			controller.getAxis().setPadding(new Insets(0, 0, 0, alignmentView.getOptionUnitWidth()));
			Platform.runLater(() -> {
				AxisAndScrollBarUpdate.update(controller.getAxis(), controller.gethScrollBar(), controller.getCanvas().getWidth(),
						alignmentView.getOptionUnitWidth(), workingCharacters.get() != null ? workingCharacters.get().getNchar() : 0, siteSelectionModel);
				AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), alignmentView.getOptionUnitWidth(), siteSelectionModel);
			});
			updateCanvasListener.invalidated(null);
		};

		controller.getCanvas().widthProperty().addListener(updateAxisScrollBarCanvasListener);
		controller.getCanvas().heightProperty().addListener(updateAxisScrollBarCanvasListener);
		alignmentView.optionUnitWidthProperty().addListener(updateAxisScrollBarCanvasListener);
		alignmentView.optionUnitHeightProperty().addListener(updateAxisScrollBarCanvasListener);

		siteSelectionModel.getSelectedItems().addListener((InvalidationListener) e -> {
			DrawAlignment.updateSiteSelection(controller.getCanvas(), controller.getSiteSelectionGroup(), workingTaxa.get(),
					workingCharacters.get(), alignmentView.getOptionUnitWidth(), alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), siteSelectionModel);
			AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), alignmentView.getOptionUnitWidth(), siteSelectionModel);
		});

		taxonSelectionListener = e -> {
			if (!inSelectionUpdate.get()) {
				try {
					inSelectionUpdate.set(true);
					controller.getTaxaListView().getSelectionModel().clearSelection();
					for (var t : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
						controller.getTaxaListView().getSelectionModel().select(t);
					}
					DrawAlignment.updateTaxaSelection(controller.getCanvas(), controller.getTaxaSelectionGroup(), workingTaxa.get(), workingCharacters.get(), alignmentView.getOptionUnitWidth(),
							alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), mainWindow.getTaxonSelectionModel());
				} finally {
					inSelectionUpdate.set(false);
				}
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(taxonSelectionListener));

		InvalidationListener updateTaxaListener = e -> {
			controller.getTaxaListView().getItems().clear();
			if (workingTaxa.get() != null) {
				for (var taxon : workingTaxa.get().getTaxa()) {
					controller.getTaxaListView().getItems().add(taxon);
				}
			}
		};

		InvalidationListener updateCharactersListener = e -> {
			var theWorkingCharacters = workingCharacters.get();
			if (theWorkingCharacters != null) {
				if (theWorkingCharacters.getDataType() == CharactersType.Protein) {
					if (!colorSchemeSet || alignmentView.getOptionColorScheme() == ColorScheme.Nucleotide) {
						alignmentView.setOptionColorScheme(ColorScheme.Diamond11);
					}
					nucleotideData.set(false);
					colorSchemeSet = true;
				} else if ((theWorkingCharacters.getDataType() == CharactersType.DNA || theWorkingCharacters.getDataType() == CharactersType.RNA)) {
					if (!colorSchemeSet || alignmentView.getOptionColorScheme() != ColorScheme.Nucleotide && alignmentView.getOptionColorScheme() != ColorScheme.Random && alignmentView.getOptionColorScheme() != ColorScheme.None) {
						alignmentView.setOptionColorScheme(ColorScheme.Nucleotide);
					}
					nucleotideData.set(true);
					colorSchemeSet = true;
				} else {
					nucleotideData.set(false);
				}
			}
		};

		invalidationListener = e -> {
			if (workflow.getWorkingTaxaNode() != null) {
				workingTaxaNode.set(workflow.getWorkingTaxaNode());
				workingTaxaNode.get().validProperty().addListener(updateTaxaListener);
				workingTaxa.set(workflow.getWorkingTaxaBlock());
			} else {
				workingTaxaNode.set(null);
				workingTaxa.set(null);
			}

			if (workflow.getWorkingDataNode() != null && workflow.getWorkingDataNode().getDataBlock() instanceof CharactersBlock charactersBlock) {
				workingCharactersNode.set(workflow.getWorkingDataNode());
				workingCharactersNode.get().validProperty().addListener(a -> siteSelectionModel.clearSelection());
				workingCharactersNode.get().validProperty().addListener(updateAxisScrollBarCanvasListener);
				workingCharactersNode.get().validProperty().addListener(updateCharactersListener);
				workingCharacters.set(charactersBlock);
			} else {
				workingCharactersNode.set(null);
				workingCharacters.set(null);
			}
		};
		mainWindow.getWorkflow().validProperty().addListener(new WeakInvalidationListener(invalidationListener));

		invalidationListener.invalidated(null);

		alignmentView.optionColorSchemeProperty().addListener(updateCanvasListener);
		alignmentView.optionColorSchemeProperty().addListener((v, o, n) -> alignmentView.getUndoManager().add("color scheme", alignmentView.optionColorSchemeProperty(), o, n));
		alignmentView.optionUnitWidthProperty().addListener((v, o, n) -> alignmentView.getUndoManager().add("column width", alignmentView.optionUnitWidthProperty(), o, n));
		alignmentView.optionUnitHeightProperty().addListener((v, o, n) -> alignmentView.getUndoManager().add("row height", alignmentView.optionUnitHeightProperty(), o, n));

		MainWindowManager.useDarkThemeProperty().addListener(new WeakInvalidationListener(updateCanvasListener));

		controller.gethScrollBar().valueProperty().addListener(updateAxisScrollBarCanvasListener);
		controller.gethScrollBar().valueProperty().addListener((v, o, n) -> {
			var diff = n.doubleValue() - o.doubleValue();
			controller.getAxis().setLowerBound(controller.getAxis().getLowerBound() + diff);
			controller.getAxis().setUpperBound(controller.getAxis().getUpperBound() + diff);
		});
		controller.getvScrollBar().valueProperty().addListener(updateAxisScrollBarCanvasListener);

		controller.getExpandHorizontallyButton().setOnAction(e -> alignmentView.setOptionUnitWidth(1.2 * alignmentView.getOptionUnitWidth()));
		controller.getExpandHorizontallyButton().disableProperty().bind(alignmentView.optionUnitWidthProperty().greaterThan(64));

		controller.getContractHorizontallyButton().setOnAction(e -> alignmentView.setOptionUnitWidth(1 / 1.2 * alignmentView.getOptionUnitWidth()));
		controller.getContractHorizontallyButton().disableProperty().bind(alignmentView.optionUnitWidthProperty().lessThan(0.01));

		controller.getExpandVerticallyButton().setOnAction(e -> alignmentView.setOptionUnitHeight(1.2 * alignmentView.getOptionUnitHeight()));
		controller.getExpandVerticallyButton().disableProperty().bind(alignmentView.optionUnitHeightProperty().greaterThan(64));

		controller.getContractVerticallyButton().setOnAction(e -> alignmentView.setOptionUnitHeight(1 / 1.2 * alignmentView.getOptionUnitHeight()));
		controller.getContractVerticallyButton().disableProperty().bind(alignmentView.optionUnitHeightProperty().lessThan(0.01));

		controller.getSelectAllMenuItem().setOnAction(e -> {
			if (workingCharacters.get() != null) {
				for (var s = 1; s <= workingCharacters.get().getNchar(); s++)
					alignmentView.getSiteSelectionModel().select(s);
			}
		});
		controller.getSelectAllMenuItem().disableProperty().bind(workingCharacters.isNull());

		controller.getSelectNoneMenuItem().setOnAction(e -> {
			alignmentView.getSiteSelectionModel().clearSelection();
		});
		controller.getSelectNoneMenuItem().disableProperty().bind(workingCharacters.isNull().or(Bindings.isEmpty(alignmentView.getSiteSelectionModel().getSelectedItems())));

		controller.getSelectCodon0MenuItem().setOnAction(e -> {
			if (workingCharacters.get() != null) {
				for (var s = 1; s <= workingCharacters.get().getNchar(); s += 3)
					alignmentView.getSiteSelectionModel().select(s);
			}
		});
		controller.getSelectCodon0MenuItem().disableProperty().bind(nucleotideData.not());

		controller.getSelectCodon1MenuItem().setOnAction(e -> {
			if (workingCharacters.get() != null) {
				for (var s = 2; s <= workingCharacters.get().getNchar(); s += 3)
					alignmentView.getSiteSelectionModel().select(s);
			}
		});
		controller.getSelectCodon1MenuItem().disableProperty().bind(nucleotideData.not());

		controller.getSelectCodon2MenuItem().setOnAction(e -> {
			if (workingCharacters.get() != null) {
				for (var s = 3; s <= workingCharacters.get().getNchar(); s += 3)
					alignmentView.getSiteSelectionModel().select(s);
			}
		});
		controller.getSelectCodon2MenuItem().disableProperty().bind(nucleotideData.not());

		controller.getSelectConstantMenuItem().setOnAction(e -> {
			if (workingCharacters.get() != null) {
				for (var s = 1; s <= workingCharacters.get().getNchar(); s++) {
					if (workingCharacters.get().isConstantSite(s))
						alignmentView.getSiteSelectionModel().select(s);
				}
			}
		});
		controller.getSelectConstantMenuItem().disableProperty().bind(alignmentView.emptyProperty());

		controller.getSelectGapMenuItem().setOnAction(e -> {
			if (workingCharacters.get() != null) {
				for (var s = 1; s <= workingCharacters.get().getNchar(); s++) {
					if (workingCharacters.get().isGapSite(s))
						alignmentView.getSiteSelectionModel().select(s);
				}
			}
		});
		controller.getSelectGapMenuItem().disableProperty().bind(alignmentView.emptyProperty());

		controller.getSelectAllNonInformativeMenuItem().setOnAction(e -> {
			if (workingCharacters.get() != null) {
				for (var s = 1; s <= workingCharacters.get().getNchar(); s++) {
					if (workingCharacters.get().isNonParsimonyInformative(s))
						alignmentView.getSiteSelectionModel().select(s);
				}
			}
		});
		controller.getSelectAllNonInformativeMenuItem().disableProperty().bind(nucleotideData.not());

		controller.getEnableButton().disableProperty().bind(Bindings.isEmpty(siteSelectionModel.getSelectedItems()).and(Bindings.isEmpty(mainWindow.getTaxonSelectionModel().getSelectedItems())));
		controller.getDisableButton().disableProperty().bind(Bindings.isEmpty(siteSelectionModel.getSelectedItems()).and(Bindings.isEmpty(mainWindow.getTaxonSelectionModel().getSelectedItems())));

		Platform.runLater(() -> invalidationListener.invalidated(null));
		Platform.runLater(() -> updateTaxaListener.invalidated(null));
		Platform.runLater(() -> updateAxisScrollBarCanvasListener.invalidated(null));

		Platform.runLater(() -> {
			var taxonHBar = BasicFX.getScrollBar(controller.getTaxaListView(), Orientation.HORIZONTAL);
			if (taxonHBar != null) {
				controller.getLeftBottomPane().setMaxHeight(taxonHBar.isVisible() ? 0 : 16);
				taxonHBar.visibleProperty().addListener((v, o, n) -> controller.getLeftBottomPane().setPrefHeight(n ? 0 : 16));
			}

			var taxonVBar = BasicFX.getScrollBar(controller.getTaxaListView(), Orientation.VERTICAL);
			if (taxonVBar != null) {
				controller.getvScrollBar().visibleProperty().bind(taxonVBar.visibleProperty());
				controller.getvScrollBar().minProperty().bind(taxonVBar.minProperty());
				controller.getvScrollBar().maxProperty().bind(taxonVBar.maxProperty());
				controller.getvScrollBar().visibleAmountProperty().bind(taxonVBar.visibleAmountProperty());
				taxonVBar.valueProperty().bindBidirectional(controller.getvScrollBar().valueProperty());
			}
		});
	}

	private void updateTaxaCellFactory(ListView<Taxon> listView, double unitHeight) {
		listView.setFixedCellSize(unitHeight);

		listView.setCellFactory(cell -> new ListCell<>() {
			@Override
			protected void updateItem(Taxon item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setText(null);
					setGraphic(null);
				} else {
					setGraphic(null);
					setStyle(String.format("-fx-font-size: %.1f;", Math.min(18, 0.6 * unitHeight)));
					setText(item.getName());
					setAlignment(Pos.CENTER_LEFT);
				}
			}
		});
	}

	@Override
	public void setupMenuItems() {
		mainWindowController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainWindowController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());
		mainWindowController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainWindowController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());
	}
}
