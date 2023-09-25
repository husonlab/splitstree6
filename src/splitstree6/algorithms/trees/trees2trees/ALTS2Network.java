/*
 *  ALTSNetwork.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.phylo.LSAUtils;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * this runs the ALTSNetwork algorithm externally
 * Daniel Huson, 7.2023
 */
public class ALTS2Network extends Trees2Trees {

	@Override
	public String getCitation() {
		return "Zhang et al 2023; Louxin Zhang, Niloufar Niloufar Abhari, Caroline Colijn and Yufeng Wu3." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023";
	}

	@Override
	public List<String> listOptions() {
		return List.of();
	}

	@Override
	public String getToolTip(String optionName) {
		return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		outputBlock.setRooted(true);
		// just copying trees to output
		for (var t = 1; t <= treesBlock.getNTrees(); t++) {
			var treeCopy = new PhyloTree(treesBlock.getTree(t));
			outputBlock.getTrees().add(treeCopy);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isReticulated() && datablock.isRooted();
	}
}
