/*
 *  StatementFilter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.window;

import splitstree6.main.SplitsTree6;

import java.io.*;

public class StatementFilter {
	public static InputStream apply(InputStream inputStream, String... tagLabels) {
		return new InputStream() {
			private final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			private String requiredEndTag = null;
			private String line = "";

			private final String[] startTags = new String[tagLabels.length / 2];
			private final String[] endTags = new String[tagLabels.length / 2];

			{
				for (var i = 0; i < tagLabels.length; i += 2) {
					startTags[i] = tagLabels[i];
					endTags[i] = tagLabels[i + 1];
				}
			}

			private int pos = 0;

			@Override
			public int read() throws IOException {
				if (line == null)
					return -1;
				while (true) {
					if (pos >= line.length()) {
						line = reader.readLine();
						if (line != null) {
							line += "\n";
							pos = 0;
						} else
							return -1;
					}

					if (requiredEndTag == null) {
						for (int i = 0; i < startTags.length; i++) {
							var startTag = startTags[i];
							var pos = line.indexOf(startTag, this.pos);
							if (pos >= 0) {
								line = line.substring(pos + startTag.length());
								requiredEndTag = endTags[i];
								this.pos = pos + startTag.length();
								break;
							}
						}
					}

					while (requiredEndTag != null) {
						var pos = line.indexOf(requiredEndTag);
						if (pos >= 0) {
							line = line.substring(pos + requiredEndTag.length());
							this.pos = pos + requiredEndTag.length();
							requiredEndTag = null;
						} else break;
					}
					if (pos < line.length()) {
						if (requiredEndTag == null)
							return line.charAt(pos++);
						else pos++;
					}
				}
			}
		};
	}

	/**
	 * filter some FXML items that I can't get to run on mobile, if not running on desktop
	 *
	 * @param ins input stream
	 * @return stream was some statements removed
	 */
	public static InputStream applyMobileFXML(InputStream ins) {
		if (SplitsTree6.isDesktop())
			return ins;
		else
			return apply(ins, "<accelerator>", "</accelerator>");
	}

	public static void main(String[] args) throws IOException {
		var text = """
					<Menu fx:id="helpMenu" text="Help">
						<MenuItem fx:id="checkForUpdatesMenuItem" text="Check For Updates..."/>
						<SeparatorMenuItem mnemonicParsing="false"/>
						<MenuItem fx:id="aboutMenuItem" text="About...">
							<accelerator>
								<KeyCodeCombination alt="UP" code="H" control="UP" meta="UP" shift="UP"
													shortcut="DOWN"/>
							</accelerator>
						</MenuItem>
					</Menu>
				""";

		try (var ins = new ByteArrayInputStream(text.getBytes())) {
			var filtered = apply(ins, "<accelerator>", "</accelerator>");
			try (var r = new BufferedReader(new InputStreamReader(filtered))) {
				String line;
				while ((line = r.readLine()) != null) {
					System.err.println(line);
				}
			}
		}
	}
}
