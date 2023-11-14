/*
 *  LeafCounter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeafCounter {

        public static void main(String[] args) throws IOException {
            var filePath = args[0];
            FileInputStream fis = new FileInputStream(filePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            for (var line : br.lines().toList()) {
                Pattern pattern = Pattern.compile("[(,][^:]*:");
                Matcher matcher = pattern.matcher(line);
                var count = matcher.results().count();
                sb.append(count).append("\n");
            }
            br.close();
            String targetFilePath = filePath.replaceAll("\\.[a-z]*","");
            targetFilePath = targetFilePath + "_leafCounts.txt";
            System.out.println(targetFilePath);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFilePath)));
            bw.write(sb.toString());
            bw.close();
        }
    }
