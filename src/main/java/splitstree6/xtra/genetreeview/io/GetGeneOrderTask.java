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
import splitstree6.xtra.genetreeview.model.Model;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetGeneOrderTask extends Task<TreeMap<Double, Integer>> {

	private final Model model;
	private final String taxonName;

	public GetGeneOrderTask(Model model, String taxonName) {
		this.model = model;
		this.taxonName = taxonName;
	}

	@Override
	protected TreeMap<Double, Integer> call() throws Exception {
		// Getting the gene order from NCBI's gene database using a simple E-utility pipeline: ESearch-ESummary
		TreeMap<Double, Integer> orderedTreeIds = new TreeMap<>();
		System.out.println(taxonName);
		var base = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/";
		for (var index = 0; index < model.getGeneTreeSet().size(); index++) {

			// ESearch
			String geneName = model.getGeneTreeSet().getOrderedGeneNames().get(index);
			System.out.println(geneName);
			var query = taxonName + "[organism]+AND+" + geneName + "[gene]";
			var searchUrl = base + "esearch.fcgi?db=gene&term=" + query + "&usehistory=y";
			var connection = (HttpURLConnection) (new URL(searchUrl)).openConnection();
			connection.setRequestMethod("GET");
			connection.connect();
			var searchResult = new String(connection.getInputStream().readAllBytes());
			connection.disconnect();
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
			if (queryKey == null | webEnv == null) throw new RuntimeException("Failed to retrieve " + geneName +
																			  " position for taxon " + taxonName + " from ncbi");

			// ESummary
			var summaryUrl = base + "esummary.fcgi?db=gene&query_key=" + queryKey + "&WebEnv=" + webEnv;
			connection = (HttpURLConnection) (new URL(summaryUrl)).openConnection();
			connection.setRequestMethod("GET");
			connection.connect();
			var summaryResult = new String(connection.getInputStream().readAllBytes());
			connection.disconnect();
			double start = 0;
			double stop = 0;
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
			if (start == 0 & stop == 0) throw new RuntimeException("Failed to retrieve " + geneName +
																   " position for taxon " + taxonName + " from ncbi");
			orderedTreeIds.put(start, model.getGeneTreeSet().getTreeId(geneName)); // genes will be ordered by their start position in the genome
			updateProgress(index, model.getGeneTreeSet().size());
		}
		if (orderedTreeIds.size() == model.getGeneTreeSet().size()) return orderedTreeIds;
		throw new RuntimeException("Failed to retrieve all gene positions for " + taxonName);
	}
}
