/*
 *  Taxon.java Copyright (C) 2022 Daniel H. Huson
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

public class Taxon {
	private String name;
	private String info;
	private String displayLabel;

	public Taxon() {
	}

	public Taxon(String name) {
		setName(name);
	}

	public Taxon(Taxon src) {
		name = src.name;
		displayLabel = src.displayLabel;
		info = src.info;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDisplayLabel() {
		return displayLabel;
	}

	public String getDisplayLabelOrName() {
		return displayLabel != null ? displayLabel : name;
	}

	public void setDisplayLabel(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
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
			return getName();
		else
			return getName() + separator + getDisplayLabel();
	}
}