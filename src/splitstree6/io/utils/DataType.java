/*
 *  DataType.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.utils;

import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.characters.CharactersReader;
import splitstree6.io.readers.distances.DistancesReader;
import splitstree6.io.readers.genomes.GenomesReader;
import splitstree6.io.readers.network.NetworkReader;
import splitstree6.io.readers.splits.SplitsReader;
import splitstree6.io.readers.text.TextReader;
import splitstree6.io.readers.trees.TreesReader;
import splitstree6.io.readers.view.ViewReader;

/**
 * datatype associated with an input file or reader
 * Daniel Huson, 10.2022
 */
public enum DataType {
	Genomes, Characters, Distances, Trees, Splits, Network, View, Text, Unknown;

	public static DataType getDataType(String fileName) {
		return getDataType(ImportManager.getInstance().getReader(fileName));

	}

	public static DataType getDataType(DataReaderBase<?> reader) {
		if (reader instanceof CharactersReader) {
			return DataType.Characters;
		} else if (reader instanceof GenomesReader) {
			return DataType.Genomes;
		} else if (reader instanceof DistancesReader) {
			return DataType.Distances;
		} else if (reader instanceof TreesReader) {
			return DataType.Trees;
		} else if (reader instanceof SplitsReader) {
			return DataType.Splits;
		} else if (reader instanceof NetworkReader) {
			return DataType.Network;
		} else if (reader instanceof ViewReader) {
			return DataType.View;
		} else if (reader instanceof TextReader) {
			return DataType.Text;
		} else
			return DataType.Unknown;
	}
}
