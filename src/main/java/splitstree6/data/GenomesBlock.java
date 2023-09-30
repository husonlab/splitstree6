/*
 * GenomesBlock.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.data;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import splitstree6.algorithms.genomes.genomes2genomes.GenomesTaxaFilter;
import splitstree6.data.parts.Genome;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;

/**
 * A genomes block
 * Daniel Huson, 2.2020
 */
public class GenomesBlock extends DataBlock {
    public static final String BLOCK_NAME = "GENOMES";

    private final ObservableList<Genome> genomes;

    private GenomesFormat format;

    public GenomesBlock() {
        genomes = FXCollections.observableArrayList();
        format = new GenomesFormat();
    }

    /**
     * shallow copy
     */
    public void copy(GenomesBlock that) {
        clear();
        genomes.addAll(that.getGenomes());
        format = that.getFormat();
    }

    @Override
    public void clear() {
        super.clear();
        genomes.clear();
    }

    @Override
    public int size() {
        return genomes.size();
    }

    /**
     * next the trees
     *
     * @return trees
     */
    public ObservableList<Genome> getGenomes() {
        return genomes;
    }

    public int getNGenomes() {
        return genomes.size();
    }

    /**
     * get t-th genomes
     *
     * @param t 1-based
     * @return tree
     */
    public Genome getGenome(int t) {
        return genomes.get(t - 1);
    }

    @Override
    public String getBlockName() {
        return BLOCK_NAME;
    }

    public void checkGenomesPresent() throws IOException {
        for (var t = 1; t <= getNGenomes(); t++) {
            if (getGenome(t).getLength() == 0)
                throw new IOException("Genome(" + t + "): not present or length 0");
        }
    }

    public GenomesFormat getFormat() {
        return format;
    }

    public void setFormat(GenomesFormat format) {
        this.format = format;
    }

    @Override
    public void updateShortDescription() {
        setShortDescription(size() == 1 ? "one genome" : size() + " genomes");
    }

    @Override
    public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
        return new GenomesTaxaFilter();
    }

    @Override
    public GenomesBlock newInstance() {
        return (GenomesBlock) super.newInstance();
    }

}
