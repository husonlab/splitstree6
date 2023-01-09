/*
 * GenomesNexusFormat.java Copyright (C) 2023 Daniel H. Huson
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

import splitstree6.io.nexus.INexusFormat;

/**
 * Genomes format
 * Daniel Huson, 3.2020
 */
public class GenomesFormat implements INexusFormat {
    public enum CharactersType {dna, protein}

    private boolean optionLabels;
    private boolean optionAccessions;
    private boolean optionMultiPart;

    private CharactersType charactersType = CharactersType.dna;

    /**
     * the Constructor
     */
    public GenomesFormat() {
    }

    public GenomesFormat(GenomesFormat src) {
        optionLabels = src.optionLabels;
        optionAccessions = src.optionAccessions;
        optionMultiPart = src.optionMultiPart;
    }

    public boolean isOptionLabels() {
        return optionLabels;
    }

    public void setOptionLabels(boolean optionLabels) {
        this.optionLabels = optionLabels;
    }

    public boolean isOptionAccessions() {
        return optionAccessions;
    }

    public void setOptionAccessions(boolean optionAccessions) {
        this.optionAccessions = optionAccessions;
    }

    public boolean isOptionMultiPart() {
        return optionMultiPart;
    }

    public void setOptionMultiPart(boolean optionMultiPart) {
        this.optionMultiPart = optionMultiPart;
    }

    public CharactersType getCharactersType() {
        return charactersType;
    }

    public void setCharactersType(CharactersType charactersType) {
        this.charactersType = charactersType;
    }
}
