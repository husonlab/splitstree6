/*
 *  ArgToExtendedNewick.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class ArgToExtendedNewick {
	public static void main(String[] args) {
		var pattern = Pattern.compile("\\)([^):,\\[]+)(?=[):,\\[#])");

		try (var r = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(args.length == 0 ? "stdin" : args[0]));
			 var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(args.length < 2 ? "stdout" : args[1])) {
			var parts = new ArrayList<String>();
			while (r.ready()) {
				var line = r.readLine();
				parts.add(line.trim());
				if (line.endsWith(";")) {
					var input = String.join("", parts);
					var cleaned = input.replaceAll("ARGNode_[a-zA-Z0-9]+", "");

					// Remove anything between '[' and ']', including the brackets
					cleaned = cleaned.replaceAll("\\[.*?\\]", "");

					// Optional: trim any extra spaces left behind
					cleaned = cleaned.replaceAll("\\s+", " ").trim();

					System.out.println(cleaned);
					parts.clear();
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
}
