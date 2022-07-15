/*
 * NexusReader.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.io.readers.distances;

import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.DistancesFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.NexusImporter;

import java.io.IOException;

public class NexusReader extends DistancesReader {

	public NexusReader() {
		setFileExtensions("nexus", "nex", "nxs");
	}

	@Override
	public void read(ProgressListener progress, String fileName, TaxaBlock taxaBlock, DistancesBlock distances) throws IOException {
		NexusImporter.parse(fileName, taxaBlock, distances);
		if (distances.getFormat().getOptionTriangle() == DistancesFormat.Triangle.Both) {
			ensureSymmetric(taxaBlock, distances);
		}
	}

	@Override
	public boolean accepts(String file) {
		return getToClass().equals(NexusImporter.determineInputData(file));
	}

	public boolean acceptsFirstLine(String text) {
		return StringUtils.getFirstLine(text).toLowerCase().startsWith("#nexus");
	}
}
