/*
 *  AlignmentView.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.AService;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.window.NotificationManager;
import jloda.util.BitSetUtils;
import jloda.util.Single;
import splitstree6.algorithms.characters.characters2characters.CharactersTaxaFilter;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;

/**
 * displays the input alignment
 * Daniel Huson, 4.2022
 */
public class AlignmentView implements IView {
	public static final double DEFAULT_UNIT_WIDTH = 18;
	public static final double DEFAULT_UNIT_HEIGHT = 24;

	private final UndoManager undoManager = new UndoManager();

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final AlignmentViewController controller;
	private final AlignmentViewPresenter presenter;

	private final InvalidationListener invalidationListener;
	private final InvalidationListener selectionListener;

	private final ObjectProperty<DataNode<TaxaBlock>> inputTaxaNode = new SimpleObjectProperty<>(this, "inputTaxaNode");
	private final ObjectProperty<AlgorithmNode<TaxaBlock, TaxaBlock>> taxaFilterNode = new SimpleObjectProperty<>(this, "taxaFilterNode");
	private final ObjectProperty<DataNode<CharactersBlock>> inputCharactersNode = new SimpleObjectProperty<>(this, "inputCharactersBlock");
	private final ObjectProperty<AlgorithmNode<CharactersBlock, CharactersBlock>> charactersTaxaFilterNode = new SimpleObjectProperty<>(this, "charactersTaxaFilterNode");
	private final ObjectProperty<DataNode<TaxaBlock>> workingTaxaNode = new SimpleObjectProperty<>(this, "workingTaxaNode");
	private final ObjectProperty<DataNode<CharactersBlock>> workingCharactersNode = new SimpleObjectProperty<>(this, "workingCharactersBlock");

	private final ObjectProperty<char[]> consensusSequence = new SimpleObjectProperty<>(this, "consensusSequence");

	private final BooleanProperty inputTaxaNodeValid = new SimpleBooleanProperty(this, "inputTaxaNodeValid", false);
	private final BooleanProperty taxaFilterValid = new SimpleBooleanProperty(this, "taxaFilterValid", false);
	private final BooleanProperty inputCharactersNodeValid = new SimpleBooleanProperty(this, "inputCharactersNodeValid", false);
	private final BooleanProperty charactersTaxaFilterValid = new SimpleBooleanProperty(this, "charactersTaxaFilterValid", false);
	private final BooleanProperty workingTaxaNodeValid = new SimpleBooleanProperty(this, "workingTaxaNodeValid", false);
	private final BooleanProperty workingCharactersNodeValid = new SimpleBooleanProperty(this, "workingCharactersNodeValid", false);

	private final ObjectProperty<BitSet> selectedTaxa = new SimpleObjectProperty<>(this, "selectedTaxa", new BitSet()); // selected taxon ids, based on input taxa
	private final ObjectProperty<BitSet> selectedSites = new SimpleObjectProperty<>(this, "selectedSites", new BitSet()); // selected sites, based input characters

	private final ObjectProperty<BitSet> activeTaxa = new SimpleObjectProperty<>(this, "activeTaxa", new BitSet()); // active taxa ids, based on input taxa
	private final ObjectProperty<BitSet> activeSites = new SimpleObjectProperty<>(this, "activeSites", new BitSet()); // active sites, based input characters

	private final BooleanProperty nucleotideData = new SimpleBooleanProperty(this, "nucleotideData");

	private final ObjectProperty<ColorScheme> optionColorScheme = new SimpleObjectProperty<>(this, "optionColorScheme", ColorScheme.None);
	private final DoubleProperty optionUnitWidth = new SimpleDoubleProperty(this, "optionUnitWidth", DEFAULT_UNIT_WIDTH);
	private final DoubleProperty optionUnitHeight = new SimpleDoubleProperty(this, "optionUnitHeight", DEFAULT_UNIT_HEIGHT);

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
				n.setGraphic(MaterialIcons.graphic("format_align_justify"));
			}
		});

		empty.bind(mainWindow.emptyProperty());

		setViewTab(viewTab);

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));

		inputTaxaNodeValid.addListener((v, o, n) -> {
			if (n) {
				setActiveTaxa(BitSetUtils.asBitSet(BitSetUtils.range(1, inputTaxaNode.get().getDataBlock().getNtax() + 1)));
			}
			selectedTaxa.set(new BitSet());
		});

		var ignoreActiveTaxaUpdate = new Single<>(false);

		workingTaxaNodeValid.addListener((v, o, n) -> {
			if (n) {
				var bits = new BitSet();
				var inputTaxa = getInputTaxa();
				var workingTaxa = getWorkingTaxa();
				if (inputTaxa != null && workingTaxa != null) {
					for (var t = 1; t <= inputTaxa.getNtax(); t++) {
						if (workingTaxa.indexOf(inputTaxa.get(t)) != -1)
							bits.set(t);
					}
					try {
						ignoreActiveTaxaUpdate.set(true);
						setActiveTaxa(bits);
					} finally {
						ignoreActiveTaxaUpdate.set(false);
					}
				}
			}
		});

		inputCharactersNodeValid.addListener((v, o, n) -> {
			if (n) {
				var inputCharacters = getInputCharacters();
				if (inputCharacters != null) {
					setActiveSites(BitSetUtils.asBitSet(BitSetUtils.range(1, inputCharacters.getNchar() + 1)));
					nucleotideData.set(inputCharacters.getDataType() == CharactersType.DNA || inputCharacters.getDataType() == CharactersType.RNA);
					consensusSequence.set(null);
					AService.run(inputCharacters::computeConsensusSequence, consensusSequence::set, e -> NotificationManager.showError("Consensus failed: " + e));
				}
			}
			setSelectedSites(new BitSet());
		});

		var inTaxaSelection = new Single<>(false);
		selectionListener = e -> {
			if (!inTaxaSelection.get()) {
				try {
					inTaxaSelection.set(true);

					var inputTaxa = getInputTaxa();
					if (inputTaxa != null) {

						var bits = new BitSet();
						for (var t = 1; t <= inputTaxa.getNtax(); t++) {
							if (mainWindow.getTaxonSelectionModel().isSelected(inputTaxa.get(t)))
								bits.set(t);
						}
						setSelectedTaxa(bits);
					}
				} finally {
					inTaxaSelection.set(false);
				}
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));

		selectedTaxa.addListener((v, o, n) -> {
			if (!inTaxaSelection.get()) {
				try {
					inTaxaSelection.set(true);
					var inputTaxa = getInputTaxa();
					var workingTaxa = getWorkingTaxa();
					if (inputTaxa != null && workingTaxa != null) {
						for (var t = 1; t <= inputTaxa.getNtax(); t++) {
							var taxon = inputTaxa.get(t);
							if (workingTaxa.indexOf(taxon) != -1) {
								if (n.get(t))
									mainWindow.getTaxonSelectionModel().select(taxon);
								else
									mainWindow.getTaxonSelectionModel().clearSelection(taxon);
							}
						}
					}
				} finally {
					inTaxaSelection.set(false);
				}
			}
		});

		activeTaxa.addListener((v, o, n) -> {
			if (!ignoreActiveTaxaUpdate.get()) {
				var inputTaxa = getInputTaxa();
				var taxaFilter = getTaxaFilter();
				if (inputTaxa != null && taxaFilter != null) {
					var disable = new HashSet<String>();
					for (var t = 1; t <= inputTaxa.getNtax(); t++) {
						if (!n.get(t))
							disable.add(inputTaxa.getLabel(t));
					}
					taxaFilter.clear();
					taxaFilter.setDisabled(disable, true);
					getTaxaFilterNode().restart();
				}
			}
		});

		activeSites.addListener((v, o, n) -> {
			var inputCharacters = getInputCharacters();
			var charactersFilter = getCharactersTaxaFilter();
			if (inputCharacters != null && charactersFilter != null) {
				var disable = new HashSet<Integer>();
				for (var s = 1; s <= inputCharacters.getNchar(); s++) {
					if (!n.get(s))
						disable.add(s);
				}
				charactersFilter.clear();
				charactersFilter.setOptionDisabledCharacters(disable.stream().mapToInt(Integer::intValue).toArray());
				getCharactersTaxaFilterNode().restart();
			}
		});

		var workflow = mainWindow.getWorkflow();
		invalidationListener = e -> {
			if (workflow.getInputTaxaNode() != null) {
				if (workflow.getInputTaxaNode() != getInputTaxaNode()) {
					inputTaxaNode.set(workflow.getInputTaxaNode());
					inputTaxaNodeValid.bind(inputTaxaNode.get().validProperty());
				}
			} else {
				inputTaxaNode.set(null);
				inputTaxaNodeValid.bind(new SimpleBooleanProperty(false));
			}
			if (workflow.getInputTaxaFilterNode() != null) {
				if (workflow.getInputTaxaFilterNode() != getTaxaFilterNode()) {
					taxaFilterNode.set(workflow.getInputTaxaFilterNode());
					taxaFilterValid.bind(taxaFilterNode.get().validProperty());
				}
			} else {
				taxaFilterNode.set(null);
				taxaFilterValid.bind(new SimpleBooleanProperty(false));
			}
			if (workflow.getWorkingTaxaNode() != null) {
				if (workflow.getWorkingTaxaNode() != getWorkingTaxaNode()) {
					workingTaxaNode.set(workflow.getWorkingTaxaNode());
					workingTaxaNodeValid.bind(workingTaxaNode.get().validProperty());
				}
			} else {
				workingTaxaNode.set(null);
				workingTaxaNodeValid.bind(new SimpleBooleanProperty(false));
			}
			if (workflow.getInputDataNode() != null && workflow.getInputDataNode().getDataBlock() instanceof CharactersBlock) {
				if (workflow.getInputDataNode() != getInputCharactersNode()) {
					inputCharactersNode.set((DataNode<CharactersBlock>) workflow.getInputDataNode());
					inputCharactersNodeValid.bind(inputCharactersNode.get().validProperty());
				}
			} else {
				inputCharactersNode.set(null);
				inputCharactersNodeValid.bind(new SimpleBooleanProperty(false));
			}
			if (workflow.getWorkingDataNode() != null && workflow.getWorkingDataNode().getDataBlock() instanceof CharactersBlock) {
				if (workflow.getWorkingDataNode() != getWorkingCharactersNode()) {
					workingCharactersNode.set((DataNode<CharactersBlock>) workflow.getWorkingDataNode());
					workingCharactersNodeValid.bind(workingCharactersNode.get().validProperty());
				}
			} else {
				workingCharactersNode.set(null);
				workingCharactersNodeValid.bind(new SimpleBooleanProperty(false));
			}
			if (workflow.getInputDataFilterNode() != null && workflow.getInputDataFilterNode().getAlgorithm() instanceof CharactersTaxaFilter) {
				if (workflow.getInputDataFilterNode() != getCharactersTaxaFilterNode()) {
					charactersTaxaFilterNode.set((AlgorithmNode<CharactersBlock, CharactersBlock>) workflow.getInputDataFilterNode());
					charactersTaxaFilterValid.bind(charactersTaxaFilterNode.get().validProperty());
				}
			} else {
				charactersTaxaFilterNode.set(null);
				charactersTaxaFilterValid.bind(new SimpleBooleanProperty(false));
			}
		};
		mainWindow.getWorkflow().validProperty().addListener(new WeakInvalidationListener(invalidationListener));
		invalidationListener.invalidated(null);
	}

	public String createSelectionString() {
		var buf = new StringBuilder();
		if (getInputTaxa() != null) {
			buf.append("taxa: ");
			if (getActiveTaxa().cardinality() == getInputTaxa().getNtax()) {
				buf.append("%,d".formatted(getInputTaxa().getNtax()));
			} else {
				buf.append("%,d (of %,d)".formatted(getActiveTaxa().cardinality(), getInputTaxa().getNtax()));
			}
			if (getSelectedTaxa().cardinality() > 0) {
				buf.append(" (selected: %,d)".formatted(getSelectedTaxa().cardinality()));
			}
		}
		if (getInputCharacters() != null) {
			buf.append(" sites: ");
			if (getActiveSites().cardinality() == getInputCharacters().getNchar()) {
				buf.append("%,d".formatted(getInputCharacters().getNchar()));
			} else {
				buf.append("%,d (of %,d)".formatted(getActiveSites().cardinality(), getInputCharacters().getNchar()));
			}
			if (getSelectedSites().cardinality() > 0) {
				buf.append(" (selected: %,d)".formatted(getSelectedSites().cardinality()));
			}
		}
		return buf.toString();
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
		return getInputCharacters() == null ? 0 : getInputCharacters().getNchar();
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
	public Node getMainNode() {
		return controller.getInnerAnchorPane();
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

	public BitSet getSelectedTaxa() {
		return selectedTaxa.get();
	}

	public ReadOnlyObjectProperty<BitSet> selectedTaxaProperty() {
		return selectedTaxa;
	}

	public void setSelectedTaxa(BitSet selectedTaxa) {
		if (!selectedTaxa.equals(getSelectedTaxa()))
			this.selectedTaxa.set(selectedTaxa);
	}

	public void setActiveTaxa(BitSet activeTaxa) {
		if (!activeTaxa.equals(getActiveTaxa()))
			this.activeTaxa.set(activeTaxa);
	}

	public BitSet getActiveTaxa() {
		return activeTaxa.get();
	}

	public ReadOnlyObjectProperty<BitSet> activeTaxaProperty() {
		return activeTaxa;
	}


	public void setActiveSites(BitSet activeSites) {
		if (!activeSites.equals(getActiveSites()))
			this.activeSites.set(activeSites);
	}

	public BitSet getSelectedSites() {
		return selectedSites.get();
	}

	public void setSelectedSites(BitSet selectedSites) {
		if (!selectedSites.equals(getSelectedSites()))
			this.selectedSites.set(selectedSites);
	}

	public ReadOnlyObjectProperty<BitSet> selectedSitesProperty() {
		return selectedSites;
	}

	public BitSet getActiveSites() {
		return activeSites.get();
	}

	public ReadOnlyObjectProperty<BitSet> activeSitesProperty() {
		return activeSites;
	}

	public DataNode<TaxaBlock> getInputTaxaNode() {
		return inputTaxaNode.get();
	}

	public ObjectProperty<DataNode<TaxaBlock>> inputTaxaNodeProperty() {
		return inputTaxaNode;
	}

	public AlgorithmNode<TaxaBlock, TaxaBlock> getTaxaFilterNode() {
		return taxaFilterNode.get();
	}

	public ObjectProperty<AlgorithmNode<TaxaBlock, TaxaBlock>> taxaFilterNodeProperty() {
		return taxaFilterNode;
	}

	public DataNode<CharactersBlock> getInputCharactersNode() {
		return inputCharactersNode.get();
	}

	public ObjectProperty<DataNode<CharactersBlock>> inputCharactersNodeProperty() {
		return inputCharactersNode;
	}

	public AlgorithmNode<CharactersBlock, CharactersBlock> getCharactersTaxaFilterNode() {
		return charactersTaxaFilterNode.get();
	}

	public ObjectProperty<AlgorithmNode<CharactersBlock, CharactersBlock>> charactersTaxaFilterNodeProperty() {
		return charactersTaxaFilterNode;
	}

	public DataNode<TaxaBlock> getWorkingTaxaNode() {
		return workingTaxaNode.get();
	}

	public ObjectProperty<DataNode<TaxaBlock>> workingTaxaNodeProperty() {
		return workingTaxaNode;
	}

	public DataNode<CharactersBlock> getWorkingCharactersNode() {
		return workingCharactersNode.get();
	}

	public ObjectProperty<DataNode<CharactersBlock>> workingCharactersNodeProperty() {
		return workingCharactersNode;
	}

	public boolean isInputTaxaNodeValid() {
		return inputTaxaNodeValid.get();
	}

	public boolean isTaxaFilterValid() {
		return taxaFilterValid.get();
	}

	public BooleanProperty taxaFilterValidProperty() {
		return taxaFilterValid;
	}

	public BooleanProperty inputTaxaNodeValidProperty() {
		return inputTaxaNodeValid;
	}

	public boolean isInputCharactersNodeValid() {
		return inputCharactersNodeValid.get();
	}

	public BooleanProperty inputCharactersNodeValidProperty() {
		return inputCharactersNodeValid;
	}

	public boolean isCharactersTaxaFilterValid() {
		return charactersTaxaFilterValid.get();
	}

	public BooleanProperty charactersTaxaFilterValidProperty() {
		return charactersTaxaFilterValid;
	}

	public boolean isWorkingTaxaNodeValid() {
		return workingTaxaNodeValid.get();
	}

	public BooleanProperty workingTaxaNodeValidProperty() {
		return workingTaxaNodeValid;
	}

	public boolean isWorkingCharactersNodeValid() {
		return workingCharactersNodeValid.get();
	}

	public BooleanProperty workingCharactersNodeValidProperty() {
		return workingCharactersNodeValid;
	}

	public TaxaBlock getInputTaxa() {
		if (isInputTaxaNodeValid())
			return getInputTaxaNode().getDataBlock();
		else
			return null;

	}

	public TaxaBlock getWorkingTaxa() {
		if (isWorkingTaxaNodeValid())
			return getWorkingTaxaNode().getDataBlock();
		else
			return null;
	}

	public TaxaFilter getTaxaFilter() {
		if (isTaxaFilterValid())
			return (TaxaFilter) getTaxaFilterNode().getAlgorithm();
		else
			return null;
	}

	public CharactersTaxaFilter getCharactersTaxaFilter() {
		if (isCharactersTaxaFilterValid())
			return (CharactersTaxaFilter) getCharactersTaxaFilterNode().getAlgorithm();
		else
			return null;
	}

	public CharactersBlock getInputCharacters() {
		if (isInputCharactersNodeValid())
			return getInputCharactersNode().getDataBlock();
		else
			return null;
	}

	public CharactersBlock getWorkingCharacters() {
		if (isWorkingCharactersNodeValid())
			return getWorkingCharactersNode().getDataBlock();
		else
			return null;
	}

	public boolean isNucleotideData() {
		return nucleotideData.get();
	}

	public ReadOnlyBooleanProperty nucleotideDataProperty() {
		return nucleotideData;
	}

	public boolean isDisabled(Taxon taxon) {
		return getWorkingTaxa() == null || getWorkingTaxa().indexOf(taxon) == -1;
	}

	public char[] getConsensusSequence() {
		return consensusSequence.get();
	}

	public ObjectProperty<char[]> consensusSequenceProperty() {
		return consensusSequence;
	}
}
