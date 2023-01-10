/*
 * GenomesNexusInput.java Copyright (C) 2023 Daniel H. Huson
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

import jloda.util.IOExceptionWithLineNumber;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.GenomesBlock;
import splitstree6.data.GenomesFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Genome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * nexus input parser
 * Daniel Huson, 3.2020
 */
public class GenomesNexusInput extends NexusIOBase implements INexusInput<GenomesBlock> {
    public static final String SYNTAX = """
            BEGIN GENOMES;
            \t[TITLE {title};]
            \t[LINK {type} = {title};]
            \t[DIMENSIONS NTAX=number-of-taxa;]
            \t[FORMAT
            \t\t[LABELS={YES|NO}]
            \t\t[ACCESSIONS={YES|NO}]
            \t\t[MULTIPART={YES|NO}]
            \t\t[FILES={YES|NO}]
            \t;]
            \tMATRIX
            \t\t[label] [accession] length {sequence | [number-of-parts] length {sequence|{file://file offset}} ...  length {sequence|{file offset}}},
            \t\t...
            \t\t[label] [accession] length {sequence | [number-of-parts] length {sequence|{file://file offset}} ...  length {sequence|{file offset}}}
            \t;]
            END;
            """;


    @Override
    public String getSyntax() {
        return SYNTAX;
    }

    /**
     * parse a genomes block
     *
     * @return taxon names, if found
     */
    @Override
    public List<String> parse(NexusStreamParser np, TaxaBlock taxaBlock, GenomesBlock genomesBlock) throws IOException {
        try {
            genomesBlock.clear();

            var format = genomesBlock.getFormat();

            np.matchBeginBlock(GenomesBlock.BLOCK_NAME);
            parseTitleAndLink(np);

            if (taxaBlock.getNtax() == 0) {
                np.matchIgnoreCase("dimensions nTax=");
                taxaBlock.setNtax(np.getInt());
                np.matchIgnoreCase(";");
            } else if (np.peekMatchIgnoreCase("dimensions")) {
                np.matchIgnoreCase("dimensions nTax=" + taxaBlock.getNtax() + ";");
            }

            if (np.peekMatchIgnoreCase("FORMAT")) {
                var tokens = np.getTokensLowerCase("format", ";");

                format.setOptionLabels(np.findIgnoreCase(tokens, "labels=yes", true, format.isOptionLabels()));
                format.setOptionLabels(np.findIgnoreCase(tokens, "labels=no", false, format.isOptionLabels()));

                format.setOptionAccessions(np.findIgnoreCase(tokens, "accessions=yes", true, format.isOptionAccessions()));
                format.setOptionAccessions(np.findIgnoreCase(tokens, "accessions=no", false, format.isOptionAccessions()));

                format.setOptionMultiPart(np.findIgnoreCase(tokens, "multiPart=yes", true, format.isOptionMultiPart()));
                format.setOptionMultiPart(np.findIgnoreCase(tokens, "multiPart=no", false, format.isOptionMultiPart()));

                if (np.findIgnoreCase(tokens, "dataType=dna", true, format.getCharactersType() == GenomesFormat.CharactersType.dna))
                    format.setCharactersType(GenomesFormat.CharactersType.dna);
                if (np.findIgnoreCase(tokens, "dataType=protein", true, format.getCharactersType() == GenomesFormat.CharactersType.protein))
                    format.setCharactersType(GenomesFormat.CharactersType.protein);

                if (tokens.size() != 0)
                    throw new IOExceptionWithLineNumber(np.lineno(), "'" + tokens + "' unexpected in FORMAT");
            }

            final var taxonNamesFound = new ArrayList<String>();

            {
                np.matchIgnoreCase("MATRIX");
                var hasTaxonNames = taxaBlock.size() > 0;
                var taxon = 0;

                while (!np.peekMatchIgnoreCase(";")) {
                    taxon++;
                    var genome = new Genome();
                    if (format.isOptionLabels()) {
                        final String name;
                        if (hasTaxonNames) {
                            name = taxaBlock.getLabel(taxon);
                            np.matchLabelRespectCase(name);
                        } else {
                            name = np.getLabelRespectCase();
                        }
                        taxonNamesFound.add(name);
                        genome.setName(name);
                    } else
                        genome.setName(taxaBlock.getLabel(taxon));
                    if (format.isOptionAccessions()) {
                        genome.setAccession(np.getLabelRespectCase());
                    }

                    var commaFound = false;
                    if (format.isOptionMultiPart()) {
                        genome.setLength(np.getInt());

                        var numberOfParts = np.getInt();

                        for (var p = 0; p < numberOfParts; p++) {
                            var part = new Genome.GenomePart();
                            var partLength = np.getInt();
                            var word = np.getWordFileNamePunctuation();

                            if (word.startsWith("file://")) {
                                part.setFile(word.replaceFirst("file://", ""), np.getLong(), partLength);
                            } else {
                                // todo: scan for partLength number of letters
                                part.setSequence(word.replaceAll("\\s+", "").getBytes(), partLength);
                            }
                            genome.getParts().add(part);
                        }
                    } else {
                        genome.setLength(np.getInt());
                        var part = new Genome.GenomePart();
                        var word = np.getWordFileNamePunctuation();
                        if (word.endsWith(",")) {
                            word = word.substring(0, word.length() - 1);
                            commaFound = true;
                        }

                        if (word.startsWith("file://")) {
                            part.setFile(word.replaceFirst("file://", ""), np.getLong(), genome.getLength());
                        } else {
                            // todo: scan for partLength number of letters
                            part.setSequence(word.replaceAll("\\s+", "").getBytes(), genome.getLength());
                        }
                        genome.getParts().add(part);
                    }
                    genomesBlock.getGenomes().addAll(genome);
                    if (!commaFound) {
                        if (np.peekMatchIgnoreCase(",")) {
                            np.matchIgnoreCase(",");
                        } else
                            break;
                    }
                }
                np.matchIgnoreCase(";");
            }
            np.matchEndBlock();
            return taxonNamesFound;
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    /**
     * is the parser at the beginning of a block that this class can parse?
     *
     * @return true, if can parse from here
     */
    public boolean atBeginOfBlock(NexusStreamParser np) {
        return np.peekMatchIgnoreCase("begin " + GenomesBlock.BLOCK_NAME + ";");
    }
}
