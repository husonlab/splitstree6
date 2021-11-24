/*
 *  ShowNetworkConsole.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.network.network2view;

import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.NetworkNexusOutput;

import java.io.IOException;
import java.io.StringWriter;

public class ShowNetworkConsole extends Network2View {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, NetworkBlock inputData, ViewBlock outputData) throws IOException {
		try (var w = new StringWriter()) {
			w.write(inputData.getName() + ":\n");
			var writer = new NetworkNexusOutput();
			writer.write(w, taxaBlock, inputData);
			System.out.println(w);
		}
	}
}