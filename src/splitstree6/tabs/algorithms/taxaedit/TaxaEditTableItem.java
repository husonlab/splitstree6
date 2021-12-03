/*
 *  TaxaEditTableItem.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.algorithms.taxaedit;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import splitstree6.data.parts.Taxon;

public class TaxaEditTableItem {
	private final int id;
	private final Taxon taxon;
	private final StringProperty displayLabel = new SimpleStringProperty(this, "DisplayLabel");
	private final BooleanProperty active = new SimpleBooleanProperty(this, "Active", false);

	public TaxaEditTableItem(int id, Taxon taxon) {
		this.id = id;
		this.taxon = taxon;
		setDisplayLabel(taxon.getDisplayLabelOrName());
		displayLabel.addListener((v, o, n) -> taxon.setDisplayLabel(n));
	}

	public boolean isActive() {
		return active.get();
	}

	public BooleanProperty activeProperty() {
		return active;
	}

	public void setActive(boolean active) {
		this.active.set(active);
	}

	public String getName() {
		return taxon.getName();
	}

	public String getDisplayLabel() {
		return displayLabel.get();
	}

	public StringProperty displayLabelProperty() {
		return displayLabel;
	}

	public void setDisplayLabel(String displayLabel) {
		this.displayLabel.set(displayLabel);
	}

	public String getNameAndDisplayLabel(String separator) {
		if (getDisplayLabel().isBlank() || getDisplayLabel().equals(getName()))
			return getName();
		else
			return getName() + separator + getDisplayLabel();
	}

	public Taxon getTaxon() {
		return taxon;
	}

	public int getId() {
		return id;
	}
}
