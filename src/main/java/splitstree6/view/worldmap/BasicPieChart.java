/*
 *  BasicPieChart.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.worldmap;

import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Pair;
import jloda.fx.util.ColorSchemeManager;
import jloda.fx.util.RunAfterAWhile;
import jloda.util.StringUtils;

/**
 * this is a very basic pie chart
 * Daniel Huson, 4.2024
 */
public class BasicPieChart extends Pane {
	private final ObservableList<Pair<String, Double>> data = FXCollections.observableArrayList();

	private final StringProperty colorScheme = new SimpleStringProperty(this, "colorScheme", "Twenty");

	private final DoubleProperty radius = new SimpleDoubleProperty(this, "radius", 32);

	private final StringProperty name = new SimpleStringProperty(this, "name", null);

	public BasicPieChart() {
		this(null);
	}

	public BasicPieChart(String name) {
		InvalidationListener invalidationListener = e -> RunAfterAWhile.applyInFXThread(this, this::update);
		data.addListener(invalidationListener);
		colorScheme.addListener(invalidationListener);
		radius.addListener(invalidationListener);
		setName(name);

		layoutXProperty().bind(radius.multiply(-1));
		layoutYProperty().bind(radius.multiply(-1));

	}

	private void update() {
		var total = data.stream().mapToDouble(p -> Math.max(0, p.getValue())).sum();

		var colorScheme = ColorSchemeManager.getInstance().getColorScheme(getColorScheme());
		if (colorScheme == null)
			colorScheme = ColorSchemeManager.getInstance().getColorScheme("Caspian8");

		getChildren().clear();

		var angle = 0.0;
		var buf = new StringBuilder();
		if (getName() != null)
			buf.append(getName()).append("\n");
		for (var i = 0; i < data.size(); i++) {
			var value = data.get(i).getValue();
			if (value > 0) {
				var name = data.get(i).getKey();
				var delta = 360.0 / total * value;
				var arc = new Arc();
				arc.setType(ArcType.ROUND);
				arc.centerXProperty().bind(radius);
				arc.centerYProperty().bind(radius);
				arc.radiusXProperty().bind(radius);
				arc.radiusYProperty().bind(radius);
				arc.setStartAngle(angle);
				arc.setLength(delta);
				arc.setFill(colorScheme.get(i % colorScheme.size()));
				arc.setStrokeWidth(0.25);
				arc.setStroke(Color.WHITE);
				arc.setUserData(name);
				angle += delta;
				getChildren().add(arc);
				buf.append("%s: %s%n".formatted(name, StringUtils.removeTrailingZerosAfterDot("%.1f", value)));
			}
		}
		Tooltip.install(this, new Tooltip(buf.toString()));
	}

	public ObservableList<Pair<String, Double>> getData() {
		return data;
	}

	public String getColorScheme() {
		return colorScheme.get();
	}

	public StringProperty colorSchemeProperty() {
		return colorScheme;
	}

	public void setColorScheme(String colorScheme) {
		this.colorScheme.set(colorScheme);
	}

	public double getRadius() {
		return radius.get();
	}

	public DoubleProperty radiusProperty() {
		return radius;
	}

	public void setRadius(double radius) {
		this.radius.set(radius);
	}

	public String getName() {
		return name.get();
	}

	public StringProperty nameProperty() {
		return name;
	}

	public void setName(String name) {
		this.name.set(name);
	}
}
