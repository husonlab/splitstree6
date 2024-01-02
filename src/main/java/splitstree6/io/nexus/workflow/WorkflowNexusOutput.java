/*
 * WorkflowNexusOutput.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.nexus.workflow;

import jloda.fx.util.ProgramProperties;
import jloda.fx.window.NotificationManager;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.FileUtils;
import jloda.util.Pair;
import splitstree6.cite.ExtractMethodsText;
import splitstree6.data.SplitsTree6Block;
import splitstree6.io.nexus.NexusExporter;
import splitstree6.io.nexus.SplitsTree6NexusOutput;
import splitstree6.io.nexus.TaxaNexusOutput;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;

/**
 * write workflow in nexus
 * Daniel Huson, 2.2018
 */
public class WorkflowNexusOutput {
	/**
	 * save the workflow in nexus format
	 *
	 * @param fileName or stdout
	 */
	public void save(Workflow workflow, final String fileName, boolean asWorkflowOnly) throws IOException {
		if (!fileName.equals("stdout")) {
			var file = new File(fileName);
			if (file.getParentFile() != null && file.getParentFile().isDirectory())
				ProgramProperties.put("SaveDir", file.getParent());
		}
		try (var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(fileName)) {
			final var count = save(workflow, w, asWorkflowOnly);
			NotificationManager.showInformation("Saved " + count + " blocks to: " + fileName);
		}
	}

	/**
	 * write a workflow
	 */
	public int save(Workflow workflow, Writer w, boolean asWorkflowOnly) throws IOException {
		SplitsTree6Block splitsTree6Block = new SplitsTree6Block();
		splitsTree6Block.setOptionNumberOfDataNodes(workflow.getNumberOfDataNodes());
		splitsTree6Block.setOptionNumberOfAlgorithms(workflow.getNumberOfAlgorithmNodes());

		final NexusExporter nexusExporter = new NexusExporter();
		nexusExporter.setAsWorkflowOnly(asWorkflowOnly);
		nexusExporter.setPrependTaxa(false);

		w.write("#nexus [SplitsTree6]\n");

		(new SplitsTree6NexusOutput()).write(w, splitsTree6Block);

		if (workflow.getInputTaxaBlock() != null)
			TaxaNexusOutput.writeComments(w, workflow.getInputTaxaBlock());

		if (!asWorkflowOnly && workflow.getInputTaxaBlock().getNtax() > 0)
			w.write("\n[\n" + ExtractMethodsText.getInstance().apply(workflow).replaceAll("\\[", "(").replaceAll("]", ")") + "]\n");

		setupExporter(workflow.getInputTaxaNode(), nexusExporter);
		nexusExporter.export(w, workflow.getInputTaxaBlock());

		if (workflow.getInputTaxaBlock().getTraitsBlock() != null && workflow.getInputTaxaBlock().getTraitsBlock().getNTraits() > 0) {
			nexusExporter.setTitle("Input Traits");
			nexusExporter.export(w, workflow.getInputTaxaBlock(), workflow.getInputTaxaBlock().getTraitsBlock());
		}

		if (workflow.getInputTaxaBlock().getSetsBlock() != null && workflow.getInputTaxaBlock().getSetsBlock().size() > 0) {
			nexusExporter.setTitle("Input Sets");
			nexusExporter.export(w, workflow.getInputTaxaBlock(), workflow.getInputTaxaBlock().getSetsBlock());
		}

		setupExporter(workflow.getInputTaxaFilterNode(), nexusExporter);
		nexusExporter.export(w, workflow.getInputTaxaFilterNode().getAlgorithm());

		setupExporter(workflow.getWorkingTaxaNode(), nexusExporter);
		nexusExporter.export(w, workflow.getWorkingTaxaBlock());

		if (workflow.getWorkingTaxaBlock().getTraitsBlock() != null && workflow.getWorkingTaxaBlock().getTraitsBlock().getNTraits() > 0) {
			nexusExporter.setTitle("Working Traits");
			nexusExporter.export(w, workflow.getWorkingTaxaBlock(), workflow.getWorkingTaxaBlock().getTraitsBlock());
		}

		if (workflow.getWorkingTaxaBlock().getSetsBlock() != null && workflow.getWorkingTaxaBlock().getSetsBlock().size() > 0) {
			nexusExporter.setTitle("Working Sets");
			nexusExporter.export(w, workflow.getWorkingTaxaBlock(), workflow.getWorkingTaxaBlock().getSetsBlock());
		}

		setupExporter(workflow.getInputDataNode(), nexusExporter);
		nexusExporter.export(w, workflow.getInputTaxaBlock(), workflow.getInputDataNode().getDataBlock());

		setupExporter(workflow.getInputDataFilterNode(), nexusExporter);
		nexusExporter.export(w, workflow.getInputDataFilterNode().getAlgorithm());

		final var queue = new LinkedList<WorkflowNode>();
		queue.add(workflow.getWorkingDataNode());
		/* todo: input doesn't work (yet), so don't output
		if(workflow.getAlignmentViewNode()!=null)
			queue.add(workflow.getAlignmentViewNode());
		 */
		while (queue.size() > 0) {
			final WorkflowNode node = queue.poll();
			if (node instanceof final DataNode dataNode) {
				setupExporter(dataNode, nexusExporter);
				nexusExporter.export(w, workflow.getWorkingTaxaBlock(), dataNode.getDataBlock());
			} else {
				final var algorithm = (AlgorithmNode) node;
				setupExporter(algorithm, nexusExporter);
				nexusExporter.export(w, algorithm.getAlgorithm());
			}
			queue.addAll(node.getChildren());
		}

		return splitsTree6Block.size();
	}


	/**
	 * sets up the exporter so that it reports title and links
	 */
	private void setupExporter(DataNode dataNode, NexusExporter nexusExporter) {
		nexusExporter.setTitle(dataNode.getTitle());
		if (dataNode.getPreferredParent() != null)
			nexusExporter.setLink(new Pair<>(Algorithm.BLOCK_NAME, dataNode.getPreferredParent().getTitle()));
	}

	/**
	 * sets up the exporter so that it reports title and links
	 */
	private void setupExporter(AlgorithmNode algorithmNode, NexusExporter nexusExporter) {
		nexusExporter.setTitle(algorithmNode.getTitle());
		if (algorithmNode.getPreferredParent() != null)
			nexusExporter.setLink(new Pair<>(algorithmNode.getPreferredParent().getDataBlock().getBlockName(), algorithmNode.getPreferredParent().getTitle()));
	}
}
