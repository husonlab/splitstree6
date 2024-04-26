/*
 *  TryWorldMap.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.utils.worldmap;

import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import jloda.fx.control.ZoomableScrollPane;

public class TryWorldMap extends Application {
	@Override
	public void start(Stage stage) throws Exception {
		var worldMap1 = new WorldMap();
		var worldMap2 = new WorldMap();

		var scale = new SimpleDoubleProperty(1.0);

		var zoomIn = new Button("++");
		zoomIn.setOnAction(e -> scale.set(scale.get() * 1.1));
		var zoomOut = new Button("--");
		zoomOut.setOnAction(e -> scale.set(scale.get() / 1.1));
		scale.addListener((v, o, n) -> worldMap1.changeScale(o.doubleValue(), n.doubleValue()));
		scale.addListener((v, o, n) -> worldMap2.changeScale(o.doubleValue(), n.doubleValue()));

		var hbox = new HBox(worldMap1, worldMap2);
		hbox.setSpacing(-5);
		var scrollPane = new ZoomableScrollPane(hbox);
		scrollPane.setLockAspectRatio(true);
		scrollPane.setUpdateScaleMethod(() -> {
			var factorX = scrollPane.getZoomFactorX();
			scale.set(scale.get() * factorX);
		});
		scrollPane.setPannable(true);

		var root = new BorderPane(scrollPane);
		root.setTop(new ToolBar(zoomIn, zoomOut));

		stage.setScene(new Scene(root, 800, 800));
		stage.sizeToScene();
		stage.show();
	}
}
