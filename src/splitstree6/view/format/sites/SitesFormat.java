/*
 *  SitesFormat.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableUtils;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;

/**
 * format sites for haplotype network
 * Daniel Huson, 4.2022
 */
public class SitesFormat extends Group {
	private final SitesFormatController controller;
	private final SitesFormatPresenter presenter;

	private final ObjectProperty<NetworkBlock> networkBlock = new SimpleObjectProperty<>();
	private final ObservableMap<Edge, Group> edgeShapeMap = FXCollections.observableHashMap();

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

	public ObservableMap<Edge, Group> getEdgeShapeMap() {
		return edgeShapeMap;
	}

	private static class SitesView extends Group {

		public SitesView(NetworkBlock networkBlock, Edge e, Group edgeGroup, SitesStyle sitesStyle) {
			var lines = BasicFX.getAllRecursively(edgeGroup, Line.class);
			for (var line : lines) {
				if (networkBlock.getEdge2data().get(e) != null && networkBlock.getEdge2data().get(e).containsKey("sites")) {
					var sites = networkBlock.getEdge2data().get(e).get("sites").trim();
					if (!sites.isEmpty()) {
						InvalidationListener invalidationListener = null;

						if (sitesStyle == SitesStyle.Counts || sitesStyle == SitesStyle.CompactLabels) {
							var label = (sitesStyle == SitesStyle.CompactLabels ? sites : String.valueOf(StringUtils.countOccurrences(sites, ',') + 1));
							var text = new CopyableLabel(label);

							invalidationListener = a -> {
								getChildren().clear();
								var start = new Point2D(line.getStartX(), line.getStartY());
								var end = new Point2D(line.getEndX(), line.getEndY());
								var angle = GeometryUtilsFX.computeAngle(end.subtract(start));
								var point = start.add(end).multiply(0.5);
								point = GeometryUtilsFX.translateByAngle(point, angle + 270, 5);
								text.setTranslateX(point.getX());
								text.setTranslateY(point.getY());
								DraggableUtils.setupDragMouseLayout(text);
								getChildren().add(text);
							};
						} else if (sitesStyle == SitesStyle.Hatches || sitesStyle == SitesStyle.Labels) {
							var count = StringUtils.countOccurrences(sites, ',') + 1;
							var labels = sites.split("\s*,\s*");
							invalidationListener = a -> {
								getChildren().clear();
								var start = new Point2D(line.getStartX(), line.getStartY());
								var end = new Point2D(line.getEndX(), line.getEndY());

								var angle = GeometryUtilsFX.computeAngle(end.subtract(start));

								var gapBetweenHatches = start.distance(end) / (count + 1);

								var point = start;
								for (var i = 0; i < count; i++) {
									point = GeometryUtilsFX.translateByAngle(point, angle, gapBetweenHatches);
									var left = GeometryUtilsFX.translateByAngle(point, angle + 90, 5);
									var right = GeometryUtilsFX.translateByAngle(point, angle + 270, 5);
									if (sitesStyle == SitesStyle.Hatches) {
										var hatch = new Line(left.getX(), left.getY(), right.getX(), right.getY());
										hatch.setStroke(Color.BLACK);
										getChildren().add(hatch);
									} else /* if(sitesStyle==SitesStyle.Labels) */ {
										var text = new CopyableLabel(labels[i]);
										text.setTranslateX(right.getX());
										text.setTranslateY(right.getY());
										DraggableUtils.setupDragMouseLayout(text);
										getChildren().add(text);
									}
								}
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
	}
}
