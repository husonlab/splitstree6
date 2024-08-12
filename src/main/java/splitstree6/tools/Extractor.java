/*
 *  Extractor.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools;

import jloda.seq.FastAFileIterator;
import jloda.util.FileUtils;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.UsageException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

public class Extractor {
	public static void main(String[] args) throws UsageException, IOException {
		if (args.length == 0)
			throw new UsageException("Extract infile(s)");
		for (var infile : args) {
			var file = new File(infile);
			var outfile = new File(file.getParent() + "/NYC_more", file.getName());
			if (outfile.exists())
				throw new IOException("Outfile exists: " + outfile);

			var strainSet = new HashSet<String>();
			var yearCountMap = new HashMap<String, Integer>();
			var totalIn = 0;
			var totalOut = 0;
			try (var it = new FastAFileIterator(infile);
				 var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(outfile.getPath())) {
				while (it.hasNext()) {
					var pair = it.next();
					var header = pair.getFirst();
					totalIn++;
					var strain = StringUtils.getTextBefore("|", StringUtils.getTextAfter("Strain Name:", header));
					//var isOutgroup = (strain.equals("A/Sydney/5/1997"));
					var isOutgroup = (strain.equals("A/Ontario/1252/2007"));
					if (isOutgroup)
						System.err.println("Found outgroup: " + strain);
					if (header.contains("H3N2") && header.contains("Human") && (strain.contains("New York") || isOutgroup)) { //  && (strain.contains("2005") ||  strain.contains("2006") || strain.contains("2007") || strain.contains("2008") || strain.contains("2009") || strain.contains("2010"))) {
						if (!strainSet.contains(strain)) {
							strainSet.add(strain);
							var last = strain.lastIndexOf("/");
							if (last != -1) {
								var year = strain.substring(last + 1);
								if (NumberUtils.isInteger(year) && NumberUtils.parseInt(year) >= 1999 || isOutgroup) {
									int count = yearCountMap.computeIfAbsent(year, k -> 0);
									if (isOutgroup || count < 16) {
										if (!isOutgroup)
											yearCountMap.put(year, count + 1);
										w.write(">" + strain + "\n");
										w.write(pair.getSecond() + "\n");
										totalOut++;
									}
								}
							}
						}
					}
				}
			}
			System.err.printf("Input:  %s%n", infile);
			System.err.printf("Total in:  %,d%n", totalIn);
			System.err.printf("Output: %s%n", outfile);
			System.err.printf("Total out: %,d%n", totalOut);
		}
	}
}
