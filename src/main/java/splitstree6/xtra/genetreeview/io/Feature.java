/*
 *  Feature.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.HashMap;

// class for tableView generation in FeatureOverviewDialog
public class Feature {
	private final StringProperty featureName;
	private final HashMap<String, String> treeName2value;

	Feature(String name, HashMap<String, String> treeName2value) {
		featureName = new SimpleStringProperty(name);
		this.treeName2value = treeName2value;
	}

	public String getFeatureName() {
		return featureName.get();
	}

	HashMap<String, String> getTreeName2value() {
		return treeName2value;
	}
}
