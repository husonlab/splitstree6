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

package splitstree6.wflow;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.util.Basic;

public class NamedBase {
	private final StringProperty name = new SimpleStringProperty(Basic.fromCamelCase(getClass().getSimpleName()));
	private final StringProperty shortDescription = new SimpleStringProperty("");

	public String getName() {
		return name.get();
	}

	public StringProperty nameProperty() {
		return name;
	}

	public void setName(String name) {
		this.name.set(name);
	}

	public String getShortDescription() {
		return shortDescription.get();
	}

	public StringProperty shortDescriptionProperty() {
		return shortDescription;
	}

	public void setShortDescription(String shortDescription) {
		this.shortDescription.set(shortDescription);
	}
}
