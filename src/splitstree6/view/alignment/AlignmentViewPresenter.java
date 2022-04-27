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
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.util.BitSetUtils;
import jloda.util.NumberUtils;
import jloda.util.Single;
import splitstree6.data.parts.CharactersType;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.window.MainWindowController;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * alignment view presenter
 * Daniel Huson, 4.2022
 */
public class AlignmentViewPresenter implements IDisplayTabPresenter {
	private final InvalidationListener updateAxisScrollBarCanvasListener;
	private final InvalidationListener updateCanvasListener;
	private final InvalidationListener taxonSelectionListener;

	private final AlignmentViewController controller;
	private final MainWindowController mainWindowController;

	private boolean colorSchemeSet = false;


	public AlignmentViewPresenter(MainWindow mainWindow, AlignmentView alignmentView) {
		var workflow = mainWindow.getWorkflow();

		controller = alignmentView.getController();
		mainWindowController = mainWindow.getController();

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


		updateCanvasListener = e -> Platform.runLater(() -> {
			updateTaxaCellFactory(controller.getTaxaListView(), alignmentView.getOptionUnitHeight());
			DrawAlignment.updateCanvas(controller.getCanvas(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(), alignmentView.getOptionColorScheme(),
					alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis());

			DrawAlignment.updateTaxaSelection(controller.getCanvas(), controller.getTaxaSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(), alignmentView.getOptionUnitWidth(),
					alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), mainWindow.getTaxonSelectionModel());
			DrawAlignment.updateTaxaSelection(controller.getCanvas(), controller.getTaxaSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(), alignmentView.getOptionUnitWidth(),
					alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), mainWindow.getTaxonSelectionModel());

			DrawAlignment.updateSiteSelection(controller.getCanvas(), controller.getSiteSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(), alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), alignmentView.getActiveSites(), alignmentView.getSelectedSites());
			controller.getSelectionLabel().setText(alignmentView.createSelectionString());
		});

		updateAxisScrollBarCanvasListener = e -> {
			controller.getAxis().setPadding(new Insets(0, 0, 0, alignmentView.getOptionUnitWidth()));
			Platform.runLater(() -> {
				AxisAndScrollBarUpdate.update(controller.getAxis(), controller.gethScrollBar(), controller.getCanvas().getWidth(),
						alignmentView.getOptionUnitWidth(), alignmentView.getInputCharacters() != null ? alignmentView.getInputCharacters().getNchar() : 0, alignmentView.selectedSitesProperty());
				AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), alignmentView.getInputCharacters(), alignmentView.getActiveSites(), alignmentView.getSelectedSites());
			});
			updateCanvasListener.invalidated(null);
		};

		controller.getCanvas().widthProperty().addListener(updateAxisScrollBarCanvasListener);
		controller.getCanvas().heightProperty().addListener(updateAxisScrollBarCanvasListener);
		alignmentView.optionUnitWidthProperty().addListener(updateAxisScrollBarCanvasListener);
		alignmentView.optionUnitHeightProperty().addListener(updateAxisScrollBarCanvasListener);

		alignmentView.selectedSitesProperty().addListener(e -> {
			DrawAlignment.updateSiteSelection(controller.getCanvas(), controller.getSiteSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(),
					alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), alignmentView.getActiveSites(), alignmentView.getSelectedSites());
			AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), alignmentView.getInputCharacters(), alignmentView.getActiveSites(), alignmentView.getSelectedSites());
			controller.getSelectionLabel().setText(alignmentView.createSelectionString());
		});

		taxonSelectionListener = e -> {
			if (!inSelectionUpdate.get()) {
				try {
					inSelectionUpdate.set(true);
					controller.getTaxaListView().getSelectionModel().clearSelection();
					for (var t : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
						controller.getTaxaListView().getSelectionModel().select(t);
					}
					DrawAlignment.updateTaxaSelection(controller.getCanvas(), controller.getTaxaSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(), alignmentView.getOptionUnitWidth(),
							alignmentView.getOptionUnitHeight(), controller.getvScrollBar(), controller.getAxis(), mainWindow.getTaxonSelectionModel());
					controller.getSelectionLabel().setText(alignmentView.createSelectionString());
				} finally {
					inSelectionUpdate.set(false);
				}
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(taxonSelectionListener));


		InvalidationListener updateTaxaListener = e -> {
			controller.getTaxaListView().getItems().clear();
			if (alignmentView.getInputTaxa() != null) {
				for (var taxon : alignmentView.getInputTaxa().getTaxa()) {
					controller.getTaxaListView().getItems().add(taxon);
				}
			}
		};

		alignmentView.inputCharactersNodeValidProperty().addListener((v, o, n) -> {
			if (n) {
				var inputCharacters = alignmentView.getInputCharacters();
				if (inputCharacters != null) {
					if (inputCharacters.getDataType() == CharactersType.Protein) {
						if (!colorSchemeSet || alignmentView.getOptionColorScheme() == ColorScheme.Nucleotide) {
							alignmentView.setOptionColorScheme(ColorScheme.Diamond11);
						}
						colorSchemeSet = true;
					} else if ((inputCharacters.getDataType() == CharactersType.DNA || inputCharacters.getDataType() == CharactersType.RNA)) {
						if (!colorSchemeSet || alignmentView.getOptionColorScheme() != ColorScheme.Nucleotide && alignmentView.getOptionColorScheme() != ColorScheme.Random && alignmentView.getOptionColorScheme() != ColorScheme.None) {
							alignmentView.setOptionColorScheme(ColorScheme.Nucleotide);
						}
						colorSchemeSet = true;
					}
				}
			}
		});

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

		controller.getSelectAllMenuItem().setOnAction(e ->
				alignmentView.setSelectedSites(BitSetUtils.asBitSet(BitSetUtils.range(1, alignmentView.getInputCharacters().getNchar() + 1))));
		controller.getSelectAllMenuItem().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not());

		controller.getSelectNoneMenuItem().setOnAction(e -> alignmentView.setSelectedSites(new BitSet()));
		controller.getSelectAllMenuItem().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not().or(Bindings.createBooleanBinding(() -> alignmentView.getSelectedSites().cardinality() == 0, alignmentView.selectedSitesProperty())));

		controller.getSelectCodon0MenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s += 3)
					bits.set(s);
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectCodon0MenuItem().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not().or(alignmentView.nucleotideDataProperty().not()));

		controller.getSelectCodon1MenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 2; s <= inputCharacters.getNchar(); s += 3)
					bits.set(s);
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectCodon1MenuItem().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not().or(alignmentView.nucleotideDataProperty().not()));

		controller.getSelectCodon2MenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 3; s <= inputCharacters.getNchar(); s += 3)
					bits.set(s);
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectCodon2MenuItem().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not().or(alignmentView.nucleotideDataProperty().not()));

		controller.getSelectConstantMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isConstantSite(s))
						bits.set(s);
				}
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectConstantMenuItem().disableProperty().bind(alignmentView.emptyProperty());

		controller.getSelectGapMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isGapSite(s))
						bits.set(s);
				}
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectGapMenuItem().disableProperty().bind(alignmentView.emptyProperty());

		controller.getSelectAllNonInformativeMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isNonParsimonyInformative(s))
						bits.set(s);
				}
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectAllNonInformativeMenuItem().disableProperty().bind(alignmentView.emptyProperty());

		controller.getEnableAllTaxaMenuItem().setOnAction(e -> {
			var inputTaxa = alignmentView.getInputTaxa();
			if (inputTaxa != null) {
				alignmentView.setActiveTaxa(BitSetUtils.asBitSet(NumberUtils.range(1, inputTaxa.getNtax() + 1)));
			}
		});
		controller.getEnableAllTaxaMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getInputTaxa() == null || alignmentView.getActiveTaxa().cardinality() == alignmentView.getInputTaxa().getNtax(), alignmentView.activeTaxaProperty()));

		controller.getEnableSelectedTaxaOnlyMenuItem().setOnAction(e -> alignmentView.setActiveTaxa(alignmentView.getSelectedTaxa()));
		controller.getEnableSelectedTaxaOnlyMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getSelectedTaxa().cardinality() == 0, alignmentView.selectedTaxaProperty()));

		controller.getDisableSelectedTaxaMenuItem().setOnAction(e -> {
			var bits = BitSetUtils.minus(alignmentView.getActiveTaxa(), alignmentView.getSelectedTaxa());
			if (bits.cardinality() < alignmentView.getActiveTaxa().cardinality())
				alignmentView.setSelectedTaxa(bits);
		});
		controller.getDisableSelectedTaxaMenuItem().disableProperty().bind(controller.getEnableSelectedTaxaOnlyMenuItem().disableProperty());


		controller.getEnableAllSitesMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				alignmentView.setActiveSites(BitSetUtils.asBitSet(NumberUtils.range(1, inputCharacters.getNchar() + 1)));
			}
		});

		controller.getEnableSelectedSitesOnlyMenuItem().setOnAction(e -> {
			alignmentView.setActiveSites(alignmentView.getSelectedSites());
		});
		controller.getEnableSelectedSitesOnlyMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getSelectedSites().cardinality() == 0, alignmentView.selectedSitesProperty()));

		controller.getDisableSelectedSitesMenuItem().setOnAction(e -> {
			var bits = BitSetUtils.minus(alignmentView.getActiveSites(), alignmentView.getSelectedSites());
			if (bits.cardinality() < alignmentView.getActiveSites().cardinality())
				alignmentView.setActiveSites(bits);
		});
		controller.getDisableSelectedSitesMenuItem().disableProperty().bind(controller.getEnableSelectedSitesOnlyMenuItem().disableProperty());

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
