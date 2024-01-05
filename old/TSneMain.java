/*
 *  TSneMain.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.more;

import com.jujutsu.tsne.barneshut.ParallelBHTsne;
import com.jujutsu.utils.TSneUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TSneMain {
	public static void main(String[] args) throws IOException {
		if (args.length != 1)
			throw new IOException("Usage: TSneMain file");

		var input = LabeledData.load(args[0]);

		var config = TSneUtils.buildConfig(input.matrix(), 2, input.n(), input.n() / 4.0, 1000);

		//var tSne=new SimpleTSne();
		//var tSne=new FastTSne();
		//var tSne=new BHTSne();
		var tSne = new ParallelBHTsne();
		var points = tSne.tsne(config);

		for (var i = 0; i < input.n(); i++) {
			System.err.print(input.labels()[i]);
			for (var j = 0; j < points[i].length; j++)
				System.err.printf(" %.3f", points[i][j]);
			System.err.println();
		}
	}

	public record LabeledData(int n, String[] labels, double[][] matrix) {
		public static LabeledData load(String fileName) throws IOException {
			try (var r = new BufferedReader(new FileReader(fileName))) {
				var n = Integer.parseInt(r.readLine().trim());
				var labels = new String[n];
				var matrix = new double[n][n];

				for (var i = 0; i < n; i++) {
					var line = r.readLine();
					var tokens = line.split("\s+");
					labels[i] = tokens[0].trim();
					for (var j = 0; j < n; j++) {
						matrix[i][j] = Double.parseDouble(tokens[j + 1].trim());
					}
				}
				return new LabeledData(n, labels, matrix);
			}
		}
	}
}
