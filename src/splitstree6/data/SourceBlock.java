/*
 *  SourceBlock.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.data;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;


public class SourceBlock extends DataBlock {
	private final ObservableList<String> sources = FXCollections.observableArrayList();
	private final ObservableList<String> fileExtensions = FXCollections.observableArrayList();

	@Override
	public int size() {
		return sources.size();
	}

	@Override
	public String getInfo() {
		return "Sources: " + sources.size();
	}

	public ObservableList<String> getSources() {
		return sources;
	}

	public ObservableList<String> getFileExtensions() {
		return fileExtensions;
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public SourceBlock newInstance() {
		return (SourceBlock) super.newInstance();
	}

}
