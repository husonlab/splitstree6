/*
 *  BlocksToLaTeX.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.latex;

import jloda.util.StringUtils;
import splitstree6.data.*;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.utils.ReaderWriterBase;
import splitstree6.io.writers.ExportManager;
import splitstree6.workflow.DataBlock;

import java.lang.reflect.InvocationTargetException;

public class IOToLaTeX {
	public static final String HEADER = """
				\\documentclass{article}
				\\usepackage{graphicx}
				\\usepackage{hyperref}
				\\usepackage{fullpage}
			\\usepackage{parskip}
			\\usepackage{makeidx}
			   
			\\makeindex
			    
				\\begin{document}
			    								
				""";

	public static final String FOOTER = """
			   
			\\clearpage
			\\printindex
			\\end{document}""";

	public static void main(String[] args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
		System.out.println(HEADER);

		System.out.println("\\section{Supported import formats}\n");

		System.out.println(outputImporters(CharactersBlock.class) +
						   outputImporters(DistancesBlock.class) +
						   outputImporters(TreesBlock.class) +
						   outputImporters(SplitsBlock.class) +
						   outputImporters(NetworkBlock.class) +
						   outputImporters(GenomesBlock.class));

		System.err.println("\\section{Supported output formats}\n");

		System.out.println(outputExporters(TaxaBlock.class) +
						   outputExporters(CharactersBlock.class) +
						   outputExporters(DistancesBlock.class) +
						   outputExporters(TreesBlock.class) +
						   outputExporters(SplitsBlock.class) +
						   outputExporters(NetworkBlock.class) +
						   outputExporters(GenomesBlock.class));

		System.out.println(FOOTER);
	}

	public static String outputImporters(Class<? extends DataBlock> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
		var dataBlock = clazz.getDeclaredConstructor().newInstance();
		var blockName = dataBlock.getBlockName().toLowerCase();
		var buf = new StringBuilder();
		buf.append("\\subsection{Importers for a %s block}\\index{%s import}%n%n".formatted(blockName, blockName));
		var importManager = ImportManager.getInstance();
		var formats = importManager.getReaders(clazz).stream().map(ReaderWriterBase::getName).toList();

		buf.append("Can import %s data in the following formats: %s.%n".formatted(blockName, StringUtils.toString(formats, ", ")));
		buf.append("\\index{").append(StringUtils.toString(formats, " import}, \\index{")).append(" import}\n\n");
		return buf.toString();
	}

	public static String outputExporters(Class<? extends DataBlock> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
		var dataBlock = clazz.getDeclaredConstructor().newInstance();
		var blockName = dataBlock.getBlockName().toLowerCase();
		var buf = new StringBuilder();
		buf.append("\\subsection{Exporters for a %s block}\\index{%s export}%n".formatted(blockName, blockName));
		var exportManager = ExportManager.getInstance();
		var formats = exportManager.getExporters(clazz).stream().map(ReaderWriterBase::getName).filter(n -> !n.equals("PlainText")).toList();

		buf.append("Can export %s data in the following formats: %s.%n".formatted(blockName, StringUtils.toString(formats, ", ")));
		buf.append("\\index{").append(StringUtils.toString(formats, " export}, \\index{")).append(" export}\n\n");

		return buf.toString();
	}
}
