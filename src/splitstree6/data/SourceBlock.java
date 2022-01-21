/*
 *  SourceBlock.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

/**
 * represents the input source for the analysis
 * Daniel Huson, 10.2021
 */
public class SourceBlock extends DataBlock {
	private final ObservableList<String> sources = FXCollections.observableArrayList();
	private final ObservableList<String> fileExtensions = FXCollections.observableArrayList();
	private final BooleanProperty allowMultiFileInput = new SimpleBooleanProperty(false);
	private final BooleanProperty usingInputEditor = new SimpleBooleanProperty(false);

	private final ObjectProperty<Class<? extends DataBlock>> supportedDataBlockClass = new SimpleObjectProperty<>(DataBlock.class);

	public SourceBlock() {
		allowMultiFileInput.bind(supportedDataBlockClass.isEqualTo(TreesBlock.class));
	}

	@Override
	public int size() {
		return sources.size();
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

	public static final String BLOCK_NAME = "SOURCE";

	@Override
	public void updateShortDescription() {
		setShortDescription(size() == 1 ? "1 file" : String.format("%,d files", size()));
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

	public Class<? extends DataBlock> getSupportedDataBlockClass() {
		return supportedDataBlockClass.get();
	}

	public ObjectProperty<Class<? extends DataBlock>> supportedDataBlockClassProperty() {
		return supportedDataBlockClass;
	}

	public void setSupportedDataBlockClass(Class<? extends DataBlock> supportedDataBlockClass) {
		this.supportedDataBlockClass.set(supportedDataBlockClass);
	}

	public boolean isAllowMultiFileInput() {
		return allowMultiFileInput.get();
	}

	public ReadOnlyBooleanProperty allowMultiFileInputProperty() {
		return allowMultiFileInput;
	}

	public boolean isUsingInputEditor() {
		return usingInputEditor.get();
	}

	public BooleanProperty usingInputEditorProperty() {
		return usingInputEditor;
	}

	public void setUsingInputEditor(boolean usingInputEditor) {
		this.usingInputEditor.set(usingInputEditor);
	}
}
