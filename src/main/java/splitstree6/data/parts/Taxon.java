/*
 *  Taxon.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.data.parts;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * a taxon
 * Daniel Huson, 2021
 */
public class Taxon {
	private String name;
	private String _info;
	private StringProperty info;
	private String _displayLabel;
	private StringProperty displayLabel;

	public Taxon() {
	}

	public Taxon(String name) {
		this.name = name;
	}

	public Taxon(Taxon src) {
		this(src.getName());
		setInfo(src.getInfo());
		setDisplayLabel(src.getDisplayLabel());
	}

	public String getName() {
		return name;
	}

	public String getDisplayLabel() {
		if (displayLabel != null)
			return displayLabel.get();
		else
			return _displayLabel;
	}

	public StringProperty displayLabelProperty() {
		if (displayLabel == null)
			displayLabel = new SimpleStringProperty(getDisplayLabelOrName());
		return displayLabel;
	}

	public String getDisplayLabelOrName() {
		return getDisplayLabel() != null ? getDisplayLabel() : getName();
	}

	public void setDisplayLabel(String displayLabel) {
		if (this.displayLabel != null)
			this.displayLabel.set(displayLabel);
		else
			_displayLabel = displayLabel;
	}

	public String getInfo() {
		if (info != null)
			return info.get();
		else
			return _info;
	}

	public StringProperty infoProperty() {
		if (info == null)
			info = new SimpleStringProperty(_info);
		return info;
	}

	public void setInfo(String info) {
		if (this.info != null)
			this.info.set(info);
		else
			_info = info;
	}

	public String toString() {
		return getName();
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Taxon taxon) {
			return this.getName().equals(taxon.getName());
		} else
			return false;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public String getNameAndDisplayLabel(String separator) {
		if (getDisplayLabel() == null || getDisplayLabel().isBlank() || getDisplayLabel().equals(getName()))
			return name;
		else
			return name + separator + getDisplayLabel();
	}
}