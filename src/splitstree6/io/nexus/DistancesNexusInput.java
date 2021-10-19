/*
 * DistancesNexusInput.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.io.nexus;

import jloda.util.IOExceptionWithLineNumber;
import jloda.util.StringUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.DistancesBlock;
import splitstree6.data.DistancesFormat;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * nexus input parser
 * Daniel Huson, 2.2018
 */
public class DistancesNexusInput extends NexusIOBase implements INexusInput<DistancesBlock> {
    public static final String SYNTAX =
			"""
					BEGIN DISTANCES;
						[TITLE {title};]
						[LINK {type} = {title};]
						[DIMENSIONS NTAX=number-of-taxa;]
						[FORMAT
							[TRIANGLE={LOWER|UPPER|BOTH}]
							[[NO] DIAGONAL]
							[LABELS={LEFT|NO}]
						;]
						MATRIX
							distance data in specified format
						;
						[VARMATRIX
							variance data in same specified format
						;]
					END;
					""";


    @Override
    public String getSyntax() {
        return SYNTAX;
    }

    /**
     * parse a distances block
     *
     * @return taxon names, if found
     */
    @Override
    public List<String> parse(NexusStreamParser np, TaxaBlock taxaBlock, DistancesBlock distancesBlock) throws IOException {
        try {
            distancesBlock.clear();

            final DistancesFormat format = distancesBlock.getFormat();

            np.matchBeginBlock("DISTANCES");
            parseTitleAndLink(np);

            if (taxaBlock.getNtax() == 0) {
                np.matchIgnoreCase("dimensions nTax=");
                taxaBlock.setNtax(np.getInt());
                np.matchIgnoreCase(";");
            } else if (np.peekMatchIgnoreCase("dimensions")) {
                np.matchIgnoreCase("dimensions nTax=" + taxaBlock.getNtax() + ";");
            }
            distancesBlock.setNtax(taxaBlock.getNtax());

            if (np.peekMatchIgnoreCase("FORMAT")) {
                final var tokens = np.getTokensLowerCase("format", ";");

                format.setOptionLabels(np.findIgnoreCase(tokens, "labels=left", true, format.isOptionLabels()));
                format.setOptionLabels(np.findIgnoreCase(tokens, "labels=no", false, format.isOptionLabels())); //DJB 14mar03

                format.setOptionDiagonal(np.findIgnoreCase(tokens, "diagonal=no", false, format.isOptionDiagonal()));
                format.setOptionDiagonal(np.findIgnoreCase(tokens, "diagonal=yes", true, format.isOptionDiagonal()));

				format.setOptionTriangleByLabel(np.findIgnoreCase(tokens, "triangle=", StringUtils.toString(DistancesFormat.Triangle.values(), " "), format.getOptionTriangle().toString()));

                // backward compatibility:
                format.setOptionLabels(np.findIgnoreCase(tokens, "no labels", false, format.isOptionLabels()));
                format.setOptionLabels(np.findIgnoreCase(tokens, "nolabels", false, format.isOptionLabels())); //DJB 14mar03
                format.setOptionLabels(np.findIgnoreCase(tokens, "labels", true, format.isOptionLabels()));

                format.setOptionDiagonal(np.findIgnoreCase(tokens, "no diagonal", false, format.isOptionDiagonal()));
                format.setOptionDiagonal(np.findIgnoreCase(tokens, "diagonal", true, format.isOptionDiagonal()));
                format.setOptionDiagonal(np.findIgnoreCase(tokens, "noDiagonal", false, format.isOptionDiagonal())); //DJB 14mar03

                // for compatibilty with splitstree3, swallow missing=?
                np.findIgnoreCase(tokens, "missing=", null, '?');

                if (tokens.size() != 0)
                    throw new IOExceptionWithLineNumber(np.lineno(), "'" + tokens + "' unexpected in FORMAT");
            }

            final var both = format.getOptionTriangle().equals(DistancesFormat.Triangle.Both);
            final var upper = format.getOptionTriangle().equals(DistancesFormat.Triangle.Upper);
            final var lower = format.getOptionTriangle().equals(DistancesFormat.Triangle.Lower);
            final var diag = format.isOptionDiagonal() ? 0 : 1;

            final ArrayList<String> taxonNamesFound = new ArrayList<>(distancesBlock.getNtax());

            {
                np.matchIgnoreCase("MATRIX");
                final var hasTaxonNames = taxaBlock.size() > 0;
                for (var t = 1; t <= distancesBlock.getNtax(); t++) {
                    if (format.isOptionLabels()) {
                        if (hasTaxonNames) {
                            np.matchLabelRespectCase(taxaBlock.getLabel(t));
                            taxonNamesFound.add(taxaBlock.getLabel(t));
                        } else
                            taxonNamesFound.add(np.getLabelRespectCase());
                    }
                    distancesBlock.set(t, t, 0);

                    final int left;
                    final int right;

                    if (lower) {
                        left = 1;
                        right = t - diag;
                    } else if (upper) {
                        left = t + diag;
                        right = distancesBlock.getNtax();
                    } else // both
                    {
                        left = 1;
                        right = distancesBlock.getNtax();
                    }

                    for (int q = left; q <= right; q++) {
                        double z = np.getDouble();

                        if (both)
                            distancesBlock.set(t, q, z);
                        else
                            distancesBlock.setBoth(t, q, z);

                    }
                }
                np.matchIgnoreCase(";");
            }

            if (np.peekMatchIgnoreCase("VARMATRIX")) {
                np.matchIgnoreCase("VARMATRIX");
                for (var t = 1; t <= distancesBlock.getNtax(); t++) {
                    if (format.isOptionLabels()) {
                        np.matchLabelRespectCase(taxaBlock.getLabel(t));
                    }

                    if (format.isOptionVariancesIO())
                        distancesBlock.setVariance(t, t, 0);

                    final int left;
                    final int right;

                    if (lower) {
                        left = 1;
                        right = t - diag;
                    } else if (upper) {
                        left = t + diag;
                        right = distancesBlock.getNtax();
                    } else // both
                    {
                        left = 1;
                        right = distancesBlock.getNtax();
                    }

                    for (var q = left; q <= right; q++) {
                        var z = np.getDouble();

                        if (format.isOptionVariancesIO()) {
                            if (both)
                                distancesBlock.setVariance(t, q, z);
                            else
                                distancesBlock.setVariance(t, q, z);
                        }
                    }
                }
                np.matchIgnoreCase(";");
            }

            np.matchEndBlock();

            if (both) {
                if (!isSymmetric(distancesBlock)) {
                    symmetrize(distancesBlock);
                    System.err.println("Warning: Distance matrix not symmetric: averaging between upper and lower parts");
                }
            }
            return taxonNamesFound;
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    /**
     * Check if the matrix is symmetric.
     *
     * @return boolean. True if it is symmetric.
     */
    private boolean isSymmetric(DistancesBlock distancesBlock) {
        int ntax = distancesBlock.getNtax();
        for (var i = 1; i <= ntax; i++) {
            for (var j = 1; j < i; j++)
                if (distancesBlock.get(i, j) != distancesBlock.get(j, i))
                    return false;
        }
        return true;
    }

    /**
     * Symmetrize the matrix. Replace d_ij and d_ji with (d_ij+d_ji)/2
     */
    private void symmetrize(DistancesBlock distancesBlock) {
        int ntax = distancesBlock.getNtax();
        for (var i = 1; i <= ntax; i++) {
            for (var j = 1; j < i; j++) {
                var d_ij = (distancesBlock.get(i, j) + distancesBlock.get(j, i)) / 2.0;
                distancesBlock.set(i, j, d_ij);
                distancesBlock.set(j, i, d_ij);
            }
        }
    }


    /**
     * is the parser at the beginning of a block that this class can parse?
     *
     * @return true, if can parse from here
     */
    public boolean atBeginOfBlock(NexusStreamParser np) {
        return np.peekMatchIgnoreCase("begin DISTANCES;");
    }
}
