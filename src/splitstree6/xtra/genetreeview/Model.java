/*
 *  Model.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.util.progress.ProgressPercentage;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.trees.TreesReader;
import splitstree6.io.utils.DataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * simple data model
 */
public class Model {
	private final TaxaBlock taxaBlock = new TaxaBlock();
	private final TreesBlock treesBlock = new TreesBlock();
	private final ObservableList<String> orderedGeneNames = FXCollections.observableArrayList();
	private ArrayList<String> initialGeneNameOrder;
	private ArrayList<Integer> treeOrder;
	private final LongProperty lastUpdate = new SimpleLongProperty(this, "lastUpdate", 0L);

	public TaxaBlock getTaxaBlock() {
		return taxaBlock;
	}

	public TreesBlock getTreesBlock() {
		return treesBlock;
	}

	public ObservableList<String> getOrderedGeneNames() {
		return orderedGeneNames;
	}

	public ArrayList<Integer> getTreeOrder() {
		return treeOrder;
	}

	public void load(File file) throws IOException {
		var importManager = ImportManager.getInstance();
		var dataType = DataType.getDataType(file.getPath());
		if (dataType == DataType.Trees) {
			var fileFormat = importManager.getFileFormat(file.getPath());
			var importer = (TreesReader) importManager.getImporterByDataTypeAndFileFormat(dataType, fileFormat);
			importer.read(new ProgressPercentage(), file.getPath(), taxaBlock, treesBlock);
			initializeTreeOrder();
			lastUpdate.set(System.currentTimeMillis());
		} else throw new IOException("File does not contain trees");
	}

	private void initializeTreeOrder() {
		initialGeneNameOrder = new ArrayList<>(treesBlock.getNTrees());
		treeOrder = new ArrayList<>(treesBlock.getNTrees());
		orderedGeneNames.clear();
		for (int i = 0; i < treesBlock.getNTrees(); i++) {
			orderedGeneNames.add(treesBlock.getTree(i+1).getName());
			initialGeneNameOrder.add(i,treesBlock.getTree(i+1).getName());
			treeOrder.add(i,i+1);
		}
	}

	public void resetTreeOrder() {
		treeOrder = new ArrayList<>(treesBlock.getNTrees());
		orderedGeneNames.clear();
		for (int i = 0; i < treesBlock.getNTrees(); i++) {
			orderedGeneNames.add(treesBlock.getTree(i+1).getName());
			treeOrder.add(i,i+1);
		}
	}

	public void setTreeOrder(TreeMap<Integer,String> position2geneName) {
		orderedGeneNames.clear();
		treeOrder = new ArrayList<>(treesBlock.getNTrees());
		int index = 0;
		for (var position : position2geneName.keySet()) {
			orderedGeneNames.add(position2geneName.get(position));
			treeOrder.add(index,initialGeneNameOrder.indexOf(position2geneName.get(position))+1);
			index++;
		}
	}

	public long getLastUpdate() {
		return lastUpdate.get();
	}

	public ReadOnlyLongProperty lastUpdateProperty() {
		return lastUpdate;
	}

	public void setGeneNames(String[] geneNames) {
		if (geneNames.length == treesBlock.getNTrees()) {
			initialGeneNameOrder = new ArrayList<>(treesBlock.getNTrees());
			for (int i = 0; i < treesBlock.getNTrees(); i++) {
				treesBlock.getTree(i+1).setName(geneNames[i]);
				initialGeneNameOrder.add(geneNames[i]);
			}
			orderedGeneNames.clear();
			for (int i : treeOrder) {
				orderedGeneNames.add(treesBlock.getTree(i).getName());
			}
		}
	}
}
