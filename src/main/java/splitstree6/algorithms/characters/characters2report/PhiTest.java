/*
 * EstimateInvariableSites.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2report;


import jloda.util.CanceledException;
import jloda.util.NumberUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.ErrorFunction;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

/**
 * Estimates the proportion of invariant sites using capture-recapture
 * Dave Bryant, 2005
 */
public class PhiTest extends AnalyzeCharactersBase {

	final static int WINDOWSIZE = 100;

	private int num_inform;
	private int ntax;
	private int[] nstates;
	private char[][] alignment;
	private int[] sitePositions;
	private char missing;

	@Override
	public String getCitation() {
		return "Bruen, Philippe & Bryant 2005; " +
			   "Bruen TC, Philippe H, Bryant D. A simple and robust statistical test for detecting the presence of recombination. Genetics 17(4):2665-81, 2006";
	}

	@Override
	public String getShortDescription() {
		return "Performs a statistical test for detecting the presence of recombination.";
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, Collection<Taxon> selectedTaxa) throws CanceledException {

		var pval = approxPhi(charactersBlock);
		pval = NumberUtils.roundSigFig(pval, 4);
		String result;

		if (pval < 0)
			result = "There are too few informative characters to use the Phi Test as implemented here.";
		else if (pval < 0.05)
			result = "The phi test did find statistically significant evidence for recombination (p = " + pval + ")";
		else
			result = "The phi test did not find statistically significant evidence for recombination (p = " + pval + ")";

		//System.err.println("Looking for blocks");

		//findBlocks(block, 10);

		return result;
	}

	/* Node_struct for graph */

	public static class node {
		public int neighbourindex;
		public node next;
	}

	private int pair_score(int char_a, int char_b) {
		/* Keep both list & matrix - one for quick adding, other for quick DFS */
		node[] adjacency_list;
		boolean[][] adjacency_matrix;

		node cur_node, new_node;


		var char_a_states = nstates[char_a];
		var char_b_states = nstates[char_b];
		var total_states = char_a_states + char_b_states;
		int char_a_val, char_b_val, i, j, edge_count;

		/* For DFS */
		node[] DFS_adjacency;
		int[] array_stack;
		boolean[] marked;      //??
		int potential_neighbour;
		boolean has_valid_neighbour;
		int comp_count;
		int top, cur_vertex;

		/* For score */

		int inc_score;

		/* Initialize list and matrix */
		adjacency_list = new node[total_states];
		for (i = 0; i < total_states; i++)
			adjacency_list[i] = null;

		adjacency_matrix = new boolean[total_states][total_states];


		for (i = 0; i < total_states; i++) {
			for (j = 0; j < total_states; j++) {
				adjacency_matrix[i][j] = false;
			}
		}

		/* Initialize stuff for DFS... */
		DFS_adjacency = new node[total_states];
		array_stack = new int[total_states];
		marked = new boolean[total_states];

		/* Build adjacency list */
		edge_count = 0;


		for (i = 0; i < ntax; i++) {
			char_a_val = alignment[char_a][i];
			/* Number vertices [0...char_a_states-1] then [char_a_states..total_states] */
			char_b_val = alignment[char_b][i];

			/* Add the edge - if necessary */

			if ((char_a_val != missing) && (char_b_val != missing))   //Both states are valid.
			{
				/* Increase index to "global index" */
				char_b_val = char_b_val + char_a_states;


				if (!adjacency_matrix[char_a_val][char_b_val]) {

					/* Update symmetric adjacency matrix (undirected graph)*/
					adjacency_matrix[char_a_val][char_b_val] = true;
					adjacency_matrix[char_b_val][char_a_val] = true;
					edge_count++;

					/* Add to adjacency lists */
					cur_node = adjacency_list[char_a_val];
					new_node = new node();

					new_node.neighbourindex = char_b_val;
					new_node.next = cur_node;
					adjacency_list[char_a_val] = new_node;

					/* And other list */
					cur_node = adjacency_list[char_b_val];
					new_node = new node();
					new_node.neighbourindex = char_a_val;
					new_node.next = cur_node;
					adjacency_list[char_b_val] = new_node;


				}
			}
		}
		/* Now do DFS to count components */

		for (i = 0; i < total_states; i++) {
			marked[i] = false;
			DFS_adjacency[i] = adjacency_list[i];
		}

		top = -1;

		comp_count = 0;

		for (i = 0; i < total_states; i++) {
			if (!marked[i]) {

				comp_count++;
				/* "push" index onto stack */
				array_stack[++top] = i;

				while (top >= 0) {
					cur_vertex = array_stack[top];
					marked[cur_vertex] = true;
					has_valid_neighbour = false;
					while ((DFS_adjacency[cur_vertex] != null) && (!has_valid_neighbour)) {
						potential_neighbour = (DFS_adjacency[cur_vertex]).neighbourindex;
						if (!marked[potential_neighbour]) {

							array_stack[++top] = potential_neighbour;
							has_valid_neighbour = true;
						} else {
							DFS_adjacency[cur_vertex] = DFS_adjacency[cur_vertex].next;
						}
					}

					if (!has_valid_neighbour) {
						top--;
					}

				}
			}
		}

		/* For the pairwise incompatibility */

		inc_score = edge_count - total_states + comp_count;

		return inc_score;
	}


	/**
	 * Takes a characters block and returns an array for the sequences where the states in every character
	 * are numbered 0,1,2,...,k-1,  where k is the number of states appearing for this character.
	 * If removeUninformative is true then
	 * <p/>
	 * Note - in this array the rows are 0...ntax-1 and the characters are 0..nchar-1
	 * so entry i,j corresponds to taxa i+1 and character j+1.
	 * If a sequence has a missing or invalid character, the entry in the array is set to nstates,
	 * where nstates is the number of symbols in the Format block of the characters block.
	 * <p/>
	 * For each site i in the resulting alignment, sitePositions[i] is the corresponding site
	 * in the original alignment
	 */
	private void get_sorted_alignment(CharactersBlock characters, boolean removeUninformative) {

		this.ntax = characters.getNtax();
		int nchar = characters.getNchar();
		var symbols = characters.getSymbols();
		int nstates = symbols.length();
		int[] symbol_map = new int[nstates];
		sitePositions = new int[nchar];

		final int unassigned = -1;
		this.missing = (char) nstates;
		this.alignment = new char[nchar][];
		this.nstates = new int[nchar];

		int charCount = 0;
		int appearsTwice;
		boolean informative;

		for (int j = 1; j <= nchar; j++) {
			//Form a table of states in this character - array mapping states to ids.
			char[] thisSite = new char[ntax];
			for (int s = 0; s < nstates; s++) {
				symbol_map[s] = unassigned;
			}
			informative = false;
			int numassigned = 0;
			appearsTwice = -1;
			for (int i = 1; i <= ntax; i++) {
				int state = symbols.lastIndexOf(characters.get(i, j));
				int index = -1;
				if (state >= 0) {
					index = symbol_map[state];
					if (index == unassigned) {
						index = symbol_map[state] = numassigned;
						numassigned++;
					} else {
						if (appearsTwice < 0)
							appearsTwice = state;
						else if (!informative && state != appearsTwice)
							informative = true;
					}
				}
				if (index >= 0)
					thisSite[i - 1] = (char) index;
				else
					thisSite[i - 1] = missing;
			}
			//Check if informative or not
			if (informative || !removeUninformative) {
				this.nstates[charCount] = numassigned;
				this.alignment[charCount] = thisSite;
				sitePositions[charCount] = j;
				charCount++;
			}
		}
		System.err.println("Found " + charCount + " informative sites");

		this.num_inform = charCount;

	}

	private double computePval(int optk) {
		int[] fi = new int[this.num_inform];
		int[] gi = new int[this.num_inform];
		double u, v, w;

		int phi_sum = 0;

		for (int i = 0; i < num_inform; i++) {
			for (int j = i + 1; j < num_inform; j++) {
				int inc = pair_score(i, j);
				fi[i] += inc;
				fi[j] += inc;    //Note... we are only looping over the upper triangle here
				gi[i] += inc * inc;
				gi[j] += inc * inc;
				if (j - i <= optk)
					phi_sum += inc;
			}
		}

		u = v = w = 0.0;
		for (int i = 0; i < num_inform; i++) {
			u += fi[i];
			v += gi[i];
			int x = fi[i];
			w += (x * x);
		}

		double n = num_inform;
		double k = optk;

		double M = n * (n - 1) * (n - 2) * (n - 3);

		double c1top = 27.0 * k * n - 18 * k * k + 28 * k * k * n - 21 * k * n * n - 9 * k + 5 * n - 9 * k * k * k - 11 * n * n + 6 * n * (n * n + k * k * k) - 4 * k * k * n * n;

		double c1bot = (k + 1 - 2 * n) * (n - 1) * n * (k + 1 - 2 * n) * k;
		//double  c1bot = (k+1-2*n)*(n-1)*n;
		//c1bot = c1bot*c1bot;
		//c1bot = c1bot*k*(n-2)*(n-3);
		double c1 = 2.0 * c1top / (3.0 * c1bot);

		double c2top = 39.0 * k * n - 14 * k * k + 8 * k * k * n - 15 * k * n * n - 21 * k + 19 * n + 3 * k * k * k - 21 * n * n + 6 * n * n * n - 4;
		//double c2bot=(double)(k+1-2*n)*(k+1-2*n)*k*M;
		double c2bot = (k + 1 - 2 * n) * (k + 1 - 2 * n) * k;

		double c2 = 2.0 * c2top / (3.0 * c2bot);

		double c3top = -18.0 * k * n - 2 * k * k * n + 16 * k * k + 6 * n * n - 10 * n + 2 + 15 * k + 3 * k * k * k;
		//double c3bot = c2bot;
		double c3 = -4.0 * c3top / (3.0 * c2bot);

		double phi = 2.0 * (double) phi_sum / (k * (2 * n - k - 1));
		double ePhi = u / (n * (n - 1));
		double varPhi = c1 * ((u * u) / M) + c2 * (v / M) + c3 * (w / M);

		System.err.println("Mean:\t" + ePhi + "\nVariance\t" + varPhi + "\nObserved:\t" + phi);
		//We do a one-sided p-value...

		//We want the probability P(X<phi) where X is a normal with mean ePhi and variance varPhi.
		// P(X<phi) = P(X> ePhi + (ePhi - phi) )
		//           = P[Z> z] where z= (ePhi - phi)/sqrt(varPhi)
		//   if z>0 then this is 0.5 - 0.5 ErrorFunction.derf(z/Math.sqrt(2));
		//  if z<0 then this is 0.5 + 0.5 ErrorFunction.derf(-z/Math.sqrt(2))


		double z = (ePhi - phi) / Math.sqrt(varPhi);
		double pval;

		if (z > 0)
			pval = 0.5 - 0.5 * ErrorFunction.derf(z / Math.sqrt(2));
		else
			pval = 0.5 + 0.5 * ErrorFunction.derf(-z / Math.sqrt(2));   //ToDO: Check if this step is necc. what is erf(-x)?

		return pval;

	}

	/**
	 * Writes the incompatibility matrix in a form suitable for matlab to the given file
	 *
	 * @param file File for output
	 */
	private void outputIncompatibilityMatrix(File file) throws IOException {
		PrintWriter out = new PrintWriter(new FileWriter(file));
		out.println("Incompatibility matrix");
		for (int i = 0; i < num_inform; i++) {

			for (int j = 0; j <= i; j++)
				out.print(" 0");
			for (int j = i + 1; j < num_inform; j++) {
				int inc = pair_score(i, j);
				out.print(" " + inc);
			}
			out.println();
		}
		out.println();
	}

	/**
	 * Computes the incompatibility matrix
	 *
	 * @return int[][] Incompatibility matrix nsites x nsites. ij entry is the incompatibility of site i and j.
	 */
	private int[][] getIncompatibilityMatrix() {
		int[][] M = new int[num_inform][num_inform];

		for (int i = 0; i < num_inform; i++) {
			for (int j = i + 1; j < num_inform; j++) {
				int inc = pair_score(i, j);
				M[i][j] = M[j][i] = inc;
			}
		}
		return M;
	}


	double approxPhi(CharactersBlock characters) {

		//Get a sorted alignment... each row corresponds to a character, and the array contains
		//only informative characters.
		get_sorted_alignment(characters, true);

		//Compute the optK value used in the Phi test (number of off-diagonal rows)
		int num_sites = characters.getNchar();
		int optk = (int) Math.floor(((double) num_inform * WINDOWSIZE) / num_sites + 0.5);
		if (optk < 1) optk = 1;

		if (num_inform < 2 * optk) {
			return -1.0; //An 'unsuccessul'
		}

		System.err.println("Using windowsize of " + WINDOWSIZE + " with k as " + optk);

		double pval = computePval(optk);
		System.err.println("P-value:\t" + pval);
		return pval;
	}


	public void findBlocks(CharactersBlock characters, int numBreakPoints) {
		//ToDo: check to see if this is already done. Perhaps Characters should be passed
		//to the constructor.
		get_sorted_alignment(characters, true);

		double[][] f = new double[numBreakPoints + 1][num_inform];
		int[][] F = new int[numBreakPoints + 1][num_inform];

		int[] s = new int[num_inform];

		for (int i = 1; i < num_inform; i++) {

			//Update the s vector, so that s[j] = \sum_{j \leq a < b \leq i} D[a,b]
			int diff = 0;
			for (int j = i - 1; j >= 0; j--) {
				diff += pair_score(i, j);
				s[j] += diff;
			}

			f[0][i] = (double) s[0] * 2.0 / ((double) i * (i + 1));  //Score for no breakpoints.

			for (int k = 1; k <= numBreakPoints; k++) {
				double minval = f[k - 1][i - 1];
				int best = i - 1;
				for (int j = 0; j <= i - 2; j++) {
					double val = f[k - 1][j] + (double) s[j + 1] * 2.0 / ((double) (i - j) * (i - j - 1));
					if (val <= minval) {
						minval = val;
						best = j;
					}
				}
				f[k][i] = minval;
				F[k][i] = best;
			}
		}

		for (int k = 0; k <= numBreakPoints; k++) {
			System.err.print("" + k + ":\t" + f[k][num_inform - 1] + "\t breakpoints:\t");
			int b = num_inform - 1;
			for (int j = k; j > 0; j--) {
				b = F[j][b];
				System.err.print("[" + b + "] " + sitePositions[b] + ";");

			}
			System.err.println();
		}
	}
}
