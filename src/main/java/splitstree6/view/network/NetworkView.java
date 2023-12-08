/*
 * NetworkView.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.network;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import jloda.graph.Edge;
import splitstree6.data.NetworkBlock;
import splitstree6.layout.network.DiagramType;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.sites.SitesFormat;
import splitstree6.view.format.sites.SitesStyle;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;
import splitstree6.view.format.taxmark.TaxonMark;
import splitstree6.view.format.traits.TraitsFormat;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

public class NetworkView implements IView {

	private final UndoManager undoManager = new UndoManager();

	private final NetworkViewController controller;
	private final NetworkViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final ObjectProperty<DiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram");
	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObjectProperty<NetworkBlock> networkBlock = new SimpleObjectProperty<>(this, "networkBlock");
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation");

	private final DoubleProperty optionZoomFactor = new SimpleDoubleProperty(this, "optionZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final ObjectProperty<String[]> optionActiveTraits = new SimpleObjectProperty<>(this, "optionActiveTraits");
	private final BooleanProperty optionTraitLegend = new SimpleBooleanProperty(this, "optionTraitLegend");
	private final IntegerProperty optionTraitSize = new SimpleIntegerProperty(this, "optionTraitSize");

	private final ObjectProperty<String[]> optionEdits = new SimpleObjectProperty<>(this, "optionEdits", new String[0]);

	private final ObjectProperty<SitesStyle> optionSitesStyle = new SimpleObjectProperty<>(this, "optionSitesStyle");

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");


	private final ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeShapeMap = FXCollections.observableHashMap();
	private final ObservableMap<jloda.graph.Edge, LabeledEdgeShape> edgeShapeMap = FXCollections.observableHashMap();


	{
		ProgramProperties.track(optionDiagram, DiagramType::valueOf, DiagramType.Network);
		ProgramProperties.track(optionOrientation, LayoutOrientation::valueOf, LayoutOrientation.Rotate0Deg);
		ProgramProperties.track(optionSitesStyle, SitesStyle::valueOf, SitesStyle.Hatches);
	}

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionOrientation.getName(), optionZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionActiveTraits.getName(), optionTraitLegend.getName(), optionTraitSize.getName(), optionSitesStyle.getName(),
				optionEdits.getName());
	}

	public NetworkView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<NetworkViewController>(NetworkViewController.class);
		controller = loader.getController();

		final ObservableMap<Integer, RichTextLabel> taxonLabelMap = FXCollections.observableHashMap();

		presenter = new NetworkViewPresenter(mainWindow, this, targetBounds, networkBlock, taxonLabelMap, nodeShapeMap, edgeShapeMap);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});

		setViewTab(viewTab);

		var taxLabelFormatter = new TaxonLabelFormat(mainWindow, undoManager);

		var traitsFormatter = new TraitsFormat(mainWindow, undoManager);
		traitsFormatter.setNodeShapeMap(nodeShapeMap);
		optionActiveTraits.bindBidirectional(traitsFormatter.optionActiveTraitsProperty());
		optionTraitLegend.bindBidirectional(traitsFormatter.optionTraitLegendProperty());
		optionTraitSize.bindBidirectional(traitsFormatter.optionTraitSizeProperty());
		traitsFormatter.getLegend().scaleProperty().bind(optionZoomFactorProperty());
		traitsFormatter.setRunAfterUpdateNodes(presenter::updateLabelLayout);
		presenter.updateCounterProperty().addListener(e -> traitsFormatter.updateNodes());

		var sitesFormat = new SitesFormat(undoManager);
		sitesFormat.optionSitesStyleProperty().bindBidirectional(optionSitesStyle);
		networkBlock.addListener(e -> sitesFormat.setNetworkBlock(networkBlock.get()));
		edgeShapeMap.addListener((InvalidationListener) e -> {
			sitesFormat.getEdgeShapeMap().clear();
			sitesFormat.getEdgeShapeMap().putAll(edgeShapeMap);
		});
		presenter.updateCounterProperty().addListener(e -> sitesFormat.updateEdges());
		networkBlockProperty().addListener((v, o, n) -> {
			sitesFormat.setDisable(n == null || n.getGraph().getNumberOfEdges() == 0);
		});

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter, new TaxonMark(mainWindow, undoManager), traitsFormatter, sitesFormat);

		AnchorPane.setLeftAnchor(traitsFormatter.getLegend(), 5.0);
		AnchorPane.setTopAnchor(traitsFormatter.getLegend(), 30.0);
		controller.getInnerAnchorPane().getChildren().add(traitsFormatter.getLegend());
		DraggableLabel.makeDraggable(traitsFormatter.getLegend());

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));
		optionDiagramProperty().addListener(e -> mainWindow.setDirty(true));

		empty.bind(Bindings.createBooleanBinding(() -> getNetworkBlock() == null || getNetworkBlock().size() == 0, networkBlockProperty()));

		viewTab.getAlgorithmBreadCrumbsToolBar().getInfoLabel().textProperty().bind(Bindings.createStringBinding(() -> "n: %,d  v: %,d  e: %,d".formatted(mainWindow.getWorkingTaxa().getNtax(),
						networkBlock.get() == null || networkBlock.get().getGraph() == null ? 0 : networkBlock.get().getGraph().getNumberOfNodes(),
						networkBlock.get() == null || networkBlock.get().getGraph() == null ? 0 : networkBlock.get().getGraph().getNumberOfEdges()),
				mainWindow.workingTaxaProperty(), networkBlockProperty()));
	}

	@Override
	public void clear() {
	}

	@Override
	public NetworkViewPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getCitation() {
		return null;
	}

	@Override
	public String getName() {
		return name.get();
	}

	@Override
	public Node getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();
	}

	@Override
	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}

	@Override
	public int size() {
		return getNetworkBlock() == null ? 0 : getNetworkBlock().size();
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

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
	}

	public NetworkBlock getNetworkBlock() {
		return networkBlock.get();
	}

	public ObjectProperty<NetworkBlock> networkBlockProperty() {
		return networkBlock;
	}

	public DiagramType getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<DiagramType> optionDiagramProperty() {
		return optionDiagram;
	}

	public LayoutOrientation getOptionOrientation() {
		return optionOrientation.get();
	}

	public void setOptionOrientation(LayoutOrientation optionOrientation) {
		this.optionOrientation.set(optionOrientation);
	}

	public ObjectProperty<LayoutOrientation> optionOrientationProperty() {
		return optionOrientation;
	}

	public double getOptionZoomFactor() {
		return optionZoomFactor.get();
	}

	public DoubleProperty optionZoomFactorProperty() {
		return optionZoomFactor;
	}

	public void setOptionZoomFactor(double optionZoomFactor) {
		this.optionZoomFactor.set(optionZoomFactor);
	}

	public double getOptionFontScaleFactor() {
		return optionFontScaleFactor.get();
	}

	public void setOptionFontScaleFactor(double optionFontScaleFactor) {
		this.optionFontScaleFactor.set(optionFontScaleFactor);
	}

	public DoubleProperty optionFontScaleFactorProperty() {
		return optionFontScaleFactor;
	}

	public String[] getOptionActiveTraits() {
		return optionActiveTraits.get();
	}

	public ObjectProperty<String[]> optionActiveTraitsProperty() {
		return optionActiveTraits;
	}

	public boolean isOptionTraitLegend() {
		return optionTraitLegend.get();
	}

	public BooleanProperty optionTraitLegendProperty() {
		return optionTraitLegend;
	}

	public int getOptionTraitSize() {
		return optionTraitSize.get();
	}

	public IntegerProperty optionTraitSizeProperty() {
		return optionTraitSize;
	}

	public SitesStyle getOptionSitesStyle() {
		return optionSitesStyle.get();
	}

	public ObjectProperty<SitesStyle> optionSitesStyleProperty() {
		return optionSitesStyle;
	}

	public void setOptionSitesStyle(SitesStyle optionSitesStyle) {
		this.optionSitesStyle.set(optionSitesStyle);
	}

	public String[] getOptionEdits() {
		return optionEdits.get();
	}

	public ObjectProperty<String[]> optionEditsProperty() {
		return optionEdits;
	}

	public void setOptionEdits(String[] optionEdits) {
		this.optionEdits.set(optionEdits);
	}

	public void setNetworkBlock(NetworkBlock networkBlock) {
		this.networkBlock.set(networkBlock);
	}

	public ObservableMap<jloda.graph.Node, LabeledNodeShape> getNodeShapeMap() {
		return nodeShapeMap;
	}

	public ObservableMap<Edge, LabeledEdgeShape> getEdgeShapeMap() {
		return edgeShapeMap;
	}

	public NetworkViewController getController() {
		return controller;
	}
}
