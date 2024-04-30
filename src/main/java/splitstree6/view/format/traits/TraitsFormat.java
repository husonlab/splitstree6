/*
 *  TraitsFormat.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.traits;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.util.Pair;
import jloda.fx.control.Legend;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.FuzzyBoolean;
import jloda.fx.util.ProgramProperties;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.view.worldmap.BasicPieChart;
import splitstree6.window.MainWindow;

/**
 * provides trait formatter
 * Daniel Huson, 3.2022
 */
public class TraitsFormat extends Pane {
	public static final String ALL = "*All*";

	private final TraitsFormatController controller;
	private final TraitsFormatPresenter presenter;

	private final ObjectProperty<TaxaBlock> workingTaxa = new SimpleObjectProperty<>();
	private final ObjectProperty<TraitsBlock> traitsBlock = new SimpleObjectProperty<>();
	private final ChangeListener<Boolean> validListener;

	private ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeShapeMap;
	private Runnable runAfterUpdateNodes;
	private final Legend legend;

	private final ObjectProperty<String[]> optionActiveTraits = new SimpleObjectProperty<>(this, "optionActiveTraits", new String[]{ALL});
	private final ObjectProperty<FuzzyBoolean> optionTraitLegend = new SimpleObjectProperty<>(this, "optionTraitLegend", FuzzyBoolean.True);

	private final IntegerProperty optionTraitSize = new SimpleIntegerProperty(this, "optionTraitSize");

	{
		ProgramProperties.track(optionTraitLegend, FuzzyBoolean::valueOf, FuzzyBoolean.True);
		ProgramProperties.track(optionTraitSize, 64);
	}

	public TraitsFormat(MainWindow mainWindow, UndoManager undoManager) {
		var loader = new ExtendedFXMLLoader<TraitsFormatController>(TraitsFormatController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		validListener = (v, o, n) -> {
			workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
			if (n)
				traitsBlock.set(mainWindow.getWorkflow().getWorkingTaxaBlock().getTraitsBlock());
		};
		workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
		mainWindow.getWorkflow().validProperty().addListener(new WeakChangeListener<>(validListener));

		legend = new Legend(FXCollections.observableArrayList(), "Twenty", Orientation.VERTICAL);
		legend.setScalingType(Legend.ScalingType.sqrt);
		legend.circleMinSizeProperty().bind(optionTraitSizeProperty().multiply(0.5));

		legend.getStyleClass().add("viewer-background");
		legend.setPadding(new Insets(3, 3, 3, 3));

		legend.showProperty().bindBidirectional(optionTraitLegend);

		presenter = new TraitsFormatPresenter(mainWindow, this, controller, undoManager);
	}

	public TraitsBlock getTraitsBlock() {
		return traitsBlock.get();
	}

	public ObjectProperty<TraitsBlock> traitsBlockProperty() {
		return traitsBlock;
	}

	public String[] getOptionActiveTraits() {
		return optionActiveTraits.get();
	}

	public ObjectProperty<String[]> optionActiveTraitsProperty() {
		return optionActiveTraits;
	}

	public void setOptionActiveTraits(String[] optionActiveTraits) {
		this.optionActiveTraits.set(optionActiveTraits);
	}

	public boolean isAllTraitsActive() {
		return getOptionActiveTraits().length == 1 && getOptionActiveTraits()[0].equals(ALL) || getOptionActiveTraits().length > 0 && traitsBlock.get() != null && getOptionActiveTraits().length >= traitsBlock.get().getNumberNumericalTraits();
	}

	public boolean isNoneTraitsActive() {
		return getOptionActiveTraits().length == 0;
	}

	public boolean isTraitActive(String traitName) {
		if (isAllTraitsActive())
			return true;
		return StringUtils.getIndex(traitName, getOptionActiveTraits()) >= 0;
	}

	public ObjectProperty<FuzzyBoolean> optionTraitLegendProperty() {
		return optionTraitLegend;
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


	public void setRunAfterUpdateNodes(Runnable runAfterUpdateNodes) {
		this.runAfterUpdateNodes = runAfterUpdateNodes;
	}

	public Runnable getRunAfterUpdateNodes() {
		return runAfterUpdateNodes;
	}

	public ObservableMap<Node, LabeledNodeShape> getNodeShapeMap() {
		return nodeShapeMap;
	}

	public void setNodeShapeMap(ObservableMap<Node, LabeledNodeShape> nodeShapeMap) {
		this.nodeShapeMap = nodeShapeMap;
	}

	public void updateNodes() {
		if (traitsBlock.get() != null && nodeShapeMap != null && traitsBlock.get().getNumberNumericalTraits() > 0) {
			var graphOptional = nodeShapeMap.keySet().stream().filter(v -> v.getOwner() != null).map(v -> (PhyloGraph) v.getOwner()).findAny();
			if (graphOptional.isPresent()) {
				var traitsBlock = getTraitsBlock();

				legend.getLabels().setAll(traitsBlock.getNumericalTraitLabels());
				legend.getActive().clear();
				if (isAllTraitsActive()) {
					legend.getActive().addAll(traitsBlock.getNumericalTraitLabels());
				} else {
					for (var active : getOptionActiveTraits()) {
						legend.getActive().add(active);
					}
				}

				var unitSize = 0.0;

				var graph = graphOptional.get();
				for (var v : nodeShapeMap.keySet()) {
					var nodeShape = nodeShapeMap.get(v);
					if (nodeShape != null) {
						nodeShape.getChildren().removeAll(BasicFX.getAllRecursively(nodeShape, BasicPieChart.class));

						if (v.getOwner() == graph && graph.getNumberOfTaxa(v) == 1) {
							var taxonId = graph.getTaxon(v);

							if (isNoneTraitsActive()) {
								var shapes = BasicFX.getAllRecursively(nodeShape, Shape.class);
								if (shapes.size() == 1) {
									var shape = shapes.iterator().next();
									if (shape.prefWidth(0) > 0 && shape.prefHeight(0) > 0) {
										shape.setScaleX(1);
										shape.setScaleY(1);
									}
								}
							} else {
									var chart = new BasicPieChart(workingTaxa.get().getLabel(taxonId));
									chart.setColorScheme(legend.getColorSchemeName());
									var max = 0.0;

								for (var t = 1; t <= workingTaxa.get().getNtax(); t++) {
									var tsum = 0.0;
									for (var trait = 1; trait <= traitsBlock.getNTraits(); trait++) {
										if (traitsBlock.isNumerical(trait) && isTraitActive(traitsBlock.getTraitLabel(trait))) {
											tsum += traitsBlock.getTraitValue(t, trait);
										}
									}
									max = Math.max(max, tsum);
									}

									var tooltipBuf = new StringBuilder();

								var sum = 0.0;
								for (var traitId : traitsBlock.numericalTraits()) {
										var label = traitsBlock.getTraitLabel(traitId);
										if (isTraitActive(label)) {
											var value = traitsBlock.getTraitValue(taxonId, traitId);
											if (value > 0) {
												tooltipBuf.append(String.format("%s: %,.2f%n", label, value));
											}
											sum += value;
											chart.getData().add(new Pair<>(traitsBlock.getTraitLabel(traitId), value));
										} else
											chart.getData().add(new Pair<>(traitsBlock.getTraitLabel(traitId), 0.0));
									}

									if (sum > 0) {
										var pieSize = (Math.sqrt(sum) / Math.sqrt(max)) * getOptionTraitSize();
										unitSize = Math.max(unitSize, (1 / Math.sqrt(max)) * getOptionTraitSize());
										chart.setRadius(0.5 * pieSize);

										var shapes = BasicFX.getAllRecursively(nodeShape, Shape.class);
										for (var shape : shapes) {
											if (shape instanceof Circle circle && !"iceberg".equals(shape.getId())) {
												circle.setRadius(pieSize / circle.getRadius());
											}
										}
										nodeShape.getChildren().add(chart);

										if (!tooltipBuf.isEmpty()) {
											Tooltip.install(chart, new Tooltip(tooltipBuf.toString()));
										}
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
