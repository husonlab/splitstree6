/*
 * TraitsPie.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.traits;

import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import jloda.fx.control.Legend;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ColorSchemeManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.graph.Node;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.ProgramProperties;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.window.MainWindow;

/**
 * provides trait formatter
 * Daniel Huson, 3.2022
 */
public class TraitsPie extends Pane {
	private final TraitsPieController controller;
	private final TraitsPiePresenter presenter;

	private final ObjectProperty<TaxaBlock> workingTaxa = new SimpleObjectProperty<>();
	private final ObjectProperty<TraitsBlock> traitsBlock = new SimpleObjectProperty<>();
	private final ChangeListener<Boolean> validListener;

	private ObservableMap<jloda.graph.Node, Group> nodeShapeMap;
	private Runnable runAfterUpdateNodes;
	private final Legend legend;

	private final StringProperty optionShowTrait = new SimpleStringProperty(this, "optionShowTrait", "None");
	private final BooleanProperty optionTraitLegend = new SimpleBooleanProperty(this, "optionTraitLegend");
	private final IntegerProperty optionTraitSize = new SimpleIntegerProperty(this, "optionTraitSize");

	{
		ProgramProperties.track(optionTraitLegend, true);
		ProgramProperties.track(optionTraitSize, 64);
	}

	public TraitsPie(MainWindow mainWindow, UndoManager undoManager) {
		var loader = new ExtendedFXMLLoader<TraitsPieController>(TraitsPieController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		workingTaxa.addListener((c, o, n) -> {
			traitsBlock.set(n == null ? null : n.getTraitsBlock());
		});

		validListener = (v, o, n) -> {
			workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
		};
		workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
		mainWindow.getWorkflow().validProperty().addListener(new WeakChangeListener<>(validListener));

		legend = new Legend(FXCollections.observableArrayList(), "Twenty", Orientation.VERTICAL);
		legend.setScalingType(Legend.ScalingType.sqrt);
		legend.circleMinSizeProperty().bind(optionTraitSizeProperty().multiply(0.5));

		presenter = new TraitsPiePresenter(mainWindow, this, controller, undoManager);
	}

	public TraitsBlock getTraitsBlock() {
		return traitsBlock.get();
	}

	public ObjectProperty<TraitsBlock> traitsBlockProperty() {
		return traitsBlock;
	}

	public String getOptionShowTrait() {
		return optionShowTrait.get();
	}

	public StringProperty optionShowTraitProperty() {
		return optionShowTrait;
	}

	public void setOptionShowTrait(String traitLabel) {
		this.optionShowTrait.set(traitLabel);
	}

	public boolean getOptionTraitLegend() {
		return optionTraitLegend.get();
	}

	public BooleanProperty optionTraitLegendProperty() {
		return optionTraitLegend;
	}

	public void setOptionTraitLegend(boolean optionTraitLegend) {
		this.optionTraitLegend.set(optionTraitLegend);
	}

	public int getOptionTraitSize() {
		return optionTraitSize.get();
	}

	public IntegerProperty optionTraitSizeProperty() {
		return optionTraitSize;
	}

	public void setOptionTraitSize(int optionTraitSize) {
		this.optionTraitSize.set(optionTraitSize);
	}

	public void setNodeShapeMap(ObservableMap<jloda.graph.Node, Group> nodeShapeMap) {
		this.nodeShapeMap = nodeShapeMap;
		updateNodes();
	}

	public void setRunAfterUpdateNodes(Runnable runAfterUpdateNodes) {
		this.runAfterUpdateNodes = runAfterUpdateNodes;
	}

	public Runnable getRunAfterUpdateNodes() {
		return runAfterUpdateNodes;
	}

	public ObservableMap<Node, Group> getNodeShapeMap() {
		return nodeShapeMap;
	}

	public void updateNodes() {
		if (traitsBlock.get() != null && nodeShapeMap != null && traitsBlock.get().getNTraits() > 0) {
			var graphOptional = nodeShapeMap.keySet().stream().map(v -> (PhyloSplitsGraph) v.getOwner()).findAny();
			if (graphOptional.isPresent()) {
				var traitsBlock = getTraitsBlock();

				legend.getLabels().setAll(traitsBlock.getTraitLabels());
				legend.getActive().clear();
				if (getOptionShowTrait().equalsIgnoreCase(TraitItem.All))
					legend.getActive().addAll(traitsBlock.getTraitLabels());
				else if (!getOptionShowTrait().equalsIgnoreCase(TraitItem.None)) {
					legend.getActive().add(getOptionShowTrait());
				}

				var unitSize = 0.0;

				var graph = graphOptional.get();
				for (var v : nodeShapeMap.keySet()) {
					var group = nodeShapeMap.get(v);
					group.getChildren().removeAll(BasicFX.getAllRecursively(group, PieChart.class));

					if (graph.getNumberOfTaxa(v) == 1) {
						var taxonId = graph.getTaxon(v);

						if (!getOptionShowTrait().equalsIgnoreCase(TraitItem.None)) {
							var pieChart = new PieChart();
							pieChart.setLabelsVisible(false);
							pieChart.setLegendVisible(false);

							var sum = 0;
							var max = (getOptionShowTrait().equalsIgnoreCase(TraitItem.All) ? Math.max(1, getTraitsBlock().getMaxAll()) : getTraitsBlock().getMax(getOptionShowTrait()));

							var tooltipBuf = new StringBuilder();

							var count = 0;
							for (var traitId = 1; traitId <= traitsBlock.getNTraits(); traitId++) {
								var label = traitsBlock.getTraitLabel(traitId);
								if (getOptionShowTrait().equalsIgnoreCase(TraitItem.All) || getOptionShowTrait().equals(label)) {
									var value = traitsBlock.getTraitValue(taxonId, traitId);
									tooltipBuf.append(String.format("%s: %,d%n", label, value));
									sum += value;
									pieChart.getData().add(new PieChart.Data(traitsBlock.getTraitLabel(traitId), value));
									count++;
								} else
									pieChart.getData().add(new PieChart.Data(traitsBlock.getTraitLabel(traitId), 0));
							}

							if (sum > 0) {
								var pieSize = (Math.sqrt(sum) / Math.sqrt(max)) * getOptionTraitSize();
								unitSize = Math.max(unitSize, (1 / Math.sqrt(max)) * getOptionTraitSize());
								pieChart.setMinSize(5, 5);
								pieChart.setPrefSize(pieSize, pieSize);
								pieChart.setMaxSize(pieSize, pieSize);
								pieChart.setLayoutX(-0.5 * pieSize);
								pieChart.setLayoutY(-0.5 * pieSize);

								group.getChildren().add(pieChart);
								ColorSchemeManager.setPieChartColors(pieChart, legend.getColorSchemeName());
								pieChart.setStyle("-fx-padding: -10;"); // remove white space around pie


								if (count == 1)
									pieChart.getStyleClass().add("single-pie");


								if (tooltipBuf.length() > 0) {
									Tooltip.install(pieChart, new Tooltip(tooltipBuf.toString()));
								}
							}
						}
					}
				}
				legend.setUnitRadius(0.5 * unitSize);
				legend.setScalingType(Legend.ScalingType.sqrt);
			}
		}
		if (getRunAfterUpdateNodes() != null)
			getRunAfterUpdateNodes().run();
	}

	public Legend getLegend() {
		return legend;
	}
}
