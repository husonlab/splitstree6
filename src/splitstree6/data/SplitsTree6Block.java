/*
 *  SplitsTree6Block.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.*;
import splitstree6.main.Version;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

/**
 * splitstree6 datablock
 * Daniel Huson, 10.2021
 */
public class SplitsTree6Block extends DataBlock {
	public static final String BLOCK_NAME = "SPLITSTREE6";

	final private IntegerProperty optionNumberOfDataNodes = new SimpleIntegerProperty(0);
	final private IntegerProperty optionNumberOfAlgorithms = new SimpleIntegerProperty(0);
	final private StringProperty optionVersion = new SimpleStringProperty(Version.SHORT_DESCRIPTION);
	final private LongProperty optionCreationDate = new SimpleLongProperty(System.currentTimeMillis());


	public void clear() {
		super.clear();
		setOptionNumberOfDataNodes(0);
		setOptionNumberOfAlgorithms(0);
		setOptionCreationDate(System.currentTimeMillis());
		setOptionVersion(Version.SHORT_DESCRIPTION);
	}

	@Override
	public int size() {
		return getOptionNumberOfDataNodes() + getOptionNumberOfAlgorithms();
	}

	public String getOptionVersion() {
		return optionVersion.get();
	}

	public StringProperty optionVersionProperty() {
		return optionVersion;
	}

	public void setOptionVersion(String optionVersion) {
		this.optionVersion.set(optionVersion);
	}

	public long getOptionCreationDate() {
		return optionCreationDate.get();
	}

	public LongProperty optionCreationDateProperty() {
		return optionCreationDate;
	}

	public void setOptionCreationDate(long optionCreationDate) {
		this.optionCreationDate.set(optionCreationDate);
	}

	public int getOptionNumberOfDataNodes() {
		return optionNumberOfDataNodes.get();
	}

	public IntegerProperty optionNumberOfDataNodesProperty() {
		return optionNumberOfDataNodes;
	}

	public void setOptionNumberOfDataNodes(int optionNumberOfDataNodes) {
		this.optionNumberOfDataNodes.set(optionNumberOfDataNodes);
	}

	public int getOptionNumberOfAlgorithms() {
		return optionNumberOfAlgorithms.get();
	}

	public IntegerProperty optionNumberOfAlgorithmsProperty() {
		return optionNumberOfAlgorithms;
	}

	public void setOptionNumberOfAlgorithms(int optionNumberOfAlgorithms) {
		this.optionNumberOfAlgorithms.set(optionNumberOfAlgorithms);
	}

	@Override
	public void updateShortDescription() {
		setShortDescription(String.format("%,d data nodes and %,d algorithms", getOptionNumberOfDataNodes(), getOptionNumberOfAlgorithms()));
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}
}
