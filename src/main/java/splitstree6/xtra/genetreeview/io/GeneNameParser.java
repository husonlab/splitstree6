/*
 *  GeneNameParser.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.stage.Stage;
import splitstree6.xtra.genetreeview.model.Model;

import java.util.ArrayList;

public class GeneNameParser extends ParserDialog {

    private final Model model;

    public GeneNameParser(Stage parentStage, Model model) {
        super(parentStage, "gene names", model.getGeneTreeSet().size());
        this.model = model;
        this.setTitle("GeneNameParser");
    }

    @Override
    boolean parseFeatureToModel(String featureName, ArrayList<String> values) {
        if (model.getGeneTreeSet().setGeneNames(values)) {
            parsedProperty.set(true);
            return true;
        }
        return false;
    }
}
