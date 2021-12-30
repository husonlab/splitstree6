/*
 *  RadialLabelLayout.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.splits.layout;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Bounds;
import jloda.fx.util.AService;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.util.IteratorUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.function.Consumer;

/**
 * do radial layout of labels
 * Daniel Huson, 12.2021
 */
public class RadialLabelLayout {

	private final AService<Boolean> layoutService = new AService<>();

	private final ArrayList<LayoutItem> items = new ArrayList<>();

	private final ArrayList<Bounds> avoidList = new ArrayList<>();

	private double gap = 4;

	public RadialLabelLayout() {
		setupLayoutService();
	}

	public void layoutLabels() {
		if (items.size() > 0) {
			if (items.get(0).width() == 0) {
				System.err.println("not ready");
				Platform.runLater(this::layoutLabels);
			} else
				layoutService.restart();
		}
	}

	public void clear() {
		items.clear();
		avoidList.clear();
	}

	public ArrayList<LayoutItem> getItems() {
		return items;
	}

	public ArrayList<Bounds> getAvoidList() {
		return avoidList;
	}

	public double getGap() {
		return gap;
	}

	public void setGap(double gap) {
		this.gap = gap;
	}

	/**
	 * setup the layout service
	 */
	private void setupLayoutService() {
		layoutService.setCallable(() -> {
					// setup graph:
					var graph = new Graph();
					var itemNodesMap = new HashMap<LayoutItem, ArrayList<Node>>();
					var itemBestChoiceMap = new HashMap<LayoutItem, Choice>();

					// nodes:
					for (var item : items) {
						for (var choice : computeChoices(item)) {
							itemBestChoiceMap.putIfAbsent(item, choice);
							var ok = true;
							for (var avoid : avoidList) {
								if (choice.intersects(avoid)) {
									ok = false;
									break;
								}
							}
							if (ok) {
								var v = graph.newNode();
								v.setData(choice);
								itemNodesMap.computeIfAbsent(item, k -> new ArrayList<>()).add(v);
							}
						}
					}

					// edges:
					for (var v : graph.nodes()) {
						var vBox = (Choice) v.getData();
						for (var w : graph.nodes(v)) {
							var wBox = (Choice) w.getData();
							if (vBox.item() != wBox.item() && vBox.intersects(wBox))
								graph.newEdge(v, w);
						}
					}

					// greedily choose locations for as many items as possible:
					var nodeList = new LinkedList<>(IteratorUtils.asCollection(IteratorUtils.randomize(graph.nodes(), new Random(666)), new LinkedList<>()));
					nodeList.sort(RadialLabelLayout::compare);

					var remainingItems = new ArrayList<>(items);
					var selectedChoices = new ArrayList<Choice>();

					while (nodeList.size() > 0) {
						var v = nodeList.remove(0);
						var mustReSort = (v.getDegree() > 0);
						var item = ((Choice) v.getData()).item();
						remainingItems.remove(item);

						var choice = (Choice) v.getData();

						Platform.runLater(() -> {
							item.xSetter().accept(choice.x() - item.anchorX());
							item.ySetter().accept(choice.y() - item.anchorY());
						});
						selectedChoices.add(choice);

						// remove nodes from graph:
						for (var w : v.adjacentNodes()) { // adjacent placements can't be used
							if (w.getOwner() != null) {
								graph.deleteNode(w);
							}
							nodeList.remove(w);
						}
						for (var w : itemNodesMap.get(item)) {
							for (var e : w.adjacentEdges()) { // other placements of v no-longer compete with placements of other nodes
								graph.deleteEdge(e);
							}
							if (w.getOwner() != null) {
								graph.deleteNode(w);
							}
							nodeList.remove(w);
						}
						if (mustReSort)
							nodeList.sort(RadialLabelLayout::compare);
					}

					// remaining items are pushed outward:
					for (var item : remainingItems) {
						var choice = itemBestChoiceMap.get(item);

						var ok = false;
						var deltaX = 5 * Math.cos(GeometryUtilsFX.deg2rad(item.angle()));
						var deltaY = 5 * Math.sin(GeometryUtilsFX.deg2rad(item.angle()));

						var x = choice.x();
						var y = choice.y();
						var i = 0;
						while (!ok && i++ < 1000) {
							ok = true;
							for (var avoid : getAvoidList()) {
								if (choice.intersects(avoid)) {
									ok = false;
									break;
								}
							}
							if (ok) {
								for (var other : selectedChoices) {
									if (other.intersects(choice)) {
										ok = false;
										break;
									}
								}
							}
							if (!ok) {
								x += deltaX;
								y += deltaY;
								choice = choice.copyWithUpdatedXY(x, y);
							}
						}
						var theChoice = choice;
						Platform.runLater(() -> {
							item.xSetter().accept(theChoice.x() - item.anchorX());
							item.ySetter().accept(theChoice.y() - item.anchorY());
						});
						selectedChoices.add(choice);
					}
					return true;
				}
		);
	}

	private Choice[] computeChoices(LayoutItem item) {
		var angle = GeometryUtilsFX.modulo360(item.angle());
		var angleRadian = GeometryUtilsFX.deg2rad(angle);
		var choices = new Choice[3];

		if (angle >= 45 && angle <= 135) { // up
			var x = item.anchorX() + gap * Math.cos(angleRadian) - 0.5 * item.width();
			var y = item.anchorY() + gap * Math.sin(angleRadian);

			choices[0] = new Choice(x, y, item.width(), item.height(), 0, item);
			choices[1] = new Choice(x + 0.5 * item.width(), y, item.width(), item.height(), 1, item);
			choices[2] = new Choice(x - 0.5 * item.width(), y, item.width(), item.height(), 2, item);
		} else if (angle > 135 && angle <= 225) { // left
			var x = item.anchorX() + gap * Math.cos(angleRadian) - item.width();
			var y = item.anchorY() + gap * Math.sin(angleRadian) - 0.5 * item.height();

			choices[0] = new Choice(x, y, item.width(), item.height(), 0, item);
			choices[1] = new Choice(x, y + 0.5 * item.height(), item.width(), item.height(), 1, item);
			choices[2] = new Choice(x, y - 0.5 * item.height(), item.width(), item.height(), 2, item);

		} else if (angle > 225 && angle <= 315) { // down
			var x = item.anchorX() + gap * Math.cos(angleRadian) - 0.5 * item.width();
			var y = item.anchorY() + gap * Math.sin(angleRadian) - item.height();

			choices[0] = new Choice(x, y, item.width(), item.height(), 0, item);
			choices[1] = new Choice(x + 0.5 * item.width(), y, item.width(), item.height(), 1, item);
			choices[2] = new Choice(x - 0.5 * item.width(), y, item.width(), item.height(), 2, item);
		} else { // right
			var x = item.anchorX() + gap * Math.cos(angleRadian);
			var y = item.anchorY() + gap * Math.sin(angleRadian) - 0.5 * item.height();

			choices[0] = new Choice(x, y, item.width(), item.height(), 0, item);
			choices[1] = new Choice(x, y + 0.5 * item.height(), item.width(), item.height(), 1, item);
			choices[2] = new Choice(x, y - 0.5 * item.height(), item.width(), item.height(), 2, item);
		}
		return choices;
	}

	/**
	 * item for radial layout
	 *
	 * @param anchorX        x coordinate of anchor
	 * @param anchorY        y coordiante of anchor
	 * @param angle          ideal angle to move label, in degrees
	 * @param widthProperty  label width
	 * @param heightProperty label height
	 * @param xSetter        method to set computed x-coordinate of label
	 * @param ySetter        method to set computed y-coordinate of label
	 */
	public static record LayoutItem(double anchorX, double anchorY, double angle, String label,
									ReadOnlyDoubleProperty widthProperty, ReadOnlyDoubleProperty heightProperty,
									Consumer<Double> xSetter, Consumer<Double> ySetter) {
		public double width() {
			return widthProperty.get();
		}

		public double height() {
			return heightProperty.get();
		}
	}

	private static record Choice(double x, double y, double width, double height, int priority, LayoutItem item) {
		private boolean intersects(Choice other) {
			return (x + width >= other.x && x <= other.x + other.width) && (y + height >= other.y && y <= other.y + other.height);
		}

		private boolean intersects(Choice other, double otherX, double otherY) {
			return (x + width >= otherX && x <= otherX + other.width) && (y + height >= otherY && y <= otherY + other.height);
		}

		private boolean intersects(Bounds bounds) {
			return (x + width >= bounds.getMinX() && x <= bounds.getMinX() + bounds.getWidth()) && (y + height >= bounds.getMinY() && y <= bounds.getMinY() + bounds.getHeight());
		}

		public Choice copyWithUpdatedXY(double x, double y) {
			return new Choice(x, y, width(), height(), priority(), item());
		}
	}

	private static int compare(Node v, Node w) {
		if (v.getDegree() < w.getDegree())
			return -1;
		else if (v.getDegree() > w.getDegree())
			return 1;
		else
			return Integer.compare(((Choice) v.getData()).priority(), ((Choice) w.getData()).priority());
	}
}
