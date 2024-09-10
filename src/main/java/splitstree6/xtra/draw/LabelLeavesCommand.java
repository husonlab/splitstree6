/*
 *  LabelLeavesCommand.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.scene.Node;
import javafx.scene.shape.Shape;
import javafx.util.Pair;
import jloda.fx.undo.UndoableRedoableCommand;
import jloda.util.Counter;
import jloda.util.Triplet;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * label all leaves command
 * Daniel Huson, 8.2024
 */
public class LabelLeavesCommand extends UndoableRedoableCommand {
	private final Runnable redo;
	private final Runnable undo;

	private final Map<Integer, Triplet<Integer, String, Node>> originalMap = new HashMap<>();

	public LabelLeavesCommand(DrawPane drawPane) {
		super("labeling");

		var network = drawPane.getNetwork();
		var nodeLabelsGroup = drawPane.getNodeLabelsGroup();

		for (var v : network.nodes()) {
			var triplet = new Triplet<Integer, String, Node>();
			if (network.hasTaxa(v))
				triplet.setFirst(network.getTaxon(v));
			triplet.setSecond(network.getLabel(v));
			network.setLabel(v, null);
			if (v.getInfo() instanceof javafx.scene.Node label) {
				triplet.setThird(label);
				label.translateXProperty().unbind();
				label.translateYProperty().unbind();
				nodeLabelsGroup.getChildren().remove(label);
			}
		}

		redo = () -> {
			nodeLabelsGroup.getChildren().clear();
			network.clearTaxa();
			var count = new Counter(0);
			network.nodeStream().filter(v -> v.getOutDegree() == 0).map(v -> new Pair<>(v, ((Shape) v.getData())))
					.sorted(Comparator.comparingDouble(a -> a.getValue().getTranslateY())).map(Pair::getKey)
					.forEach(v -> {
						var shape = (Shape) v.getData();
						var t = (int) count.incrementAndGet();
						var text = "t" + t;
						var label = drawPane.createLabel(v, text);
						network.addTaxon(v, t);
						v.setInfo(label);
					});
			drawPane.getNetworkFX().incrementLastUpdate();
		};

		undo = () -> {
			nodeLabelsGroup.getChildren().clear();
			network.clearTaxa();
			for (var id : originalMap.keySet()) {
				var triplet = originalMap.get(id);
				var v = network.findNodeById(id);
				var shape = (Shape) v.getData();

				var taxId = triplet.getFirst();
				if (taxId != null)
					network.addTaxon(v, taxId);
				var text = triplet.getSecond();
				if (text != null)
					network.setLabel(v, text);
				var label = triplet.getThird();
				if (label != null) {
					label.translateXProperty().bind(shape.translateXProperty());
					label.translateYProperty().bind(shape.translateYProperty());
					v.setInfo(label);
					nodeLabelsGroup.getChildren().add(label);
				}
			}
			drawPane.getNetworkFX().incrementLastUpdate();
		};
	}

	@Override
	public void undo() {
		undo.run();

	}

	@Override
	public void redo() {
		redo.run();
	}
}
