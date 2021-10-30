/*
 *  SinkBlock.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;


public class SinkBlock extends DataBlock {
	private final StringProperty sink = new SimpleStringProperty();
	private final ObservableList<String> fileExtensions = FXCollections.observableArrayList();

	@Override
	public int size() {
		return isSinkSet() ? 1 : 0;
	}

	public String getSink() {
		return sink.get();
	}

	public StringProperty sinkProperty() {
		return sink;
	}

	public void setSink(String sink) {
		this.sink.set(sink);
	}

	public boolean isSinkSet() {
		return getSink() != null && !getSink().isBlank();
	}

	public ObservableList<String> getFileExtensions() {
		return fileExtensions;
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public SinkBlock newInstance() {
		return (SinkBlock) super.newInstance();
	}

	public static final String BLOCK_NAME = "SINK";

	@Override
	public void updateShortDescription() {
		setShortDescription(isSinkSet() ? "set" : "not set");
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

}
