/*
 *  FeatureParser.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.io;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import splitstree6.xtra.genetreeview.model.FeatureType;
import splitstree6.xtra.genetreeview.model.Model;

import java.util.ArrayList;

public class FeatureParser extends ParserDialog {

	private final Model model;
	private String parsedFeatureName;
	private final TextField textFieldForFeatureName;
	private final ComboBox<String> featureTypeSelection;

	public FeatureParser(Stage parentStage, Model model) {
		super(parentStage, "feature name and values", model.getGeneTreeSet().size());
		this.model = model;
		this.setTitle("FeatureParser");

		textFieldForFeatureName = new TextField();
		textFieldForFeatureName.setPromptText("Enter Feature Name");
		vBoxOverall.getChildren().add(1, textFieldForFeatureName);
		featureTypeSelection = new ComboBox<>();
		featureTypeSelection.setPromptText("feature type");
		featureTypeSelection.getItems().addAll("numerical", "categorical");
		hBoxBottom.getChildren().add(0, featureTypeSelection);
	}

	@Override
	boolean parseFeatureToModel(String featureName, ArrayList<String> values) {
		FeatureType featureType;
		ArrayList<?> finalValues;
		if (featureTypeSelection.getSelectionModel().getSelectedItem() == null) return false;
		if (featureTypeSelection.getSelectionModel().getSelectedItem().equals("categorical")) {
			featureType = FeatureType.CATEGORICAL;
			finalValues = values;
		} else if (featureTypeSelection.getSelectionModel().getSelectedItem().equals("numerical")) {
			featureType = FeatureType.NUMERICAL;
			ArrayList<Double> numericalValues = new ArrayList<>();
			try {
				for (var value : values) {
					numericalValues.add(Double.parseDouble(value));
				}
				finalValues = numericalValues;
			} catch (Exception e) {
				return false;
			}
		} else return false;
		parsedFeatureName = textFieldForFeatureName.getText();
		if (model.getGeneTreeSet().addProperty(parsedFeatureName, finalValues, featureType)) {
			parsedProperty.set(true);
			return true;
		} else return false;
	}

	public String getParsedFeatureName() {
		return parsedFeatureName;
	}
}
