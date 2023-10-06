/*
 *  FeatureParserForPastedTrees.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;

public class FeatureParserForPastedTrees extends ParserDialog {

    private final ArrayList<TextField> inputTextFields = new ArrayList<>();
    private final HashMap<String,ArrayList<String>> parsedFeatureValues = new HashMap<>();

    public FeatureParserForPastedTrees(Stage parentStage, ArrayList<String> featureNames, int expectedNumberOfValues) {
        super(parentStage, featureNames.get(0), expectedNumberOfValues);
        this.setTitle("FeatureParser");
        introLabel.setText("Enter feature values for pasted trees:");

        if (featureNames.size() == 0) this.close();
        else {
            vBoxInput.getChildren().clear();
            for (String featureName : featureNames) {
                HBox hBoxInput = new HBox(5);
                hBoxInput.setPadding(new Insets(5));
                hBoxInput.getChildren().add(new Label(featureName));
                TextField inputTextField = new TextField();
                inputTextField.setPromptText("values");
                hBoxInput.getChildren().add(inputTextField);
                inputTextFields.add(inputTextField);
                vBoxInput.getChildren().add(hBoxInput);
            }
            startButton.setOnAction(e -> {
                boolean parsingFailed = false;
                for (var inputTextField : inputTextFields) {
                    String textInput = inputTextField.getText();
                    ArrayList<String> values = extractFeatureValues(textInput, expectedNumberOfValues);
                    if (values != null) {
                        parsedFeatureValues.put(featureNames.get(inputTextFields.indexOf(inputTextField)), values);
                    }
                    else {
                        infoLabel.setText("Import failed due to " +
                                featureNames.get(inputTextFields.indexOf(inputTextField)) + " values");
                        parsingFailed = true;
                        break;
                    }
                }
                if (!parsingFailed) {
                    if (parseFeatureToModel(null, null))
                        this.close();
                }
            });
        }
    }

    @Override
    boolean parseFeatureToModel(String featureName, ArrayList<String> values) {
        parsedProperty.setValue(true);
        return true;
    }

    public HashMap<String,ArrayList<String>> getParsedFeatureValues() {
        return parsedFeatureValues;
    }
}
