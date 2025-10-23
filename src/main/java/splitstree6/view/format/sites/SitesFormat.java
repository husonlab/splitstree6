/*
 *  SitesFormat.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.sites;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import jloda.fx.control.CopyableLabel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.*;
import jloda.graph.Edge;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.layout.tree.LabeledEdgeShape;

/**
 * format sites for haplotype network
 * Daniel Huson, 4.2022
 */
public class SitesFormat extends Group {
	private final SitesFormatController controller;
	private final SitesFormatPresenter presenter;

	private final ObjectProperty<NetworkBlock> networkBlock = new SimpleObjectProperty<>();
	private final ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap = FXCollections.observableHashMap();

	private final ObjectProperty<SitesStyle> optionSitesStyle = new SimpleObjectProperty<>(this, "optionSitesStyle");

	{
		ProgramProperties.track(optionSitesStyle, SitesStyle::valueOf, SitesStyle.Hatches);
	}

	public SitesFormat(UndoManager undoManager) {
		var loader = new ExtendedFXMLLoader<SitesFormatController>(SitesFormatController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new SitesFormatPresenter(this, undoManager, controller);
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

	public void updateEdges() {
		var networkBlock = getNetworkBlock();
		var edgeShapeMap = getEdgeShapeMap();
		if (networkBlock != null && edgeShapeMap != null) {
			var graph = networkBlock.getGraph();

			for (var e : graph.edges()) {
				var group = getEdgeShapeMap().get(e);
				if (group != null) {
					group.getChildren().removeAll(BasicFX.getAllRecursively(group, SitesView.class));
					if (getOptionSitesStyle() != SitesStyle.None) {
						group.getChildren().add(new SitesView(networkBlock, e, group, getOptionSitesStyle()));
					}
				}
			}
		}
	}

	public NetworkBlock getNetworkBlock() {
		return networkBlock.get();
	}

	public ObjectProperty<NetworkBlock> networkBlockProperty() {
		return networkBlock;
	}

	public void setNetworkBlock(NetworkBlock networkBlock) {
		this.networkBlock.set(networkBlock);
	}

	public ObservableMap<Edge, LabeledEdgeShape> getEdgeShapeMap() {
		return edgeShapeMap;
	}

	private static class SitesView extends Group {

		public SitesView(NetworkBlock networkBlock, Edge e, Group edgeGroup, SitesStyle sitesStyle) {
			var lines = BasicFX.getAllRecursively(edgeGroup, Line.class);
			edgeGroup.getChildren().setAll(lines); // remove everything else
			// todo: use the fact that edgesGroup main contain a label

			for (var line : lines) { // all lines representing edge, usually 1
				var data = networkBlock.getEdge2data().get(e);
				InvalidationListener invalidationListener = null;
				if (data != null && data.containsKey("sites")) {
					var sites = data.get("sites").trim();
					if (!sites.isEmpty()) {
						if (sitesStyle == SitesStyle.Counts || sitesStyle == SitesStyle.CompactLabels) {
							var text = (sitesStyle == SitesStyle.CompactLabels ? sites : String.valueOf(StringUtils.countOccurrences(sites, ',') + 1));
							var label = new CopyableLabel(text);
							label.setOnMouseClicked(line.getOnMouseClicked());
							label.setUserData(e);

							invalidationListener = a -> {
								getChildren().clear();
								var start = new Point2D(line.getStartX(), line.getStartY());
								var end = new Point2D(line.getEndX(), line.getEndY());
								var angle = GeometryUtilsFX.computeAngle(end.subtract(start));
								var point = start.add(end).multiply(0.5);
								point = GeometryUtilsFX.translateByAngle(point, angle + 270, 5);
								label.setTranslateX(point.getX());
								label.setTranslateY(point.getY());
								DraggableUtils.setupDragMouseLayout(label);
								label.setOnMouseClicked(edgeGroup.getOnMouseClicked());
								getChildren().add(label);
							};
						} else if (sitesStyle == SitesStyle.Hatches || sitesStyle == SitesStyle.Labels) {
							var count = StringUtils.countOccurrences(sites, ',') + 1;
							var labels = sites.split("\\s*,\\s*");
							invalidationListener = a -> {
								getChildren().clear();
								var start = new Point2D(line.getStartX(), line.getStartY());
								var end = new Point2D(line.getEndX(), line.getEndY());

								var gapBetweenHatches = 1.0;
								if (sitesStyle == SitesStyle.Hatches) {
									var onScreenDistance = line.localToScreen(start.getX(), start.getY()).distance(line.localToScreen(end.getX(), end.getY()));
									var onScreenGap = 4.0;
									if ((count + 1) * onScreenGap > 0.3 * onScreenDistance) {
										onScreenGap = 0.3 * onScreenDistance / (count + 1);
									}
									gapBetweenHatches = (line.screenToLocal(onScreenGap, onScreenGap).subtract(line.screenToLocal(0, 0))).magnitude(); // close enough...
									var start1 = start.multiply(0.35).add(end.multiply(0.65));
									var end1 = start.multiply(0.65).add(end.multiply(0.35));
									start = start1;
									end = end1;
								} else {
									gapBetweenHatches = start.distance(end) / (count + 1);
								}

								var angle = GeometryUtilsFX.computeAngle(end.subtract(start));

								var point = start;
								for (var i = 0; i < count; i++) {
									point = GeometryUtilsFX.translateByAngle(point, angle, gapBetweenHatches);
									var left = GeometryUtilsFX.translateByAngle(point, angle + 90, 5);
									var right = GeometryUtilsFX.translateByAngle(point, angle + 270, 5);
									{
										var hatch = new Line(left.getX(), left.getY(), right.getX(), right.getY());
										hatch.setStroke(Color.BLACK);
										hatch.getStyleClass().add("graph-edge");
										getChildren().add(hatch);
									}
									if (sitesStyle == SitesStyle.Labels) {
										var label = new CopyableLabel(labels[i]);
										label.setUserData(e);
										label.setTranslateX(right.getX());
										label.setTranslateY(right.getY());
										DraggableUtils.setupDragMouseLayout(label);
										label.setOnMouseClicked(edgeGroup.getOnMouseClicked());
										getChildren().add(label);
									}
								}
							};
						}
					}
				} else if (data != null && sitesStyle == SitesStyle.Hatches && networkBlock.getGraph().hasEdgeWeights()) {
					invalidationListener = a -> {
						var count = Math.round(networkBlock.getGraph().getWeight(e));
						getChildren().clear();
						var start = new Point2D(line.getStartX(), line.getStartY());
						var end = new Point2D(line.getEndX(), line.getEndY());

						var gapBetweenHatches = 1.0;
						{
							var onScreenDistance = line.localToScreen(start.getX(), start.getY()).distance(line.localToScreen(end.getX(), end.getY()));
							var onScreenGap = 4.0;
							if ((count + 1) * onScreenGap > 0.3 * onScreenDistance) {
								onScreenGap = 0.3 * onScreenDistance / (count + 1);
							}
							gapBetweenHatches = (line.screenToLocal(onScreenGap, onScreenGap).subtract(line.screenToLocal(0, 0))).magnitude(); // close enough...
							var start1 = start.multiply(0.35).add(end.multiply(0.65));
							var end1 = start.multiply(0.65).add(end.multiply(0.35));
							start = start1;
							end = end1;
						}

						var angle = GeometryUtilsFX.computeAngle(end.subtract(start));

						var point = start;
						for (var i = 0; i < count; i++) {
							point = GeometryUtilsFX.translateByAngle(point, angle, gapBetweenHatches);
							var left = GeometryUtilsFX.translateByAngle(point, angle + 90, 5);
							var right = GeometryUtilsFX.translateByAngle(point, angle + 270, 5);
							{
								var hatch = new Line(left.getX(), left.getY(), right.getX(), right.getY());
								hatch.setStroke(Color.BLACK);
								hatch.getStyleClass().add("graph-edge");
								getChildren().add(hatch);
							}
						}
					};
				}
				if ((sitesStyle == SitesStyle.Weights || ((data == null || !data.containsKey("sites")) && sitesStyle == SitesStyle.Counts)) && networkBlock.getGraph().hasEdgeWeights()) {
					String text;
					if (sitesStyle == SitesStyle.Counts) {
						text = String.valueOf(Math.round(networkBlock.getGraph().getWeight(e)));
					} else {
						text = StringUtils.removeTrailingZerosAfterDot(networkBlock.getGraph().getWeight(e));
					}

					var label = new CopyableLabel(text);

					label.setOnMouseClicked(line.getOnMouseClicked());
					label.setUserData(e);
					invalidationListener = a -> {
						getChildren().clear();
						var start = new Point2D(line.getStartX(), line.getStartY());
						var end = new Point2D(line.getEndX(), line.getEndY());
						var angle = GeometryUtilsFX.computeAngle(end.subtract(start));
						var point = start.add(end).multiply(0.5);
						point = GeometryUtilsFX.translateByAngle(point, angle + 270, 5);
						label.setTranslateX(point.getX());
						label.setTranslateY(point.getY());
						DraggableUtils.setupDragMouseLayout(label);
						label.setOnMouseClicked(edgeGroup.getOnMouseClicked());
						getChildren().add(label);
					};
				}
				if (invalidationListener != null) {
					line.startXProperty().addListener(invalidationListener);
					line.startYProperty().addListener(invalidationListener);
					line.endXProperty().addListener(invalidationListener);
					line.endYProperty().addListener(invalidationListener);
					invalidationListener.invalidated(null);
				}
			}
		}
	}
}
