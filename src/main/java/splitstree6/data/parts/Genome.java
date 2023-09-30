/*
 * Genome.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.data.parts;

import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;

/**
 * represents data associated with a genome
 * Daniel Huson, 2.2020
 */
public class Genome {
    private String name;
    private String accession;
    private int length;
    final private ArrayList<GenomePart> parts = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccession() {
        return accession;
    }

    public void setAccession(String accession) {
        this.accession = accession;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public ArrayList<GenomePart> getParts() {
        return parts;
    }

    /**
     * get the number of parts (e.g. chromosomes or contigs)
     *
     * @return number of parts
     */
    public int getNumberOfParts() {
        return parts.size();
    }

    /**
     * get the name of a part
     *
     * @param i 1-based
     * @return name
     */
    public String getName(int i) {
        return parts.get(i).getName();
    }

    /**
     * get the length of a part
     *
     * @param i 1-based
     * @return number of letters
     */
    public int length(int i) {
        return parts.get(i).getLength();
    }

    /**
     * get the sequence of a part
     *
     * @param i 1-based
     */
    public GenomePart getPart(int i) {
        return parts.get(i);
    }

    public Iterable<byte[]> parts() {
        return () -> new Iterator<>() {
            int i = 0;
            byte[] next = (i < getNumberOfParts() ? getPart(i++).getSequence() : null);

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public byte[] next() {
                final byte[] result = next;
                next = (i < getNumberOfParts() ? getPart(i++).getSequence() : null);
                return result;
            }
        };
    }

    public int computeLength() {
        int length = 0;
        for (int i = 0; i < getNumberOfParts(); i++)
            length += length(i);
        return length;
    }

    public Optional<String> getMissingFile() {
        for (var part : getParts()) {
            if (part.getSequence() == null && !FileUtils.fileExistsAndIsNonEmpty(part.getFile()))
                return Optional.of(part.getFile());
        }
        return Optional.empty();
    }

    public void replaceMissingFile(String oldPath, String newPath) {
        int i = oldPath.length();
        int j = newPath.length();
        while (i > 0 && j > 0 && oldPath.charAt(i - 1) == newPath.charAt(j - 1)) {
            i--;
            j--;
        }
        final String commonSuffix = newPath.substring(j);
        final String oldPrefix = oldPath.substring(0, oldPath.length() - commonSuffix.length());
        final String newPrefix = newPath.substring(0, newPath.length() - commonSuffix.length());

        for (var part : getParts()) {
            if (part.getFile().startsWith(oldPrefix))
                part.setFile(newPrefix + part.getFile().substring(oldPrefix.length()), part.getOffset(), part.getLength());
        }
    }


    public static class GenomePart {
        private String name;
        private byte[] sequence;
        private String file;
        private long offset;
        private int length;

        public GenomePart() {
        }

        public GenomePart(String name, byte[] sequence, String file, long offset, int length) {
            this.name = name;
            this.sequence = sequence;
            this.file = file;
            this.offset = offset;
            this.length = length;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public byte[] getSequence() {
            if (sequence != null)
                return sequence;
            else {
                if (file != null) {
                    try (BufferedReader ins = new BufferedReader(new InputStreamReader(FileUtils.getInputStreamPossiblyZIPorGZIP(file)))) {
                        long toSkip = offset;
                        while (toSkip > 0) {
                            toSkip -= ins.skip(toSkip);
                            if (!ins.ready())
                                break;
                        }
                        String line = ins.readLine();
                        if (line != null && line.startsWith(">"))
                            line = ins.readLine();

                        if (line != null) {
                            int length = 0;
                            final ArrayList<byte[]> lines = new ArrayList<>();
                            do {
                                final byte[] bytes = line.getBytes();
                                length += bytes.length;
                                lines.add(bytes);
                                if (length >= getLength())
                                    break;
                                do { // skip headers
                                    line = ins.readLine();
                                } while (line != null && line.startsWith(">"));
                            }
                            while (line != null);
                            return StringUtils.concatenate(lines);
                        }
                    } catch (IOException e) {
                        NotificationManager.showError("Read file failed: " + e);
                        if (e.getMessage().contains("Unexpected end")) {
                            System.err.println("Appears to be a corrupted file, deleting: " + file);
                            FileUtils.deleteFileIfExists(file);
                        }
                        return null;
                    }
                }
                return null;
            }
        }

        public void setSequence(byte[] sequence, int length) {
            if (sequence != null) {
                file = null;
                offset = 0;
            }
            this.sequence = sequence;
            this.length = length;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file, long offset, int length) {
            if (file != null)
                sequence = null;
            this.file = file;
            this.offset = offset;
            this.length = length;
        }

        public long getOffset() {
            return offset;
        }

        public int getLength() {
            return length;
        }
    }
}
