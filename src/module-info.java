module splitstreesix {
	requires transitive jloda;
	requires transitive javafx.controls;
	requires transitive javafx.graphics;
	requires transitive javafx.fxml;
	requires transitive javafx.web;
	requires transitive java.sql;
	requires transitive java.desktop;

	requires Jama;

    /*
    exports splitstree6.resources.css;
    exports splitstree6.resources.icons;
    exports splitstree6.resources.images;


    opens splitstree6.resources.css;
    opens splitstree6.resources.icons;
    opens splitstree6.resources.images;
    */

	exports splitstree6.xtra;
}