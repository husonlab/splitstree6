/*
 * ViewNexusInput.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.ReportBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.writers.report.PlainTextWriter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * reportFairProportions block nexus input
 * Daniel Huson, 2.2023
 */
public class ReportNexusInput extends NexusIOBase {
	public static final String SYNTAX = """
			BEGIN REPORT;
				[TITLE title;]
				[LINK {type} = {title};]
				TEXT
					text...
				;
			END;
			""";

	public String getSyntax() {
		return SYNTAX;
	}

	/**
	 * parse a text block
	 */
	public void parse(NexusStreamParser np, TaxaBlock taxaBlock, ReportBlock reportBlock) throws IOException {
		np.matchBeginBlock("REPORT");
		parseTitleAndLink(np);

		np.matchIgnoreCase("TEXT");
		reportBlock.clear();
		var lineNo = np.lineno();
		var lineBuilder = new StringBuilder();
		while (!np.peekMatchIgnoreCase(";")) {
			var token = np.getWordRespectCase();

			if (lineBuilder.length() > 0)
				lineBuilder.append(" ");
			lineBuilder.append(token);

			if (np.lineno() > lineNo) {
				reportBlock.addLine(lineBuilder.toString());
				lineBuilder.setLength(0);
			}
		}
		if (lineBuilder.length() > 0)
			reportBlock.addLine(lineBuilder.toString());
		np.matchIgnoreCase(";");
		np.matchEndBlock();

		reportBlock.setText(StringUtils.toString(reportBlock.getLines(), "\n"));
	}

	public static void main(String[] args) throws IOException {
		var textBlock = new ReportBlock();
		textBlock.addLine("first line 0.1");
		textBlock.addLine("second	gogogo");
		textBlock.addLine("third 99;1");

		var errw = new OutputStreamWriter(System.err);
		System.err.println("first:");
		(new PlainTextWriter()).write(errw, null, textBlock);

		var w1 = new StringWriter();
		{
			(new ReportNexusOutput()).write(w1, null, textBlock);

			System.err.println("first as nexus:");
			System.err.println(w1);
		}

		var textBlock2 = new ReportBlock();
		try (var np = new NexusStreamParser(new StringReader(w1.toString()))) {
			(new ReportNexusInput()).parse(np, null, textBlock2);
		}

		System.err.println("second:");
		(new PlainTextWriter()).write(errw, null, textBlock2);

		{
			var w2 = new StringWriter();
			(new ReportNexusOutput()).write(w2, null, textBlock2);

			System.err.println("second as nexus:");
			System.err.println(w2);
		}

	}
}
