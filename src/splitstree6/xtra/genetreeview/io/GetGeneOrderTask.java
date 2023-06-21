/*
 *  GetGeneOrderTask.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.io;

import javafx.concurrent.Task;
import splitstree6.xtra.genetreeview.Model;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetGeneOrderTask extends Task<TreeMap<Integer,String>> {

    private final Model model;
    private final String taxonName;

    public GetGeneOrderTask(Model model, String taxonName) {
        this.model = model;
        this.taxonName = taxonName;
    }

    @Override
    protected TreeMap<Integer, String> call() throws Exception {
        // Getting the gene order from ncbi using a simple E-utility pipeline: ESearch-ESummary
        // TODO: try to directly parse the list of gene names instead of getting each gene one by one (maybe with EPost)
        TreeMap<Integer,String> orderedGeneNames = new TreeMap<>();
        System.out.println(taxonName);
        var base = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/";
        /* // EPost works with a list of genes but not with taxon as additional query term
        StringBuilder geneListBuilder = new StringBuilder();
        for (var index = 0; index < model.getTreesBlock().getNTrees(); index++) {
            String geneName = model.getOrderedGeneNames().get(index);
            geneListBuilder.append(geneName).append(",");
        }
        String geneList = geneListBuilder.deleteCharAt(geneListBuilder.length()-1).toString();
        System.out.println(geneList);

        var query = taxonName + "[organism]+AND+" + geneList + "[gene]";
        var searchUrl = base + "epost.fcgi?db=gene&term=" + query;
        var connection = (HttpURLConnection) (new URL(searchUrl)).openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        var searchResult = new String(connection.getInputStream().readAllBytes());
        connection.disconnect();
        //System.out.println(searchResult);*/


        for (var index = 0; index < model.getTreesBlock().getNTrees(); index++) {
            // ESearch
            String geneName = model.getOrderedGeneNames().get(index);
            System.out.println(geneName);
            var query = taxonName + "[organism]+AND+" + geneName + "[gene]";
            var searchUrl = base + "esearch.fcgi?db=gene&term=" + query + "&usehistory=y";
            var connection = (HttpURLConnection) (new URL(searchUrl)).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            var searchResult = new String(connection.getInputStream().readAllBytes());
            connection.disconnect();
            //System.out.println(searchResult);
            // Extraction of QueryKey and WebEnv for the next step in the pipeline
            String webEnv = null;
            String queryKey = null;
            Matcher queryKeyMatcher = Pattern.compile("<QueryKey>(\\d+)</QueryKey>").matcher(searchResult);
            if (queryKeyMatcher.find()) {
                queryKey = queryKeyMatcher.group(1);
            }
            Matcher webEnvMatcher = Pattern.compile("<WebEnv>(\\S+)</WebEnv>").matcher(searchResult);
            if (webEnvMatcher.find()) {
                webEnv = webEnvMatcher.group(1);
            }
            if (queryKey == null | webEnv == null) return null;

            // ESummary
            var summaryUrl = base + "esummary.fcgi?db=gene&query_key=" + queryKey + "&WebEnv=" + webEnv;
            connection = (HttpURLConnection) (new URL(summaryUrl)).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            var summaryResult = new String(connection.getInputStream().readAllBytes());
            connection.disconnect();
            //System.out.println(summaryResult);
            int start = 0;
            int stop = 0;
            Matcher startMatcher = Pattern.compile("<ChrStart>(\\d+)</ChrStart>").matcher(summaryResult);
            if (startMatcher.find()) {
                start = Integer.parseInt(startMatcher.group(1));
                System.out.println("\tStart: " + start);
            }
            Matcher stopMatcher = Pattern.compile("<ChrStop>(\\d+)</ChrStop>").matcher(summaryResult);
            if (stopMatcher.find()) {
                stop = Integer.parseInt(stopMatcher.group(1));
                System.out.println("\tStop: " + stop);
            }
            if (start == 0 & stop == 0) return null;
            orderedGeneNames.put(start,geneName); // genes will be ordered by their start position in the genome
            updateProgress(index,model.getTreesBlock().getNTrees());
        }
        if (orderedGeneNames.size() == model.getTreesBlock().getNTrees()) return orderedGeneNames;
        return null;
    }
}