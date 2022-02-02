/*
 * NexusExporter.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.util.Basic;
import jloda.util.Pair;
import splitstree6.data.*;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * exports in Nexus format
 * Daniel Huson, 2.2018
 */
public class NexusExporter {
	private boolean prependTaxa = true;
	private String title;
	private Pair<String, String> link;
	private boolean asWorkflowOnly;

	public void setAsWorkflowOnly(boolean asWorkflowOnly) {
		this.asWorkflowOnly = asWorkflowOnly;
	}

	public void export(Writer w, TaxaBlock taxa) throws IOException {
		if (prependTaxa)
			w.write("#nexus\n");
		final TaxaNexusOutput output = new TaxaNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly)
			output.write(w, new TaxaBlock());
		else
			output.write(w, taxa);
	}

    /*
    @Override
    public void export(Writer w, AnalysisBlock block) throws IOException {
        final AnalysisNexusOutput output = new AnalysisNexusOutput();
        output.setTitleAndLink(getTitle(), getLink());
        if (asWorkflowOnly)
            output.write(w, new AnalysisBlock());
        else
            output.write(w, block);
    }
    */


	public void export(Writer w, TaxaBlock taxa, CharactersBlock block) throws IOException {
		if (prependTaxa)
			new TaxaNexusOutput().write(w, taxa);
		final CharactersNexusOutput output = new CharactersNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			final CharactersBlock newBlock = new CharactersBlock();
			newBlock.setDataType(block.getDataType());
			newBlock.setFormat(block.getFormat());
			output.write(w, new TaxaBlock(), new CharactersBlock());
		} else
			output.write(w, taxa, block);
	}

    /*
    public void export(Writer w, TaxaBlock taxa, GenomesBlock genomes) throws IOException {
        if (prependTaxa)
            new TaxaNexusOutput().write(w, taxa);
        final GenomesNexusOutput output = new GenomesNexusOutput();
        output.setTitleAndLink(getTitle(), getLink());
        if (asWorkflowOnly) {
            final GenomesBlock newBlock = new GenomesBlock();
            newBlock.setFormat(genomes.getFormat());
            output.write(w, new TaxaBlock(), newBlock);
        } else
            output.write(w, taxa, genomes);
    }

     */

	public void export(Writer w, TaxaBlock taxa, DistancesBlock distances) throws IOException {
		if (prependTaxa)
			new TaxaNexusOutput().write(w, taxa);
		final DistancesNexusOutput output = new DistancesNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			final DistancesBlock newBlock = new DistancesBlock();
			newBlock.setFormat(distances.getFormat());
			output.write(w, new TaxaBlock(), newBlock);
		} else
			output.write(w, taxa, distances);
	}

	public void export(Writer w, TaxaBlock taxa, NetworkBlock block) throws IOException {
		if (prependTaxa)
			new TaxaNexusOutput().write(w, taxa);
		final NetworkNexusOutput output = new NetworkNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			final NetworkBlock newBlock = new NetworkBlock();
			//newBlock.setFormat(block.getFormat());
			output.write(w, new TaxaBlock(), newBlock);
		} else
			output.write(w, taxa, block);
	}

	public void export(Writer w, TaxaBlock taxa, SplitsBlock block) throws IOException {
		if (prependTaxa)
			new TaxaNexusOutput().write(w, taxa);
		final SplitsNexusOutput output = new SplitsNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			final SplitsBlock newBlock = new SplitsBlock();
			newBlock.setFormat(block.getFormat());
			output.write(w, new TaxaBlock(), newBlock);
		} else
			output.write(w, taxa, block);
	}

	public void export(Writer w, TaxaBlock taxa, TreesBlock block) throws IOException {
		if (prependTaxa)
			new TaxaNexusOutput().write(w, taxa);
		final TreesNexusOutput output = new TreesNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			final TreesBlock newBlock = new TreesBlock();
			newBlock.setFormat(block.getFormat());
			output.write(w, new TaxaBlock(), newBlock);
		} else
			output.write(w, taxa, block);
	}

	public void export(Writer w, TaxaBlock taxa, TraitsBlock block) throws IOException {
		if (prependTaxa)
			new TaxaNexusOutput().write(w, taxa);
		final TraitsNexusOutput output = new TraitsNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			final TraitsBlock newBlock = new TraitsBlock();
			newBlock.setFormat(block.getFormat());
			output.write(w, new TaxaBlock(), newBlock);
		} else
			output.write(w, taxa, block);
	}

	public void export(Writer w, TaxaBlock taxa, ViewBlock block) throws IOException {
		if (prependTaxa)
			new TaxaNexusOutput().write(w, taxa);
		final var output = new ViewNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			final var newBlock = new ViewBlock();
			output.write(w, new TaxaBlock(), newBlock);
		} else
			output.write(w, taxa, block);

	}

	/**
	 * save an algorithms block
	 *
	 */
	public void export(Writer w, Algorithm algorithm) throws IOException {
		final AlgorithmNexusOutput output = new AlgorithmNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		output.write(w, algorithm);
	}

	/**
	 * export a datablock
	 */
	public void export(Writer w, TaxaBlock taxaBlock, DataBlock dataBlock) throws IOException {
		if (dataBlock instanceof CharactersBlock charactersBlock)
			export(w, taxaBlock, charactersBlock);
			// else if (dataBlock instanceof GenomesBlock)
			//     export(w, taxaBlock, (GenomesBlock) dataBlock);
		else if (dataBlock instanceof DistancesBlock distancesBlock)
			export(w, taxaBlock, distancesBlock);
		else if (dataBlock instanceof SplitsBlock splitsBlock)
			export(w, taxaBlock, splitsBlock);
		else if (dataBlock instanceof TreesBlock treesBlock)
			export(w, taxaBlock, treesBlock);
		else if (dataBlock instanceof NetworkBlock networkBlock)
			export(w, taxaBlock, networkBlock);
		else if (dataBlock instanceof ViewBlock viewBlock)
			export(w, taxaBlock, viewBlock);
		else if (dataBlock instanceof TraitsBlock)
			export(w, taxaBlock, (TraitsBlock) dataBlock);
			//else if (dataBlock instanceof ViewerBlock)
			//    export(w, taxaBlock, (ViewerBlock) dataBlock);
		else
			throw new IOException("Export " + Basic.getShortName(dataBlock.getClass()) + ": not implemented");
	}

	public boolean isPrependTaxa() {
		return prependTaxa;
	}

	public void setPrependTaxa(boolean prependTaxa) {
		this.prependTaxa = prependTaxa;
	}

	/**
	 * get the title of the block to be exported.
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * set the title of the block to be exported.
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * the link to be exported with the block
	 *
	 * @return list of links
	 */
	public Pair<String, String> getLink() {
		return link;
	}

	public void setLink(Pair<String, String> link) {
		this.link = link;
	}
}
