module splitstreesix {
	requires transitive jloda;
	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires transitive javafx.fxml;
	requires transitive javafx.web;
	requires transitive java.sql;
	requires transitive java.desktop;

	requires Jama;
	requires richtextfx.fat;

	opens splitstree6.resources.css;
	opens splitstree6.resources.icons;
	opens splitstree6.resources.images;

	opens splitstree6.algorithms.characters.characters2characters;
	opens splitstree6.algorithms.characters.characters2distances;
	opens splitstree6.algorithms.characters.characters2network;
	opens splitstree6.algorithms.characters.characters2sink;
	opens splitstree6.algorithms.characters.characters2splits;
	opens splitstree6.algorithms.characters.characters2trees;
	opens splitstree6.algorithms.distances.distances2distances;
	opens splitstree6.algorithms.distances.distances2sink;
	opens splitstree6.algorithms.distances.distances2splits;
	opens splitstree6.algorithms.distances.distances2trees;
	opens splitstree6.algorithms.networks.network2network;
	opens splitstree6.algorithms.networks.network2sink;
	opens splitstree6.algorithms.source.source2characters;
	opens splitstree6.algorithms.source.source2distances;
	opens splitstree6.algorithms.source.source2splits;
	opens splitstree6.algorithms.source.source2trees;
	opens splitstree6.algorithms.splits.splits2distances;
	opens splitstree6.algorithms.splits.splits2sink;
	opens splitstree6.algorithms.splits.splits2splits;
	opens splitstree6.algorithms.splits.splits2trees;
	opens splitstree6.algorithms.taxa;
	opens splitstree6.algorithms.taxa.taxa2taxa;
	opens splitstree6.algorithms.trees.trees2distances;
	opens splitstree6.algorithms.trees.trees2sink;
	opens splitstree6.algorithms.trees.trees2splits;
	opens splitstree6.algorithms.trees.trees2trees;

	opens splitstree6.io.readers.characters;
	opens splitstree6.io.readers.distances;
	opens splitstree6.io.readers.splits;
	opens splitstree6.io.readers.trees;
	opens splitstree6.io.readers.network;

	opens splitstree6.io.writers.characters;
	opens splitstree6.io.writers.distances;
	opens splitstree6.io.writers.splits;
	opens splitstree6.io.writers.trees;
	opens splitstree6.io.writers.network;

	opens splitstree6.window;

	opens splitstree6.treeview;

	opens splitstree6.tabs.inputeditor;
	opens splitstree6.tabs.textdisplay;
	opens splitstree6.tabs.network;
	opens splitstree6.tabs.tree;
	opens splitstree6.tabs.workflow;
	opens splitstree6.tabs.workflow.algorithm;
	opens splitstree6.tabs.workflow.data;
	opens splitstree6.dialog.attachnode;

	opens splitstree6.contextmenus.algorithmnode;
	opens splitstree6.contextmenus.datanode;

	exports splitstree6.xtra;
	exports splitstree6.main;
	opens splitstree6.tabs.algorithms;
	opens splitstree6.tabs.algorithms.taxa;

	opens splitstree6.densitree;
}