/*
 *  ViewBlock.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import splitstree6.view.IView;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;


public class ViewBlock extends DataBlock {
	private final ObjectProperty<String> inputBlockName = new SimpleObjectProperty<>();
	private final ObjectProperty<IView> view = new SimpleObjectProperty<>();

	@Override
	public int size() {
		return getView() == null ? 0 : getView().size();
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public ViewBlock newInstance() {
		return (ViewBlock) super.newInstance();
	}

	public static final String BLOCK_NAME = "VIEW";

	@Override
	public void updateShortDescription() {
		setShortDescription("a visualization of the " + getName().toLowerCase());
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

	public String getInputBlockName() {
		return inputBlockName.get();
	}

	public ObjectProperty<String> inputBlockNameProperty() {
		return inputBlockName;
	}

	public void setInputBlockName(String inputBlockName) {
		this.inputBlockName.set(inputBlockName);
	}

	public IView getView() {
		return view.get();
	}

	public ObjectProperty<IView> viewProperty() {
		return view;
	}

	public void setView(IView view) {
		this.view.set(view);
	}

	public static IView createView(String inputBlockName, String name) {
		return null;
	}
}
