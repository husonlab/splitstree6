/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  AttachNodeDialogPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.dialog.attachnode;

import javafx.util.Pair;
import jloda.util.PluginClassLoader;
import jloda.util.StringUtils;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

public class AttachNodeDialogPresenter {

	public AttachNodeDialogPresenter(DataNode dataNode, AttachNodeDialogController controller) {

		controller.getAlgorithmCBox().getItems().addAll(getAllAlgorithms(dataNode));
		controller.getAlgorithmCBox().valueProperty().addListener((v, o, n) ->
				controller.getDataTypeLabel().setText(n == null ? "" : StringUtils.fromCamelCase(n.getToClass().getSimpleName().replaceAll("Block", ""))));

		if (controller.getAlgorithmCBox().getItems().size() > 0)
			controller.getAlgorithmCBox().setValue(controller.getAlgorithmCBox().getItems().get(0));
	}

	private Collection<Algorithm> getAllAlgorithms(DataNode dataNode) {
		var list = new ArrayList<Pair<String, Algorithm>>();
		for (var algorithm : PluginClassLoader.getInstances(Algorithm.class, "splitstree6.algorithms")) {
			if (algorithm.getFromClass() == dataNode.getDataBlock().getClass())
				list.add(new Pair<>(algorithm.getName(), algorithm));
		}
		list.sort(Comparator.comparing(Pair::getKey)); // sort alphabetically
		return list.stream().map(Pair::getValue).collect(Collectors.toList());
	}
}
