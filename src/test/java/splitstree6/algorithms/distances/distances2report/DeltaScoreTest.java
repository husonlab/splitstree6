/*
 *  DeltaScoreTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2report;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import splitstree6.data.ReportBlock;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the delta score of Holland et al. 2002
 */
public class DeltaScoreTest {
	static String report(double[][] d) throws Exception {
		var report = new ReportBlock();
		new DeltaScore().compute(new ProgressSilent(), taxa(d.length), distances(d), report);
		return String.join("\n", report.getLines());
	}

	static double[][] randomDistances(int n) {
		var random = new Random(n);
		var d = new double[n][n];
		for (var i = 0; i < n; i++) for (var j = i + 1; j < n; j++) d[i][j] = d[j][i] = 1 + random.nextDouble();
		return d;
	}

	/**
	 * the first number on the first line of the report, which is the delta score
	 */
	static double deltaScore(String report) {
		var matcher = java.util.regex.Pattern.compile("-?\\d+\\.\\d+").matcher(report.lines().findFirst().orElse(""));
		assertTrue(matcher.find(), "no number in the first line of the report: " + report.lines().findFirst().orElse(""));
		return Double.parseDouble(matcher.group());
	}

	/**
	 * for each quartet, with the three sums of distances of opposite pairs sorted as max, mid, min:
	 * (max - mid) / (max - min), averaged over all quartets
	 */
	@Test
	void deltaScoreAgreesWithTheDefinition() throws Exception {
		var n = 60;
		var d = randomDistances(n);
		double sum = 0;
		long count = 0;
		for (var a = 0; a < n; a++)
			for (var b = a + 1; b < n; b++)
				for (var c = b + 1; c < n; c++)
					for (var e = c + 1; e < n; e++) {
						double x = d[a][b] + d[c][e], y = d[a][c] + d[b][e], z = d[a][e] + d[b][c];
						var max = Math.max(x, Math.max(y, z));
						var min = Math.min(x, Math.min(y, z));
						var mid = x + y + z - max - min;
						if (max - min > 1e-7) sum += (max - mid) / (max - min);
						count++;
					}
		assertEquals(sum / count, deltaScore(report(d)), 1e-4);
	}

	/**
	 * the four-point condition holds for every quartet of a tree metric, so the delta score is 0
	 */
	@Test
	void deltaScoreOfATreeMetricIsZero() throws Exception {
		assertEquals(0, deltaScore(report(metric(randomTree(30, new Random(3)), 30))), 1e-4);
	}

	/**
	 * the number of quartets was once counted in an int, which overflows from 340 taxa: at 400 the score came out as
	 * -22.85. Distances drawn uniformly from [1,2] have a delta score of 0.500.
	 * Takes about ten seconds, so it runs only with -DexcludedTestGroups=none
	 */
	@Tag("slow")
	@Test
	void deltaScoreOfManyTaxa() throws Exception {
		assertEquals(0.5, deltaScore(report(randomDistances(345))), 0.01);
	}

	@Test
	void theReportNamesItsInput() throws Exception {
		var report = new ReportBlock();
		new DeltaScore().compute(new ProgressSilent(), taxa(10), distances(randomDistances(10)), report);
		assertEquals("DISTANCES", report.getInputBlockName());
	}

	@Test
	void smallAndDegenerateInput() throws Exception {
		for (var entry : degenerateDistances().entrySet()) {
			var text = report(entry.getValue());
			assertFalse(text.contains("NaN") || text.contains("Infinity"), entry.getKey() + ": " + text);
		}
	}
}
