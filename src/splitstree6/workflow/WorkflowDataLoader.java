/*
 * WorkflowDataLoader.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.workflow;

import jloda.util.parse.NexusStreamParser;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressPercentage;
import splitstree6.io.nexus.NexusExporter;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.Importer;
import splitstree6.io.readers.NexusImporter;
import splitstree6.io.utils.DataType;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static splitstree6.io.readers.ImportManager.UNKNOWN_FORMAT;

/**
 * workflow data loader
 * Daniel Huson, 11.2018
 */
public class WorkflowDataLoader {
    /**
     * loads data into a file
     */
    public static void load(Workflow workflow, String inputFile, String inputFormat) throws IOException {
        final var inputTaxaNode = workflow.getInputTaxaNode();
        if (inputTaxaNode == null)
            throw new IOException("Workflow does not have input taxon node");
        final var inputDataNode = workflow.getInputDataNode();
        if (inputDataNode == null)
            throw new IOException("Workflow does not have input data node");

        final var dataType = DataType.getDataType(inputFile);
        final var fileFormat = (inputFormat.equalsIgnoreCase(UNKNOWN_FORMAT) ? ImportManager.getInstance().getFileFormat(inputFile) : inputFormat);

        if (!dataType.equals(DataType.Unknown) && !fileFormat.equalsIgnoreCase(UNKNOWN_FORMAT)) {
            final var importer = ImportManager.getInstance().getImporterByDataTypeAndFileFormat(dataType, fileFormat);
            if (importer == null)
                throw new IOException("Can't open file '" + inputFile + "': Unknown data type or file format");
            else {
                try (final ProgressListener progress = new ProgressPercentage("Loading input data from file: " + inputFile + " (data type: '" + dataType + "' format: '" + fileFormat + "')")) {
                    var pair = Importer.apply(progress, importer, inputFile);
                    final var inputTaxa = pair.getFirst();
                    final var inputData = pair.getSecond();
                    final var w = new StringWriter();
                    w.write("#nexus\n");
                    var exporter = new NexusExporter();
                    exporter.export(w, inputTaxa, inputData);
                    try (var np = new NexusStreamParser(new StringReader(w.toString()))) {
                        NexusImporter.parse(np, inputTaxaNode.getDataBlock(), inputDataNode.getDataBlock());
                    }
                    inputTaxaNode.getDataBlock().updateShortDescription();
                    inputDataNode.getDataBlock().updateShortDescription();
                }
                System.err.println("Number of input taxa: " + workflow.getInputTaxaBlock().getNtax());
            }
        } else {
            throw new IOException("Unknown data or file format: " + inputFile);
        }
    }
}
