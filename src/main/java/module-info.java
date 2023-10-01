module splitstreesix {
	requires transitive jloda2;
	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires transitive javafx.fxml;
	requires transitive javafx.swing;
	requires javafx.base;

	requires org.apache.commons.collections4;
	requires org.apache.commons.math4.legacy;
	requires org.apache.commons.math4.legacy.exception;

	requires com.install4j.runtime;
	requires java.sql;
	requires java.sql.rowset;
	requires org.xerial.sqlitejdbc;
	requires java.desktop;
	requires org.fxmisc.flowless;
	requires org.fxmisc.richtext;
	requires org.fxmisc.undo;

	opens splitstree6.resources.css;
	opens splitstree6.resources.icons;
	opens splitstree6.resources.images;

	opens splitstree6.algorithms.characters.characters2characters;
	opens splitstree6.algorithms.characters.characters2distances;
	opens splitstree6.algorithms.characters.characters2distances.nucleotide;
	opens splitstree6.algorithms.characters.characters2network;
	opens splitstree6.algorithms.characters.characters2view;
	opens splitstree6.algorithms.characters.characters2splits;
	opens splitstree6.algorithms.characters.characters2trees;
	opens splitstree6.algorithms.characters.characters2report;

	opens splitstree6.algorithms.genomes.genomes2genomes;
	opens splitstree6.algorithms.genomes.genome2distances;

	opens splitstree6.algorithms.distances.distances2distances;
	opens splitstree6.algorithms.distances.distances2network;
	opens splitstree6.algorithms.distances.distances2view;
	opens splitstree6.algorithms.distances.distances2splits;
	opens splitstree6.algorithms.distances.distances2trees;
	opens splitstree6.algorithms.distances.distances2report;

	opens splitstree6.algorithms.network.network2network;
	opens splitstree6.algorithms.network.network2view;

	opens splitstree6.algorithms.source.source2characters;
	opens splitstree6.algorithms.source.source2distances;
	opens splitstree6.algorithms.source.source2splits;
	opens splitstree6.algorithms.source.source2trees;

	opens splitstree6.algorithms.splits.splits2distances;
	opens splitstree6.algorithms.splits.splits2view;
	opens splitstree6.algorithms.splits.splits2splits;
	opens splitstree6.algorithms.splits.splits2trees;
	opens splitstree6.algorithms.splits.splits2network;
	opens splitstree6.algorithms.splits.splits2report;

	opens splitstree6.algorithms.taxa.taxa2taxa;
	opens splitstree6.algorithms.taxa.taxa2view;

	opens splitstree6.algorithms.trees.trees2distances;
	opens splitstree6.algorithms.trees.trees2network;
	opens splitstree6.algorithms.trees.trees2view;
	opens splitstree6.algorithms.trees.trees2splits;
	opens splitstree6.algorithms.trees.trees2trees;
	opens splitstree6.algorithms.trees.trees2report;

	opens splitstree6.io.readers.characters;
	opens splitstree6.io.readers.genomes;
	opens splitstree6.io.readers.distances;
	opens splitstree6.io.readers.splits;
	// opens splitstree6.io.readers.taxa;
	opens splitstree6.io.readers.report;
	opens splitstree6.io.readers.trees;
	opens splitstree6.io.readers.network;
	opens splitstree6.io.readers.view;

	opens splitstree6.io.writers.characters;
	opens splitstree6.io.writers.genomes;
	opens splitstree6.io.writers.distances;
	opens splitstree6.io.writers.splits;
	opens splitstree6.io.writers.trees;
	opens splitstree6.io.writers.taxa;
	opens splitstree6.io.writers.report;
	opens splitstree6.io.writers.network;
	opens splitstree6.io.writers.view;

	opens splitstree6.window;

	opens splitstree6.dialog.exporting.data;
	opens splitstree6.dialog.analyzegenomes;

	opens splitstree6.workflowtree;

	opens splitstree6.tabs.workflow;
	opens splitstree6.tabs.workflow.algorithm;
	opens splitstree6.tabs.workflow.data;
	opens splitstree6.tabs.algorithms.treefilter;

	opens splitstree6.contextmenus.algorithmnode;
	opens splitstree6.contextmenus.datanode;

	exports splitstree6.xtra;
	exports splitstree6.main;
	opens splitstree6.tools;

	opens splitstree6.tabs.algorithms;
	opens splitstree6.tabs.algorithms.taxafilter;

	opens splitstree6.layout.splits;
	opens splitstree6.layout.tree;

	opens splitstree6.view.splits.viewer;

	opens splitstree6.view.trees.treepages;
	opens splitstree6.view.trees.tanglegram;
	opens splitstree6.view.trees.treeview;
	opens splitstree6.view.trees.densitree;

	opens splitstree6.view.network;

	opens splitstree6.view.displaytext;
	opens splitstree6.view.displaydatablock;
	opens splitstree6.view.inputeditor;

	opens splitstree6.view.format.taxmark;
	opens splitstree6.view.format.densitree;
	opens splitstree6.view.format.taxlabel;
	opens splitstree6.view.format.splits;
	opens splitstree6.view.format.traits;
	opens splitstree6.view.format.selecttraits;
	opens splitstree6.view.format.sites;
	opens splitstree6.view.format.edges;

	//opens splitstree6.view.utils;
	opens splitstree6.view.alignment;
	opens splitstree6.view.trees;
	opens splitstree6.layout.network;
	opens splitstree6.dialog.importdialog;

	opens splitstree6.dialog.exporting;
	opens splitstree6.layout;
	opens splitstree6.algorithms.distances.distances2splits.neighbornet;
	exports splitstree6.io.utils;
	exports splitstree6.data.parts;

	opens splitstree6.xtra.genetreeview;
	opens splitstree6.xtra.outliner;

	opens splitstree6.algorithms.utils;
	opens splitstree6.xtra.genetreeview.layout;
	opens splitstree6.xtra.genetreeview.io;
	opens splitstree6.xtra.genetreeview.model;
	opens splitstree6.xtra.genetreeview.util;
	exports splitstree6.xtra.more;
	exports splitstree6.splits;
	opens splitstree6.splits;
	opens splitstree6.xtra.more;
}