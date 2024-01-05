/*
 *  AlignmentViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.binding.When;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Font;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.BasicFX;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.window.MainWindowManager;
import jloda.util.*;
import splitstree6.algorithms.utils.CharactersUtilities;
import splitstree6.data.parts.CharactersType;
import splitstree6.data.parts.Taxon;
import splitstree6.splits.ASplit;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.utils.ExportUtils;
import splitstree6.window.MainWindow;
import splitstree6.window.MainWindowController;
import splitstree6.workflow.Workflow;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * alignment view presenter
 * Daniel Huson, 4.2022
 */
public class AlignmentViewPresenter implements IDisplayTabPresenter {
	private final InvalidationListener updateCanvasListener;

	private final AlignmentView view;
	private final AlignmentViewController controller;
	private final MainWindowController mainController;

	private final MainWindow mainWindow;

	private final Workflow workflow;

	private final Object sync = new Object();

	private boolean colorSchemeSet = false;

	public AlignmentViewPresenter(MainWindow mainWindow, AlignmentView view) {
		this.mainWindow = mainWindow;
		this.view = view;
		this.workflow = mainWindow.getWorkflow();

		controller = view.getController();
		mainController = mainWindow.getController();

		setupColorSchemeMenu(view.optionColorSchemeProperty(), controller.getColorSchemeMenuButton());
		controller.getColorSchemeMenuButton().disableProperty().bind(view.emptyProperty());

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

		InvalidationListener updateTaxaListener = e -> {
			controller.getTaxaListView().getItems().clear();
			if (view.getInputTaxa() != null) {
				for (var taxon : view.getInputTaxa().getTaxa()) {
					controller.getTaxaListView().getItems().add(taxon);
				}
			}
		};
		view.inputTaxaNodeValidProperty().addListener(updateTaxaListener);

		var canvasWidth = new SimpleDoubleProperty();
		canvasWidth.bind(controller.getHorizontalScrollBar().widthProperty());
		var canvasHeight = new SimpleDoubleProperty();
		canvasHeight.bind(controller.getVerticalScrollBar().heightProperty());

		var alignmentDrawer = new AlignmentDrawer(controller.getImageGroup(), controller.getCanvasGroup(), mainController.getBottomFlowPane());

		updateCanvasListener = e -> {
			RunAfterAWhile.applyInFXThread(sync, () -> {
				var width = canvasWidth.get();
				var height = canvasHeight.get();

				AxisAndScrollBarUpdate.update(controller.getAxis(), controller.getHorizontalScrollBar(), width,
						view.getOptionUnitWidth(), view.getInputCharacters() != null ? view.getInputCharacters().getNchar() : 0, view);

				alignmentDrawer.updateCanvas(width, height, view.getInputTaxa(), view.getInputCharacters(), view.getConsensusSequence(),
						view.getOptionColorScheme(), view.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(),
						view.getActiveTaxa(), view.getActiveSites());


				AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), view.getInputCharacters(),
						view.getActiveSites(), view.getSelectedSites());

				updateTaxaCellFactory(controller.getTaxaListView(), view.getOptionUnitHeight(), view::isDisabled);

				alignmentDrawer.updateTaxaSelection(controller.getTaxaSelectionGroup(), view.getInputTaxa(), view.getInputCharacters(),
						view.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), view.getSelectedTaxa());

				alignmentDrawer.updateSiteSelection(controller.getSiteSelectionGroup(), view.getInputTaxa(), view.getInputCharacters(),
						view.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), view.getSelectedSites());

				if (false)
					alignmentDrawer.updateTaxaSelection(controller.getTaxaSelectionGroup(), view.getInputTaxa(), view.getInputCharacters(),
							view.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), view.getSelectedTaxa());

				controller.getSelectionLabel().setText(view.createSelectionString());

				// update block increment for vertical scroll bar:
				{
					var listViewVerticalScrollBar = BasicFX.getScrollBar(controller.getTaxaListView(), Orientation.VERTICAL);
					if (listViewVerticalScrollBar != null) {
						var countVisible = controller.getTaxaListView().getHeight() / controller.getTaxaListView().getFixedCellSize();
						if (view.getInputTaxa() != null && countVisible < view.getInputTaxa().getNtax()) {
							controller.getVerticalScrollBar().setBlockIncrement(countVisible / (view.getInputTaxa().getNtax() - countVisible));
						} else
							controller.getVerticalScrollBar().setBlockIncrement(1.0);
					}
				}
			});
		};

		canvasWidth.addListener(updateCanvasListener);
		canvasHeight.addListener(updateCanvasListener);

		view.optionUnitWidthProperty().addListener(updateCanvasListener);
		view.optionUnitHeightProperty().addListener(updateCanvasListener);

		view.activeSitesProperty().addListener(updateCanvasListener);
		view.activeTaxaProperty().addListener(updateCanvasListener);

		view.selectedSitesProperty().addListener(e -> {
			alignmentDrawer.updateSiteSelection(controller.getSiteSelectionGroup(), view.getInputTaxa(), view.getInputCharacters(),
					view.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), view.getSelectedSites());
			AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), view.getInputCharacters(), view.getActiveSites(), view.getSelectedSites());
			controller.getSelectionLabel().setText(view.createSelectionString());
		});

		view.selectedTaxaProperty().addListener((v, o, n) -> {
			if (!inSelectionUpdate.get()) {
				try {
					inSelectionUpdate.set(true);
					var start = System.currentTimeMillis();
					controller.getTaxaListView().getSelectionModel().clearSelection();
					var inputTaxa = view.getInputTaxa();
					if (inputTaxa != null) {

						for (var taxon : controller.getTaxaListView().getItems()) {
							var t = inputTaxa.indexOf(taxon);
							if (t != -1 && n.get(t))
								controller.getTaxaListView().getSelectionModel().select(taxon);
							if (System.currentTimeMillis() - start > 1000)
								break; // this is taking too long
						}
						alignmentDrawer.updateTaxaSelection(controller.getTaxaSelectionGroup(), inputTaxa, view.getInputCharacters(),
								view.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), view.getSelectedTaxa());
					}
					controller.getSelectionLabel().setText(view.createSelectionString());
				} finally {
					inSelectionUpdate.set(false);
				}
			}
		});

		view.inputCharactersNodeValidProperty().addListener((v, o, n) -> {
			if (n) {
				var inputCharacters = view.getInputCharacters();
				if (inputCharacters != null) {
					if (inputCharacters.getDataType() == CharactersType.Protein) {
						if (!colorSchemeSet || view.getOptionColorScheme() == ColorScheme.Nucleotide) {
							view.setOptionColorScheme(ColorScheme.Diamond11);
						}
						colorSchemeSet = true;
					} else if ((inputCharacters.getDataType() == CharactersType.DNA || inputCharacters.getDataType() == CharactersType.RNA)) {
						if (!colorSchemeSet || view.getOptionColorScheme() != ColorScheme.Nucleotide && view.getOptionColorScheme() != ColorScheme.Random && view.getOptionColorScheme() != ColorScheme.None) {
							view.setOptionColorScheme(ColorScheme.Nucleotide);
						}
						colorSchemeSet = true;
					} else if (inputCharacters.getDataType() == CharactersType.Standard) {
						if (!colorSchemeSet || view.getOptionColorScheme() != ColorScheme.Nucleotide && view.getOptionColorScheme() != ColorScheme.Random && view.getOptionColorScheme() != ColorScheme.None) {
							view.setOptionColorScheme(ColorScheme.Binary);
						}
						colorSchemeSet = true;
					}
				}
			}
		});

		view.optionColorSchemeProperty().addListener(updateCanvasListener);
		view.optionColorSchemeProperty().addListener((v, o, n) -> view.getUndoManager().add("color scheme", view.optionColorSchemeProperty(), o, n));
		view.optionUnitWidthProperty().addListener((v, o, n) -> view.getUndoManager().add("column width", view.optionUnitWidthProperty(), o, n));
		view.optionUnitHeightProperty().addListener((v, o, n) -> view.getUndoManager().add("row height", view.optionUnitHeightProperty(), o, n));

		MainWindowManager.useDarkThemeProperty().addListener(new WeakInvalidationListener(updateCanvasListener));

		controller.getHorizontalScrollBar().valueProperty().addListener(updateCanvasListener);

		controller.getHorizontalScrollBar().valueProperty().addListener((v, o, n) -> {
			var diff = n.doubleValue() - controller.getAxis().getLowerBound();
			controller.getAxis().setLowerBound(controller.getAxis().getLowerBound() + diff);
			controller.getAxis().setUpperBound(controller.getAxis().getUpperBound() + diff);
		});

		controller.getVerticalScrollBar().valueProperty().addListener(updateCanvasListener);

		controller.getExpandHorizontallyButton().setOnAction(e -> view.setOptionUnitWidth(1.2 * view.getOptionUnitWidth()));
		controller.getExpandHorizontallyButton().disableProperty().bind(view.optionUnitWidthProperty().greaterThan(64));

		controller.getContractHorizontallyButton().setOnAction(e -> view.setOptionUnitWidth(1 / 1.2 * view.getOptionUnitWidth()));
		controller.getContractHorizontallyButton().disableProperty().bind(view.optionUnitWidthProperty().lessThan(0.01));

		controller.getExpandVerticallyButton().setOnAction(e -> view.setOptionUnitHeight(1.2 * view.getOptionUnitHeight()));
		controller.getExpandVerticallyButton().disableProperty().bind(view.optionUnitHeightProperty().greaterThan(64));

		controller.getContractVerticallyButton().setOnAction(e -> view.setOptionUnitHeight(1 / 1.2 * view.getOptionUnitHeight()));
		controller.getContractVerticallyButton().disableProperty().bind(view.optionUnitHeightProperty().lessThan(0.01));

		controller.getZoomToFitButton().setOnAction(e -> {
			if (view.getInputCharacters().getNchar() * view.getOptionUnitWidth() > canvasWidth.get()
				|| view.getInputCharacters().getNtax() * view.getOptionUnitHeight() > canvasHeight.get()) {
				controller.getVerticalScrollBar().setValue(controller.getVerticalScrollBar().getMin());
				controller.getHorizontalScrollBar().setValue(controller.getHorizontalScrollBar().getMin());
				view.setOptionUnitWidth(Math.min(AlignmentView.DEFAULT_UNIT_WIDTH, canvasWidth.get() / view.getInputCharacters().getNchar()));
				view.setOptionUnitHeight(Math.min(AlignmentView.DEFAULT_UNIT_HEIGHT, canvasHeight.get() / view.getInputCharacters().getNtax()));
			} else {
				view.setOptionUnitWidth(AlignmentView.DEFAULT_UNIT_WIDTH);
				view.setOptionUnitHeight(AlignmentView.DEFAULT_UNIT_HEIGHT);
			}
		});
		controller.getZoomToFitButton().disableProperty().bind(view.inputCharactersNodeValidProperty().not());

		controller.getCanvas().setOnZoom(e -> {
			view.setOptionUnitWidth(e.getZoomFactor() * view.getOptionUnitWidth());
			view.setOptionUnitHeight(e.getZoomFactor() * view.getOptionUnitHeight());
		});

		controller.getSelectAllMenuItem().setOnAction(e ->
				view.setSelectedSites(BitSetUtils.asBitSet(BitSetUtils.range(1, view.getInputCharacters().getNchar() + 1))));
		controller.getSelectAllMenuItem().disableProperty().bind(view.inputCharactersNodeValidProperty().not());

		controller.getSelectNoneMenuItem().setOnAction(e -> view.setSelectedSites(new BitSet()));
		controller.getSelectNoneMenuItem().disableProperty().bind(view.inputCharactersNodeValidProperty().not().or(Bindings.createBooleanBinding(() -> view.getSelectedSites().cardinality() == 0, view.selectedSitesProperty())));

		controller.getInvertSelectionMenuItem().setOnAction(e -> view.setSelectedSites(BitSetUtils.getComplement(view.getSelectedSites(), 1, this.view.getInputCharacters().getNchar() + 1)));
		controller.getInvertSelectionMenuItem().disableProperty().bind(this.view.emptyProperty());

		controller.getSelectCompatibleMenuItem().setOnAction(e -> {
			var split = new ASplit(this.view.getSelectedTaxa(), this.view.getInputTaxa().getNtax());
			var compatible = CharactersUtilities.computeAllCompatible(this.view.getInputCharacters(), split);
			System.err.printf("Compatible sites (%,d): %s%n ", compatible.cardinality(), StringUtils.toString(compatible));
			this.view.getSelectedSites().clear();
			if (compatible.cardinality() > 0) {
				Platform.runLater(() -> this.view.setSelectedSites(compatible));
			}
		});
		controller.getSelectCompatibleMenuItem().disableProperty().bind(this.view.emptyProperty()
				.or(Bindings.createBooleanBinding(() -> this.view.getSelectedTaxa().cardinality() == 0 || this.view.getSelectedTaxa().cardinality() == this.view.getInputTaxa().getNtax(), this.view.selectedTaxaProperty())));

		controller.getSelectIncompatibleMenuItem().setOnAction(e -> {
			var split = new ASplit(this.view.getSelectedTaxa(), this.view.getInputTaxa().getNtax());
			var compatible = CharactersUtilities.computeAllCompatible(this.view.getInputCharacters(), split);
			var incompatible = BitSetUtils.getComplement(compatible, 1, this.view.getInputCharacters().getNchar() + 1);
			System.err.printf("Incompatible sites (%,d): %s%n ", compatible.cardinality(), StringUtils.toString(incompatible));
			this.view.getSelectedSites().clear();
			if (incompatible.cardinality() > 0) {
				Platform.runLater(() -> this.view.setSelectedSites(incompatible));
			}
		});
		controller.getSelectIncompatibleMenuItem().disableProperty().bind(controller.getSelectCompatibleMenuItem().disableProperty());

		controller.getSelectCodon0MenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s += 3)
					bits.set(s);
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectCodon0MenuItem().disableProperty().bind(view.inputCharactersNodeValidProperty().not().or(view.nucleotideDataProperty().not()));

		controller.getSelectCodon1MenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 2; s <= inputCharacters.getNchar(); s += 3)
					bits.set(s);
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectCodon1MenuItem().disableProperty().bind(view.inputCharactersNodeValidProperty().not().or(view.nucleotideDataProperty().not()));

		controller.getSelectCodon2MenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 3; s <= inputCharacters.getNchar(); s += 3)
					bits.set(s);
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectCodon2MenuItem().disableProperty().bind(view.inputCharactersNodeValidProperty().not().or(view.nucleotideDataProperty().not()));

		controller.getSelectSynapomorphiesMenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			var bits = new BitSet();
			for (var s = 1; s <= inputCharacters.getNchar(); s++) {
				if (inputCharacters.isSynapomorphy(s, view.getSelectedTaxa()))
					bits.set(s);
			}
			bits.or(view.getSelectedSites());
			view.setSelectedSites(bits);
		});
		controller.getSelectSynapomorphiesMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> view.getSelectedTaxa().cardinality() == 0, view.selectedTaxaProperty()));

		controller.getSelectConstantMenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isConstantSite(s))
						bits.set(s);
				}
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectConstantMenuItem().disableProperty().bind(view.emptyProperty());

		controller.getSelectMissingMenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isMissingSite(s))
						bits.set(s);
				}
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectMissingMenuItem().disableProperty().bind(view.emptyProperty());

		controller.getSelectGapMenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isGapSite(s))
						bits.set(s);
				}
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectGapMenuItem().disableProperty().bind(view.emptyProperty());

		controller.getSelectMajorityGapOrMissingMenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					var count = 0;
					for (int t = 1; t <= inputCharacters.getNtax(); t++) {
						if (inputCharacters.get(t, s) == inputCharacters.getGapCharacter() || inputCharacters.get(t, s) == inputCharacters.getMissingCharacter()) {
							count++;
							if (count >= 0.5 * inputCharacters.getNtax()) {
								bits.set(s);
								break;
							}
						}
					}
				}
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectMajorityGapOrMissingMenuItem().disableProperty().bind(view.emptyProperty());


		controller.getSelectAllNonInformativeMenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isNonParsimonyInformative(s))
						bits.set(s);
				}
				bits.or(view.getSelectedSites());
				view.setSelectedSites(bits);
			}
		});
		controller.getSelectAllNonInformativeMenuItem().disableProperty().bind(view.emptyProperty());

		controller.getEnableAllTaxaMenuItem().setOnAction(e -> {
			var inputTaxa = view.getInputTaxa();
			if (inputTaxa != null) {
				var oldBits = view.getActiveTaxa();
				var newBits = BitSetUtils.asBitSet(NumberUtils.range(1, inputTaxa.getNtax() + 1));
				if (!oldBits.equals(newBits))
					view.getUndoManager().doAndAdd("enable all taxa", () -> view.setActiveTaxa(oldBits), () -> view.setActiveTaxa(newBits));
			}
		});
		controller.getEnableAllTaxaMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> view.getInputTaxa() == null || view.getActiveTaxa().cardinality() == view.getInputTaxa().getNtax(), view.activeTaxaProperty()));

		controller.getEnableSelectedTaxaOnlyMenuItem().setOnAction(e -> {
			var oldBits = view.getActiveTaxa();
			var newBits = view.getSelectedTaxa();
			if (!oldBits.equals(newBits))
				view.getUndoManager().doAndAdd("enable selected taxa only", () -> view.setActiveTaxa(oldBits), () -> view.setActiveTaxa(newBits));
		});
		controller.getEnableSelectedTaxaOnlyMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> view.getSelectedTaxa().cardinality() == 0, view.selectedTaxaProperty()));

		controller.getEnableSelectedTaxaMenuItem().setOnAction(e -> {
			var oldBits = view.getActiveTaxa();
			var newBits = BitSetUtils.union(view.getActiveTaxa(), view.getSelectedTaxa());
			if (!oldBits.equals(newBits))
				view.getUndoManager().doAndAdd("enable selected taxa", () -> view.setActiveTaxa(oldBits), () -> view.setActiveTaxa(newBits));
		});
		controller.getEnableSelectedTaxaMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> BitSetUtils.minus(view.getSelectedTaxa(), view.getActiveTaxa()).cardinality() == 0, view.selectedTaxaProperty(), view.activeTaxaProperty()));

		controller.getDisableSelectedTaxaMenuItem().setOnAction(e -> {
			var oldBits = view.getActiveTaxa();
			var newBits = BitSetUtils.minus(view.getActiveTaxa(), view.getSelectedTaxa());
			if (!oldBits.equals(newBits))
				view.getUndoManager().doAndAdd("disable selected taxa", () -> view.setActiveTaxa(oldBits), () -> view.setActiveTaxa(newBits));
		});
		controller.getDisableSelectedTaxaMenuItem().disableProperty().bind(controller.getEnableSelectedTaxaOnlyMenuItem().disableProperty());

		controller.getEnableAllSitesMenuItem().setOnAction(e -> {
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var oldBits = view.getActiveSites();
				var newBits = BitSetUtils.asBitSet(NumberUtils.range(1, inputCharacters.getNchar() + 1));
				if (!oldBits.equals(newBits))
					view.getUndoManager().doAndAdd("enable all sites", () -> view.setActiveSites(oldBits), () -> view.setActiveSites(newBits));
			}
		});
		controller.getEnableAllSitesMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> view.getInputCharacters() == null || view.getActiveSites().cardinality() == view.getInputCharacters().getNchar(), view.inputCharactersNodeValidProperty(), view.activeSitesProperty()));

		controller.getEnableSelectedSitesOnlyMenuItem().setOnAction(e -> {
			var oldBits = view.getActiveSites();
			var newBits = view.getSelectedSites();
			if (!oldBits.equals(newBits))
				view.getUndoManager().doAndAdd("enable selected sites only", () -> view.setActiveSites(oldBits), () -> view.setActiveSites(newBits));
		});
		controller.getEnableSelectedSitesOnlyMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> view.getSelectedSites().cardinality() == 0, view.selectedSitesProperty()));

		controller.getEnableSelectedSitesMenuItem().setOnAction(e -> {
			var oldBits = view.getActiveSites();
			var newBits = BitSetUtils.union(view.getActiveSites(), view.getActiveSites());
			if (!oldBits.equals(newBits))
				view.getUndoManager().doAndAdd("enable selected sites", () -> view.setActiveSites(oldBits), () -> view.setActiveSites(newBits));
		});
		controller.getEnableSelectedSitesMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> BitSetUtils.minus(view.getSelectedSites(), view.getActiveSites()).cardinality() == 0, view.selectedSitesProperty(), view.activeSitesProperty()));

		controller.getDisableSelectedSitesMenuItem().setOnAction(e -> {
			var oldBits = view.getActiveSites();
			var newBits = BitSetUtils.minus(view.getActiveSites(), view.getSelectedSites());
			if (!oldBits.equals(newBits))
				view.getUndoManager().doAndAdd("disable selected sites", () -> view.setActiveSites(oldBits), () -> view.setActiveSites(newBits));
		});
		controller.getDisableSelectedSitesMenuItem().disableProperty().bind(controller.getEnableSelectedSitesOnlyMenuItem().disableProperty());

		controller.getFilterMenu().disableProperty().bind(workflow.runningProperty());

		Platform.runLater(() -> {
			var taxonHBar = BasicFX.getScrollBar(controller.getTaxaListView(), Orientation.HORIZONTAL);
			if (taxonHBar != null) {
				controller.getLeftBottomPane().prefHeightProperty().bind(new When(taxonHBar.visibleProperty()).then(0).otherwise(16));
			}

			var taxonVBar = BasicFX.getScrollBar(controller.getTaxaListView(), Orientation.VERTICAL);
			if (taxonVBar != null) {
				controller.getVerticalScrollBar().visibleProperty().bind(taxonVBar.visibleProperty());
				controller.getVerticalScrollBar().minProperty().bind(taxonVBar.minProperty());
				controller.getVerticalScrollBar().maxProperty().bind(taxonVBar.maxProperty());
				controller.getVerticalScrollBar().visibleAmountProperty().bind(taxonVBar.visibleAmountProperty());
				taxonVBar.valueProperty().bindBidirectional(controller.getVerticalScrollBar().valueProperty());
			}
		});

		Platform.runLater(() -> updateTaxaListener.invalidated(null));
		Platform.runLater(() -> updateCanvasListener.invalidated(null));

		mainWindow.getWorkflow().runningProperty().addListener(e -> updateCharSetSelection(mainWindow, view, controller.getSetsMenu().getItems()));
	}

	public static void updateTaxaCellFactory(ListView<Taxon> listView, double unitHeight, Predicate<Taxon> isDisabled) {
		listView.setFixedCellSize(unitHeight);

		listView.setCellFactory(cell -> new ListCell<>() {
			@Override
			protected void updateItem(Taxon item, boolean empty) {
				try {
					super.updateItem(item, empty);
					if (empty) {
						setText(null);
						setGraphic(null);
					} else {
						setGraphic(null);
						var tooltip = new Tooltip(item.getName());
						tooltip.setFont(Font.font(tooltip.getFont().getFamily(), 11));
						setTooltip(tooltip);
						if (isDisabled.test(item))
							setStyle(String.format("-fx-text-fill: gray; -fx-font-size: %.1f;", Math.min(18, 0.6 * unitHeight)));
						else
							setStyle(String.format("-fx-font-size: %.1f;", Math.min(18, 0.6 * unitHeight)));
						setText(item.getName());
						setAlignment(Pos.CENTER_LEFT);
					}
				} catch (Exception ex) {
					Basic.caught(ex);
				}
			}
		});
		Platform.runLater(() -> {
			listView.refresh();
			listView.requestLayout();
		});
	}

	@Override
	public void setupMenuItems() {
		mainController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());
		mainController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());

		mainController.getCopyMenuItem().setOnAction(a -> {
			var buf = new StringBuilder();
			for (var t = 1; t <= view.getInputTaxa().getNtax(); t++) {
				if (view.getSelectedTaxa().isEmpty() || view.getSelectedTaxa().get(t)) {
					buf.append(">").append(view.getInputTaxa().get(t).getName()).append("\n");
					for (var s = 1; s <= view.getInputCharacters().getNchar(); s++) {
						if (view.getSelectedSites().isEmpty() || view.getSelectedSites().get(s)) {
							buf.append(view.getInputCharacters().get(t, s));
						}
					}
					buf.append("\n");
				}
			}
			var clipboardContent = new ClipboardContent();
			clipboardContent.putString(buf.toString());
			Clipboard.getSystemClipboard().setContent(clipboardContent);
		});
		mainController.getCopyMenuItem().disableProperty().bind(
				Bindings.createBooleanBinding(() -> view.getSelectedTaxa().isEmpty() && view.getSelectedSites().isEmpty(), view.selectedTaxaProperty(), view.selectedSitesProperty()));

		mainController.getPasteMenuItem().setDisable(true);
		mainController.getCutMenuItem().setDisable(true);

		mainController.getSelectAllMenuItem().setOnAction(e -> {
			var inputTaxa = view.getInputTaxa();
			if (inputTaxa != null) {
				var bits = BitSetUtils.asBitSet(NumberUtils.range(1, inputTaxa.getNtax() + 1));
				view.setSelectedTaxa(bits);
			}
			var inputCharacters = view.getInputCharacters();
			if (inputCharacters != null) {
				var bits = BitSetUtils.asBitSet(NumberUtils.range(1, inputCharacters.getNchar() + 1));
				view.setSelectedSites(bits);
			}
		});

		mainController.getSelectNoneMenuItem().setOnAction(e -> {
			view.setSelectedTaxa(new BitSet());
			view.setSelectedSites(new BitSet());
		});
		mainController.getSelectNoneMenuItem().disableProperty().bind(
				Bindings.createBooleanBinding(() -> view.getSelectedTaxa().cardinality() == 0
													&& view.getSelectedSites().cardinality() == 0, view.selectedTaxaProperty(), view.selectedSitesProperty()));
		mainController.getSelectInverseMenuItem().setOnAction(e -> {
			if (view.getSelectedTaxa().cardinality() > 0) {
				var inputTaxa = view.getInputTaxa();
				if (inputTaxa != null) {
					var bits = BitSetUtils.minus(BitSetUtils.asBitSet(NumberUtils.range(1, inputTaxa.getNtax() + 1)), view.getSelectedTaxa());
					view.setSelectedTaxa(bits);
				}
			}
			if (view.getSelectedSites().cardinality() > 0) {
				var inputCharacters = view.getInputCharacters();
				if (inputCharacters != null) {
					var bits = BitSetUtils.minus(BitSetUtils.asBitSet(NumberUtils.range(1, inputCharacters.getNchar() + 1)), view.getSelectedSites());
					view.setSelectedSites(bits);
				}
			}
		});

		mainController.getIncreaseFontSizeMenuItem().setOnAction(e -> {
			if (!controller.getExpandHorizontallyButton().isDisabled())
				controller.getExpandHorizontallyButton().fire();
			if (!controller.getExpandVerticallyButton().isDisabled())
				controller.getExpandVerticallyButton().fire();
		});
		mainController.getIncreaseFontSizeMenuItem().disableProperty().bind(controller.getExpandHorizontallyButton().disabledProperty().and(controller.getExpandVerticallyButton().disabledProperty()));

		mainController.getDecreaseFontSizeMenuItem().setOnAction(e -> {
			if (!controller.getContractHorizontallyButton().isDisabled())
				controller.getContractHorizontallyButton().fire();
			if (!controller.getContractVerticallyButton().isDisabled())
				controller.getContractVerticallyButton().fire();
		});
		mainController.getDecreaseFontSizeMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disabledProperty().and(controller.getContractVerticallyButton().disabledProperty()));

		mainController.getSelectInverseMenuItem().disableProperty().bind(mainController.getSelectNoneMenuItem().disableProperty());
		mainController.getSelectCompatibleSitesMenuItem().setOnAction(controller.getSelectCompatibleMenuItem().getOnAction());
		mainController.getSelectCompatibleSitesMenuItem().disableProperty().bind(controller.getSelectCompatibleMenuItem().disableProperty());

		ExportUtils.setup(mainWindow, view.getWorkingCharactersNode().getDataBlock().getNode(), view.emptyProperty());
	}

	public static void updateCharSetSelection(MainWindow mainWindow, AlignmentView view, List<MenuItem> items) {
		items.removeAll(items.stream().filter(t -> t.getText() != null && t.getText().startsWith("CharSet")).collect(Collectors.toList()));

		var taxaBlock = mainWindow.getWorkflow().getInputTaxaBlock();
		if (taxaBlock != null && taxaBlock.getSetsBlock() != null && !taxaBlock.getSetsBlock().getCharSets().isEmpty()) {
			for (var set : taxaBlock.getSetsBlock().getCharSets()) {
				var menuItem = new MenuItem("CharSet " + set.getName());
				menuItem.setOnAction(e -> {
					var bits = BitSetUtils.copy(set);
					bits.or(view.getSelectedSites());
					view.setSelectedSites(bits);
				});
				menuItem.disableProperty().bind(mainWindow.emptyProperty());
				items.add(menuItem);
			}
		}
	}

	public FindToolBar getFindToolBar() {
		return null;
	}

	@Override
	public boolean allowFindReplace() {
		return false;
	}

	private static void setupColorSchemeMenu(ObjectProperty<ColorScheme> colorSchemeProperty, MenuButton menuButton) {
		var toggleGroup = new ToggleGroup();
		for (var colorScheme : ColorScheme.values()) {
			var menuItem = new RadioMenuItem(colorScheme.name());
			menuItem.selectedProperty().addListener((v, o, n) -> {
				if (n)
					colorSchemeProperty.set(colorScheme);
			});
			toggleGroup.getToggles().add(menuItem);
			menuButton.getItems().add(menuItem);
			if (colorScheme.equals(colorSchemeProperty.get()))
				menuItem.setSelected(true);
		}
	}
}
