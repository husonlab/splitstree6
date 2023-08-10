/*
 *  AlignmentViewPresenter.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Font;
import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.util.*;
import splitstree6.algorithms.utils.CharactersUtilities;
import splitstree6.splits.ASplit;
import splitstree6.data.parts.CharactersType;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
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

	private final AlignmentView alignmentView;
	private final AlignmentViewController controller;
	private final MainWindowController mainWindowController;
	private final Workflow workflow;

	private boolean colorSchemeSet = false;

	public AlignmentViewPresenter(MainWindow mainWindow, AlignmentView alignmentView) {
		this.alignmentView = alignmentView;
		this.workflow = mainWindow.getWorkflow();

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

		InvalidationListener updateTaxaListener = e -> {
			controller.getTaxaListView().getItems().clear();
			if (alignmentView.getInputTaxa() != null) {
				for (var taxon : alignmentView.getInputTaxa().getTaxa()) {
					controller.getTaxaListView().getItems().add(taxon);
				}
			}
		};
		alignmentView.inputTaxaNodeValidProperty().addListener(updateTaxaListener);

		var canvasWidth = new SimpleDoubleProperty();
		canvasWidth.bind(controller.getHorizontalScrollBar().widthProperty());
		var canvasHeight = new SimpleDoubleProperty();
		canvasHeight.bind(controller.getVerticalScrollBar().heightProperty());

		var alignmentDrawer = new AlignmentDrawer(controller.getImageGroup(), controller.getCanvasGroup(), mainWindowController.getBottomFlowPane());

		updateCanvasListener = e -> {
			var width = canvasWidth.get();
			var height = canvasHeight.get();

			AxisAndScrollBarUpdate.update(controller.getAxis(), controller.getHorizontalScrollBar(), width,
					alignmentView.getOptionUnitWidth(), alignmentView.getInputCharacters() != null ? alignmentView.getInputCharacters().getNchar() : 0, alignmentView);

			alignmentDrawer.updateCanvas(width, height, alignmentView.getInputTaxa(), alignmentView.getInputCharacters(), alignmentView.getConsensusSequence(),
					alignmentView.getOptionColorScheme(), alignmentView.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(),
					alignmentView.getActiveTaxa(), alignmentView.getActiveSites());


			AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), alignmentView.getInputCharacters(),
					alignmentView.getActiveSites(), alignmentView.getSelectedSites());

			updateTaxaCellFactory(controller.getTaxaListView(), alignmentView.getOptionUnitHeight(), alignmentView::isDisabled);

			alignmentDrawer.updateTaxaSelection(controller.getTaxaSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(),
					alignmentView.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), alignmentView.getSelectedTaxa());

			alignmentDrawer.updateSiteSelection(controller.getSiteSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(),
					alignmentView.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), alignmentView.getSelectedSites());

			if (false)
				alignmentDrawer.updateTaxaSelection(controller.getTaxaSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(),
						alignmentView.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), alignmentView.getSelectedTaxa());

			controller.getSelectionLabel().setText(alignmentView.createSelectionString());

			// update block increment for vertical scroll bar:
			{
				var listViewVerticalScrollBar = BasicFX.getScrollBar(controller.getTaxaListView(), Orientation.VERTICAL);
				if (listViewVerticalScrollBar != null) {
					var countVisible = controller.getTaxaListView().getHeight() / controller.getTaxaListView().getFixedCellSize();
					if (alignmentView.getInputTaxa() != null && countVisible < alignmentView.getInputTaxa().getNtax()) {
						controller.getVerticalScrollBar().setBlockIncrement(countVisible / (alignmentView.getInputTaxa().getNtax() - countVisible));
					} else
						controller.getVerticalScrollBar().setBlockIncrement(1.0);
				}
			}
		};

		canvasWidth.addListener(updateCanvasListener);
		canvasHeight.addListener(updateCanvasListener);

		alignmentView.optionUnitWidthProperty().addListener(updateCanvasListener);
		alignmentView.optionUnitHeightProperty().addListener(updateCanvasListener);

		alignmentView.activeSitesProperty().addListener(updateCanvasListener);
		alignmentView.activeTaxaProperty().addListener(updateCanvasListener);

		alignmentView.selectedSitesProperty().addListener(e -> {
			alignmentDrawer.updateSiteSelection(controller.getSiteSelectionGroup(), alignmentView.getInputTaxa(), alignmentView.getInputCharacters(),
					alignmentView.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), alignmentView.getSelectedSites());
			AxisAndScrollBarUpdate.updateSelection(controller.getRightTopPane(), controller.getAxis(), alignmentView.getInputCharacters(), alignmentView.getActiveSites(), alignmentView.getSelectedSites());
			controller.getSelectionLabel().setText(alignmentView.createSelectionString());
		});

		alignmentView.selectedTaxaProperty().addListener((v, o, n) -> {
			if (!inSelectionUpdate.get()) {
				try {
					inSelectionUpdate.set(true);
					controller.getTaxaListView().getSelectionModel().clearSelection();
					var inputTaxa = alignmentView.getInputTaxa();
					if (inputTaxa != null) {

						for (var taxon : controller.getTaxaListView().getItems()) {
							var t = inputTaxa.indexOf(taxon);
							if (t != -1 && n.get(t))
								controller.getTaxaListView().getSelectionModel().select(taxon);
						}
						alignmentDrawer.updateTaxaSelection(controller.getTaxaSelectionGroup(), inputTaxa, alignmentView.getInputCharacters(),
								alignmentView.getOptionUnitHeight(), controller.getVerticalScrollBar(), controller.getAxis(), alignmentView.getSelectedTaxa());
					}
					controller.getSelectionLabel().setText(alignmentView.createSelectionString());
				} finally {
					inSelectionUpdate.set(false);
				}
			}
		});

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
					} else if (inputCharacters.getDataType() == CharactersType.Standard) {
						if (!colorSchemeSet || alignmentView.getOptionColorScheme() != ColorScheme.Nucleotide && alignmentView.getOptionColorScheme() != ColorScheme.Random && alignmentView.getOptionColorScheme() != ColorScheme.None) {
							alignmentView.setOptionColorScheme(ColorScheme.Binary);
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

		controller.getHorizontalScrollBar().valueProperty().addListener(updateCanvasListener);

		controller.getHorizontalScrollBar().valueProperty().addListener((v, o, n) -> {
			var diff = n.doubleValue() - controller.getAxis().getLowerBound();
			controller.getAxis().setLowerBound(controller.getAxis().getLowerBound() + diff);
			controller.getAxis().setUpperBound(controller.getAxis().getUpperBound() + diff);
		});

		controller.getVerticalScrollBar().valueProperty().addListener(updateCanvasListener);

		controller.getExpandHorizontallyButton().setOnAction(e -> alignmentView.setOptionUnitWidth(1.2 * alignmentView.getOptionUnitWidth()));
		controller.getExpandHorizontallyButton().disableProperty().bind(alignmentView.optionUnitWidthProperty().greaterThan(64));

		controller.getContractHorizontallyButton().setOnAction(e -> alignmentView.setOptionUnitWidth(1 / 1.2 * alignmentView.getOptionUnitWidth()));
		controller.getContractHorizontallyButton().disableProperty().bind(alignmentView.optionUnitWidthProperty().lessThan(0.01));

		controller.getExpandVerticallyButton().setOnAction(e -> alignmentView.setOptionUnitHeight(1.2 * alignmentView.getOptionUnitHeight()));
		controller.getExpandVerticallyButton().disableProperty().bind(alignmentView.optionUnitHeightProperty().greaterThan(64));

		controller.getContractVerticallyButton().setOnAction(e -> alignmentView.setOptionUnitHeight(1 / 1.2 * alignmentView.getOptionUnitHeight()));
		controller.getContractVerticallyButton().disableProperty().bind(alignmentView.optionUnitHeightProperty().lessThan(0.01));

		controller.getZoomToFitButton().setOnAction(e -> {
			if (alignmentView.getInputCharacters().getNchar() * alignmentView.getOptionUnitWidth() > canvasWidth.get()
				|| alignmentView.getInputCharacters().getNtax() * alignmentView.getOptionUnitHeight() > canvasHeight.get()) {
				controller.getVerticalScrollBar().setValue(controller.getVerticalScrollBar().getMin());
				controller.getHorizontalScrollBar().setValue(controller.getHorizontalScrollBar().getMin());
				alignmentView.setOptionUnitWidth(Math.min(AlignmentView.DEFAULT_UNIT_WIDTH, canvasWidth.get() / alignmentView.getInputCharacters().getNchar()));
				alignmentView.setOptionUnitHeight(Math.min(AlignmentView.DEFAULT_UNIT_HEIGHT, canvasHeight.get() / alignmentView.getInputCharacters().getNtax()));
			} else {
				alignmentView.setOptionUnitWidth(AlignmentView.DEFAULT_UNIT_WIDTH);
				alignmentView.setOptionUnitHeight(AlignmentView.DEFAULT_UNIT_HEIGHT);
			}
		});
		controller.getZoomToFitButton().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not());

		controller.getSelectAllMenuItem().setOnAction(e ->
				alignmentView.setSelectedSites(BitSetUtils.asBitSet(BitSetUtils.range(1, alignmentView.getInputCharacters().getNchar() + 1))));
		controller.getSelectAllMenuItem().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not());

		controller.getSelectNoneMenuItem().setOnAction(e -> alignmentView.setSelectedSites(new BitSet()));
		controller.getSelectNoneMenuItem().disableProperty().bind(alignmentView.inputCharactersNodeValidProperty().not().or(Bindings.createBooleanBinding(() -> alignmentView.getSelectedSites().cardinality() == 0, alignmentView.selectedSitesProperty())));

		controller.getInvertSelectionMenuItem().setOnAction(e -> alignmentView.setSelectedSites(BitSetUtils.getComplement(alignmentView.getSelectedSites(), 1, this.alignmentView.getInputCharacters().getNchar() + 1)));
		controller.getInvertSelectionMenuItem().disableProperty().bind(this.alignmentView.emptyProperty());

		controller.getSelectCompatibleMenuItem().setOnAction(e -> {
			var split = new ASplit(this.alignmentView.getSelectedTaxa(), this.alignmentView.getInputTaxa().getNtax());
			var compatible = CharactersUtilities.computeAllCompatible(this.alignmentView.getInputCharacters(), split);
			System.err.printf("Compatible sites (%,d): %s%n ", compatible.cardinality(), StringUtils.toString(compatible));
			this.alignmentView.getSelectedSites().clear();
			if (compatible.cardinality() > 0) {
				Platform.runLater(() -> this.alignmentView.setSelectedSites(compatible));
			}
		});
		controller.getSelectCompatibleMenuItem().disableProperty().bind(this.alignmentView.emptyProperty()
				.or(Bindings.createBooleanBinding(() -> this.alignmentView.getSelectedTaxa().cardinality() == 0 || this.alignmentView.getSelectedTaxa().cardinality() == this.alignmentView.getInputTaxa().getNtax(), this.alignmentView.selectedTaxaProperty())));

		controller.getSelectIncompatibleMenuItem().setOnAction(e -> {
			var split = new ASplit(this.alignmentView.getSelectedTaxa(), this.alignmentView.getInputTaxa().getNtax());
			var compatible = CharactersUtilities.computeAllCompatible(this.alignmentView.getInputCharacters(), split);
			var incompatible = BitSetUtils.getComplement(compatible, 1, this.alignmentView.getInputCharacters().getNchar() + 1);
			System.err.printf("Incompatible sites (%,d): %s%n ", compatible.cardinality(), StringUtils.toString(incompatible));
			this.alignmentView.getSelectedSites().clear();
			if (incompatible.cardinality() > 0) {
				Platform.runLater(() -> this.alignmentView.setSelectedSites(incompatible));
			}
		});
		controller.getSelectIncompatibleMenuItem().disableProperty().bind(controller.getSelectCompatibleMenuItem().disableProperty());

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

		controller.getSelectSynapomorphiesMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			var bits = new BitSet();
			for (var s = 1; s <= inputCharacters.getNchar(); s++) {
				if (inputCharacters.isSynapomorphy(s, alignmentView.getSelectedTaxa()))
					bits.set(s);
			}
			bits.or(alignmentView.getSelectedSites());
			alignmentView.setSelectedSites(bits);
		});
		controller.getSelectSynapomorphiesMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getSelectedTaxa().cardinality() == 0, alignmentView.selectedTaxaProperty()));

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

		controller.getSelectMissingMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = new BitSet();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (inputCharacters.isMissingSite(s))
						bits.set(s);
				}
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectMissingMenuItem().disableProperty().bind(alignmentView.emptyProperty());

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

		controller.getSelectMajorityGapOrMissingMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
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
				bits.or(alignmentView.getSelectedSites());
				alignmentView.setSelectedSites(bits);
			}
		});
		controller.getSelectMajorityGapOrMissingMenuItem().disableProperty().bind(alignmentView.emptyProperty());


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
				var oldBits = alignmentView.getActiveTaxa();
				var newBits = BitSetUtils.asBitSet(NumberUtils.range(1, inputTaxa.getNtax() + 1));
				if (!oldBits.equals(newBits))
					alignmentView.getUndoManager().doAndAdd("enable all taxa", () -> alignmentView.setActiveTaxa(oldBits), () -> alignmentView.setActiveTaxa(newBits));
			}
		});
		controller.getEnableAllTaxaMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getInputTaxa() == null || alignmentView.getActiveTaxa().cardinality() == alignmentView.getInputTaxa().getNtax(), alignmentView.activeTaxaProperty()));

		controller.getEnableSelectedTaxaOnlyMenuItem().setOnAction(e -> {
			var oldBits = alignmentView.getActiveTaxa();
			var newBits = alignmentView.getSelectedTaxa();
			if (!oldBits.equals(newBits))
				alignmentView.getUndoManager().doAndAdd("enable selected taxa only", () -> alignmentView.setActiveTaxa(oldBits), () -> alignmentView.setActiveTaxa(newBits));
		});
		controller.getEnableSelectedTaxaOnlyMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getSelectedTaxa().cardinality() == 0, alignmentView.selectedTaxaProperty()));

		controller.getEnableSelectedTaxaMenuItem().setOnAction(e -> {
			var oldBits = alignmentView.getActiveTaxa();
			var newBits = BitSetUtils.union(alignmentView.getActiveTaxa(), alignmentView.getSelectedTaxa());
			if (!oldBits.equals(newBits))
				alignmentView.getUndoManager().doAndAdd("enable selected taxa", () -> alignmentView.setActiveTaxa(oldBits), () -> alignmentView.setActiveTaxa(newBits));
		});
		controller.getEnableSelectedTaxaMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> BitSetUtils.minus(alignmentView.getSelectedTaxa(), alignmentView.getActiveTaxa()).cardinality() == 0, alignmentView.selectedTaxaProperty(), alignmentView.activeTaxaProperty()));

		controller.getDisableSelectedTaxaMenuItem().setOnAction(e -> {
			var oldBits = alignmentView.getActiveTaxa();
			var newBits = BitSetUtils.minus(alignmentView.getActiveTaxa(), alignmentView.getSelectedTaxa());
			if (!oldBits.equals(newBits))
				alignmentView.getUndoManager().doAndAdd("disable selected taxa", () -> alignmentView.setActiveTaxa(oldBits), () -> alignmentView.setActiveTaxa(newBits));
		});
		controller.getDisableSelectedTaxaMenuItem().disableProperty().bind(controller.getEnableSelectedTaxaOnlyMenuItem().disableProperty());

		controller.getEnableAllSitesMenuItem().setOnAction(e -> {
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var oldBits = alignmentView.getActiveSites();
				var newBits = BitSetUtils.asBitSet(NumberUtils.range(1, inputCharacters.getNchar() + 1));
				if (!oldBits.equals(newBits))
					alignmentView.getUndoManager().doAndAdd("enable all sites", () -> alignmentView.setActiveSites(oldBits), () -> alignmentView.setActiveSites(newBits));
			}
		});
		controller.getEnableAllSitesMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getInputCharacters() == null || alignmentView.getActiveSites().cardinality() == alignmentView.getInputCharacters().getNchar(), alignmentView.inputCharactersNodeValidProperty(), alignmentView.activeSitesProperty()));

		controller.getEnableSelectedSitesOnlyMenuItem().setOnAction(e -> {
			var oldBits = alignmentView.getActiveSites();
			var newBits = alignmentView.getSelectedSites();
			if (!oldBits.equals(newBits))
				alignmentView.getUndoManager().doAndAdd("enable selected sites only", () -> alignmentView.setActiveSites(oldBits), () -> alignmentView.setActiveSites(newBits));
		});
		controller.getEnableSelectedSitesOnlyMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> alignmentView.getSelectedSites().cardinality() == 0, alignmentView.selectedSitesProperty()));

		controller.getEnableSelectedSitesMenuItem().setOnAction(e -> {
			var oldBits = alignmentView.getActiveSites();
			var newBits = BitSetUtils.union(alignmentView.getActiveSites(), alignmentView.getActiveSites());
			if (!oldBits.equals(newBits))
				alignmentView.getUndoManager().doAndAdd("enable selected sites", () -> alignmentView.setActiveSites(oldBits), () -> alignmentView.setActiveSites(newBits));
		});
		controller.getEnableSelectedSitesMenuItem().disableProperty().bind(Bindings.createBooleanBinding(() -> BitSetUtils.minus(alignmentView.getSelectedSites(), alignmentView.getActiveSites()).cardinality() == 0, alignmentView.selectedSitesProperty(), alignmentView.activeSitesProperty()));

		controller.getDisableSelectedSitesMenuItem().setOnAction(e -> {
			var oldBits = alignmentView.getActiveSites();
			var newBits = BitSetUtils.minus(alignmentView.getActiveSites(), alignmentView.getSelectedSites());
			if (!oldBits.equals(newBits))
				alignmentView.getUndoManager().doAndAdd("disable selected sites", () -> alignmentView.setActiveSites(oldBits), () -> alignmentView.setActiveSites(newBits));
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

		mainWindow.getWorkflow().runningProperty().addListener(e -> updateCharSetSelection(mainWindow, alignmentView, controller.getSetsMenu().getItems()));
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
		mainWindowController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainWindowController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());
		mainWindowController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainWindowController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());

		mainWindowController.getCopyMenuItem().setOnAction(a -> {
			var buf = new StringBuilder();
			for (var t = 1; t <= alignmentView.getInputTaxa().getNtax(); t++) {
				if (alignmentView.getSelectedTaxa().isEmpty() || alignmentView.getSelectedTaxa().get(t)) {
					buf.append(">").append(alignmentView.getInputTaxa().get(t).getName()).append("\n");
					for (var s = 1; s <= alignmentView.getInputCharacters().getNchar(); s++) {
						if (alignmentView.getSelectedSites().isEmpty() || alignmentView.getSelectedSites().get(s)) {
							buf.append(alignmentView.getInputCharacters().get(t, s));
						}
					}
					buf.append("\n");
				}
			}
			var clipboardContent = new ClipboardContent();
			clipboardContent.putString(buf.toString());
			Clipboard.getSystemClipboard().setContent(clipboardContent);
		});
		mainWindowController.getCopyMenuItem().disableProperty().bind(
				Bindings.createBooleanBinding(() -> alignmentView.getSelectedTaxa().isEmpty() && alignmentView.getSelectedSites().isEmpty(), alignmentView.selectedTaxaProperty(), alignmentView.selectedSitesProperty()));

		mainWindowController.getPasteMenuItem().setDisable(true);
		mainWindowController.getCutMenuItem().setDisable(true);

		mainWindowController.getSelectAllMenuItem().setOnAction(e -> {
			var inputTaxa = alignmentView.getInputTaxa();
			if (inputTaxa != null) {
				var bits = BitSetUtils.asBitSet(NumberUtils.range(1, inputTaxa.getNtax() + 1));
				alignmentView.setSelectedTaxa(bits);
			}
			var inputCharacters = alignmentView.getInputCharacters();
			if (inputCharacters != null) {
				var bits = BitSetUtils.asBitSet(NumberUtils.range(1, inputCharacters.getNchar() + 1));
				alignmentView.setSelectedSites(bits);
			}
		});

		mainWindowController.getSelectNoneMenuItem().setOnAction(e -> {
			alignmentView.setSelectedTaxa(new BitSet());
			alignmentView.setSelectedSites(new BitSet());
		});
		mainWindowController.getSelectNoneMenuItem().disableProperty().bind(
				Bindings.createBooleanBinding(() -> alignmentView.getSelectedTaxa().cardinality() == 0
													&& alignmentView.getSelectedSites().cardinality() == 0, alignmentView.selectedTaxaProperty(), alignmentView.selectedSitesProperty()));
		mainWindowController.getSelectInverseMenuItem().setOnAction(e -> {
			if (alignmentView.getSelectedTaxa().cardinality() > 0) {
				var inputTaxa = alignmentView.getInputTaxa();
				if (inputTaxa != null) {
					var bits = BitSetUtils.minus(BitSetUtils.asBitSet(NumberUtils.range(1, inputTaxa.getNtax() + 1)), alignmentView.getSelectedTaxa());
					alignmentView.setSelectedTaxa(bits);
				}
			}
			if (alignmentView.getSelectedSites().cardinality() > 0) {
				var inputCharacters = alignmentView.getInputCharacters();
				if (inputCharacters != null) {
					var bits = BitSetUtils.minus(BitSetUtils.asBitSet(NumberUtils.range(1, inputCharacters.getNchar() + 1)), alignmentView.getSelectedSites());
					alignmentView.setSelectedSites(bits);
				}
			}
		});

		mainWindowController.getSelectInverseMenuItem().disableProperty().bind(mainWindowController.getSelectNoneMenuItem().disableProperty());

		mainWindowController.getSelectCompatibleSitesMenuItem().setOnAction(controller.getSelectCompatibleMenuItem().getOnAction());

		mainWindowController.getSelectCompatibleSitesMenuItem().disableProperty().bind(controller.getSelectCompatibleMenuItem().disableProperty());
	}

	public static void updateCharSetSelection(MainWindow mainWindow, AlignmentView view, List<MenuItem> items) {
		items.removeAll(items.stream().filter(t -> t.getText() != null && t.getText().startsWith("CharSet")).collect(Collectors.toList()));

		var taxaBlock = mainWindow.getWorkflow().getInputTaxaBlock();
		if (taxaBlock != null && taxaBlock.getSetsBlock() != null && taxaBlock.getSetsBlock().getCharSets().size() > 0) {
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
}
