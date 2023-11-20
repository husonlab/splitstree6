/*
 *  AccessReferenceDatabase.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.dialog.analyzegenomes;

import javafx.collections.ObservableList;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.graph.NodeIntArray;
import jloda.kmers.bloomfilter.BloomFilter;
import jloda.kmers.mash.MashDistance;
import jloda.kmers.mash.MashSketch;
import jloda.phylo.PhyloTree;
import jloda.thirdparty.HexUtils;
import jloda.util.*;
import jloda.util.progress.ProgressListener;

import java.io.*;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * provides next to a reference database
 * Daniel Huson, 8.2020
 */
public class AccessReferenceDatabase implements Closeable {
    private final AccessReferenceDatabase[] copiesForSearching;
    private final AtomicInteger which = new AtomicInteger(0);

    private Supplier<File> fileCacheDirectory;

    private static final boolean verbose = false;

    private final Set<Integer> unusableTaxa = new TreeSet<>();

    private final Connection connection;
    private final File dbFile;

    private final int mashK;
    private final int mashS;
    private final int mashSeed;
    private final int taxonomyRoot;

    /**
     * open database
     */
    public AccessReferenceDatabase(String dbFile, Supplier<File> fileCacheDirectory, int copies) throws IOException, SQLException {
        this(dbFile, fileCacheDirectory, copies, true);
    }

    /**
     * open the database
     * <p>
     * Schema:
     * CREATE TABLE mash_sketches (taxon_id INTEGER PRIMARY KEY, mash_sketch TEXT NOT NULL);
     * CREATE TABLE bloom_filters (taxon_id INTEGER PRIMARY KEY, bloom_filter TEXT NOT NULL);
     * CREATE TABLE taxa (taxon_id INTEGER PRIMARY KEY, taxon_name TEXT, taxon_display_name TEXT, parent_id INTEGER REFERENCES taxa(taxon_id));
     * CREATE TABLE info (key TEXT PRIMARY KEY, value TEXT NOT NULL);
     * CREATE TABLE genomes (taxon_id INTEGER PRIMARY KEY, genome_accession TEXT NOT NULL, genome_size INTEGER, fasta_url TEXT);
     *
     * @param dbFile   file
     * @param copies   number of copies to use in parallel processing
     * @param readOnly open read only
     */
    public AccessReferenceDatabase(String dbFile, Supplier<File> fileCacheDirectory, int copies, boolean readOnly) throws IOException, SQLException {
        this.dbFile = new File(dbFile);
        this.fileCacheDirectory = fileCacheDirectory;

        copiesForSearching = new AccessReferenceDatabase[copies];
        for (int i = 0; i < copiesForSearching.length; i++)
            copiesForSearching[i] = new AccessReferenceDatabase(dbFile, fileCacheDirectory, 0);

        if (!FileUtils.fileExistsAndIsNonEmpty(dbFile))
            throw new IOException("File not found or unreadable: " + dbFile);

        if (!FileUtils.fileExistsAndIsNonEmpty(dbFile))
            throw new IOException("File not found or empty: " + dbFile);

		connection = DatabaseConnector.createSQLiteConnection(dbFile, readOnly);

        unusableTaxa.addAll(executeQueryInt("SELECT taxon_id FROM genomes WHERE fasta_url is NULL or fasta_url='';", 1));

        mashK = executeQueryInt("SELECT value FROM info WHERE key='mash_k';", 1).get(0);
        mashS = executeQueryInt("SELECT value FROM info WHERE key='mash_s';", 1).get(0);
        mashSeed = executeQueryInt("SELECT value FROM info WHERE key='mash_seed';", 1).get(0);
        taxonomyRoot = executeQueryInt("SELECT taxon_id FROM taxa WHERE parent_id=0;", 1).get(0);
    }

    public static String getSchema() {
        return "CREATE TABLE bloom_filters (taxon_id INTEGER PRIMARY KEY, bloom_filter TEXT NOT NULL) WITHOUT ROWID;\n" +
               "CREATE TABLE tree (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;\n" +
               "CREATE TABLE taxa (taxon_id INTEGER PRIMARY KEY, taxon_name TEXT, taxon_display_name TEXT, parent_id INTEGER REFERENCES taxa(taxon_id)) WITHOUT ROWID;\n" +
               "CREATE TABLE info (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;\n" +
               "CREATE TABLE genomes (taxon_id INTEGER PRIMARY KEY, genome_accession TEXT NOT NULL, genome_size INTEGER, fasta_url TEXT) WITHOUT ROWID;\n" +
               "CREATE TABLE mash_sketches (taxon_id INTEGER PRIMARY KEY, mash_sketch TEXT NOT NULL) WITHOUT ROWID;\n";
    }

    public static boolean isDatabaseFile(String fileName) {
        return FileUtils.fileExistsAndIsNonEmpty(fileName) && (fileName.endsWith(".db") || fileName.endsWith(".st5db"));
    }

    /**
     * generic method for executing queries with results of type int/Integer
     *
     * @param query the SQL query
     * @return ArrayList containing all query results of the specified type
     * @throws SQLException if something went wrong with the database
     */
    public ArrayList<Integer> executeQueryInt(String query, int index) throws SQLException {
        final var rs = connection.createStatement().executeQuery(query);
        final var list = new ArrayList<Integer>();
        while (rs.next()) {
            list.add(rs.getInt(index));
        }
        return list;
    }

    /**
     * generic method for executing queries with results of type String
     *
     * @param query the SQL query
     * @param index the index of the result of interest
     * @return ArrayList containing all query results of the specified type
     * @throws SQLException if something went wrong with the database
     */
    public ArrayList<String> executeQueryString(String query, int index) throws SQLException {
        final var rs = connection.createStatement().executeQuery(query);
        final var result = new ArrayList<String>();
        while (rs.next()) {
            result.add(rs.getString(index));
        }
        return result;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                Basic.caught(e);
            }
        }
        for (AccessReferenceDatabase copy : copiesForSearching) {
            copy.close();
        }
    }

    public ArrayList<Pair<Integer, MashSketch>> getMashSketches(Collection<Integer> taxonIds) throws SQLException, IOException {
        final var query = String.format("select taxon_id,mash_sketch from mash_sketches where taxon_id in('%s');", StringUtils.toString(taxonIds, "','"));

        final var rs = connection.createStatement().executeQuery(query);
        final var columnCount = rs.getMetaData().getColumnCount();

        final var result = new ArrayList<Pair<Integer, MashSketch>>();
        if (columnCount >= 2) {
            while (rs.next()) {
                final var taxon = rs.getInt(1);
                final var hexString = rs.getString(2);
                final var mashSketch = MashSketch.parse(HexUtils.decodeHexString(hexString));
                result.add(new Pair<>(taxon, mashSketch));
            }
        }
        return result;
    }

    public ArrayList<Pair<Integer, BloomFilter>> getBloomFilters(Collection<Integer> taxonIds) throws SQLException, IOException {
        final var query = String.format("SELECT taxon_id,bloom_filter FROM bloom_filters WHERE taxon_id IN('%s');", StringUtils.toString(taxonIds, "','"));

        final var rs = connection.createStatement().executeQuery(query);

        final var result = new ArrayList<Pair<Integer, BloomFilter>>();
        while (rs.next()) {
            final var taxon = rs.getInt(1);
            final var hexString = rs.getString(2);
            final var bloomFilter = BloomFilter.parseBytes(HexUtils.decodeHexString(hexString));
            result.add(new Pair<>(taxon, bloomFilter));
        }
        return result;
    }

    public Map<Integer, String> getNames(Collection<Integer> taxonIds) throws SQLException {
        final String query = String.format("SELECT taxon_id,taxon_name FROM taxa WHERE taxon_id IN('%s');", StringUtils.toString(taxonIds, "','"));

        final var rs = connection.createStatement().executeQuery(query);

        final var result = new TreeMap<Integer, String>();
        while (rs.next()) {
            result.put(rs.getInt(1), rs.getString(2));
        }
        return result;
    }

    public String getName(int taxonId) throws SQLException {
        final var query = String.format("SELECT taxon_id,taxon_name FROM taxa WHERE taxon_id=%d;", taxonId);
        final var rs = connection.createStatement().executeQuery(query);

        return rs.getString(2);
    }

    public Map<Integer, List<Integer>> getAncestors(Collection<Integer> taxonIds) throws SQLException {
        final var queryFormat = """
                WITH RECURSIVE
                parent_of(taxon_id, parent) AS
                (SELECT taxon_id, parent_id FROM taxa),
                ancestor(taxon_id) AS
                (SELECT parent FROM parent_of WHERE taxon_id=%d
                UNION ALL
                SELECT parent FROM parent_of JOIN ancestor USING(taxon_id))
                SELECT taxa.taxon_id FROM ancestor, taxa
                WHERE ancestor.taxon_id=taxa.taxon_id;
                """;

        final var map = new HashMap<Integer, List<Integer>>();
        for (var taxonId : taxonIds) {
            final var rs = connection.createStatement().executeQuery(String.format(queryFormat, taxonId));
            final var ancestors = new ArrayList<Integer>();
            while (rs.next()) {
                ancestors.add(0, rs.getInt(1));
            }
            map.put(taxonId, ancestors);
        }
        return map;
    }

    public ArrayList<Integer> getAllTaxonIds() throws SQLException {
        final var query = "SELECT taxon_id FROM taxa;";

        final var rs = connection.createStatement().executeQuery(query);

        final var result = new ArrayList<Integer>();
        while (rs.next()) {
            result.add(rs.getInt(1));
        }
        return result;
    }

    public PhyloTree getTaxonomy() throws SQLException {
        final var query = "SELECT taxon_id, parent_id, taxon_name FROM taxa;";

        final var rs = connection.createStatement().executeQuery(query);

        var tree = new PhyloTree();
        var node2parentId = new NodeIntArray(tree);
        var id2node = new HashMap<Integer, Node>();
        Node root = null;
        while (rs.next()) {
            var taxId = rs.getInt(1);
            var parentId = rs.getInt(2);
            var taxonName = rs.getString(3);
            var v = tree.newNode(taxId);
            if (parentId == 0)
                root = v;
            else
                node2parentId.put(v, parentId);
            id2node.put(taxId, v);
            tree.setLabel(v, taxonName);
        }
        for (var v : tree.nodes()) {
            int parentId = node2parentId.getInt(v);
            if (parentId != 0) {
                tree.newEdge(id2node.get(parentId), v);
            }
        }
        return tree;
    }

    public Map<Integer, String> getFiles(Collection<Integer> taxonIds) throws SQLException, IOException {
        final String query = String.format("SELECT taxon_id,fasta_url FROM genomes WHERE taxon_id IN('%s');", StringUtils.toString(taxonIds, "','"));

        final var rs = connection.createStatement().executeQuery(query);

        final var result = new HashMap<Integer, String>();
        while (rs.next()) {
            final var taxon = rs.getInt(1);
            if (rs.getString(2).equals("null")) {
                System.err.println("The genome URL for " + this.getName(taxon) + " is null");
                continue;
            }
            System.out.println(taxon + "\t" + rs.getString(2));
            final var fileURL = UrlUtilities.getFileForFtpUrl(rs.getString(2), ".*_cds_.*", ".*fna.gz$");
            result.put(taxon, fileURL);
        }
        return result;
    }

    public String getFile(Integer taxonId) throws SQLException, IOException {
        final var query = String.format("SELECT taxon_id,fasta_url FROM genomes WHERE taxon_id=%d;", taxonId);

        final var rs = connection.createStatement().executeQuery(query);

        //final Map<Integer, String> result = new HashMap<>();
        //final int taxon = rs.getInt(1);
        return UrlUtilities.getFileForFtpUrl(rs.getString(2), ".*_cds_.*", ".*fna.gz$");
    }

    public int countGenomes() throws SQLException {
        return executeQueryInt("SELECT count(*) FROM genomes;", 1).get(0);
    }

    public int countBloomFilters() throws SQLException {
        return executeQueryInt("SELECT count(*) FROM bloom_filters;", 1).get(0);
    }

    public int countMashSketches() throws SQLException {
        return executeQueryInt("SELECT count(*) FROM mash_sketches;", 1).get(0);
    }

    public int getMashK() {
        return mashK;
    }

    public int getMashS() {
        return mashS;
    }

    public int getMashSeed() {
        return mashSeed;
    }

    public int getTaxonomyRoot() {
        return taxonomyRoot;
    }

    public Collection<Integer> getTaxonomyChildren(int parent_id) throws SQLException {
        return new ArrayList<>(executeQueryInt("SELECT taxon_id FROM taxa WHERE parent_id=" + parent_id + ";", 1));
    }

    public int getTaxonomyParent(int taxid) throws SQLException {
        return new ArrayList<>(executeQueryInt("SELECT parent_id FROM taxa WHERE taxon_id=" + taxid + ";", 1)).get(0);
    }

    public Map<String, String> getReferenceFile2Name(ObservableList<Integer> taxonIds, ProgressListener progress) throws SQLException, IOException {
        progress.setSubtask("Downloading/caching reference files");
        progress.setMaximum(taxonIds.size());
        progress.setProgress(0);

        final var id2name = getNames(taxonIds);
        final var id2file = getFiles(taxonIds);

        if (fileCacheDirectory.get() == null) {
            NotificationManager.showError("File cache directory not set");
        }
        final var result = new HashMap<String, String>();

        for (var taxonId : taxonIds) {
            if (!id2file.containsKey(taxonId)) {
                continue;
            }
            final var fName = (File.separatorChar != '/' ? id2file.get(taxonId).replace("/", "\\") : id2file.get(taxonId));
            final var cacheFile = new File(fileCacheDirectory.get(), FileUtils.getFileNameWithoutPath(fName));

            if (!FileUtils.fileExistsAndIsNonEmpty(cacheFile)) {
                System.err.println("Caching file: " + id2file.get(taxonId));

                final var tmpFile = File.createTempFile("download", ".tmp", cacheFile.getParentFile());

                if (tmpFile.exists() && !tmpFile.delete())
                    NotificationManager.showWarning("Failed to delete existing tmp file: " + tmpFile);

                var ok = false;
                try (var ins = (new URL(id2file.get(taxonId)).openStream()); OutputStream outs = new FileOutputStream(tmpFile)) {
                    ins.transferTo(outs);
                    ok = true;
                } catch (IOException ex) {
                    NotificationManager.showError("Failed to cache file: " + id2file.get(taxonId));
                    ok = false;
                } finally {
                    if (ok && !tmpFile.renameTo(cacheFile)) {
                        NotificationManager.showError("Failed to create file: " + cacheFile);
                        ok = false;
                    }
                    if (!ok && tmpFile.exists() && !tmpFile.delete())
                        NotificationManager.showError("Failed to delete file: " + tmpFile);
                }
            }
            result.put(cacheFile.getPath(), "<c GRAY>" + id2name.get(taxonId) + "</c>");
            progress.incrementProgress();
        }
        return result;
    }

    /**
     * find all genomes that have non-zero Jaccard index when compared with the query
     */
    public Collection<Map.Entry<Integer, Double>> findSimilar(ProgressListener progress, double maxDistance, boolean includeStrains, Collection<byte[]> query, boolean ignoreUnusableTaxa) throws SQLException, IOException {
        final var mash_k = getMashK();
        final var mash_s = getMashS();
        final var mash_seed = getMashSeed();

        if (verbose)
            System.err.println("Using mash_k=" + mash_k + ", mash_s=" + mash_s + ", mash_seed=" + mash_seed);

        progress.setTasks("Find similar", "Sketching");
        progress.setMaximum(query.size());
        progress.setProgress(0);
        final var querySketches = new ArrayList<MashSketch>();
        try {
            ExecuteInParallel.apply(query,
                    q -> Collections.singleton(MashSketch.compute("", Collections.singletonList(q), true, mash_s, mash_k, mash_seed, false, true, progress)),
                    querySketches, ProgramExecutorService.getNumberOfCoresToUse());
        } catch (Exception e) {
            throw new IOException(e);
        }

        final var kmers = new HashSet<String>();
        for (var sketch : querySketches) {
            for (var kmer : sketch.getKmers()) {
                kmers.add(StringUtils.toString(kmer));
            }
        }

        final var minSharedKMers = MashDistance.computeMinIntersectionSizeForMaxDistance(maxDistance, getMashK(), getMashS());
        System.err.printf("Max distance = %.3f  => min number of shared k-mers = %d%n", maxDistance, minSharedKMers);

        progress.setSubtask("Searching");
        progress.setMaximum(countBloomFilters());
        progress.setProgress(0);

        final var id2distance = new ConcurrentHashMap<Integer, Double>();

        final var exception = new Single<Exception>();
        final var jobs = new AtomicInteger(1);

        final var service = Executors.newFixedThreadPool(ProgramExecutorService.getNumberOfCoresToUse());
        try {
            service.submit(createTasksRec(getTaxonomyRoot(), querySketches, kmers, minSharedKMers, id2distance, progress, exception, jobs, service));

            if (exception.get() != null)
                throw new IOException(exception.get());
            try {
                //noinspection ResultOfMethodCallIgnored
                service.awaitTermination(1000, TimeUnit.DAYS);
            } catch (InterruptedException ignored) {
            }
        } finally {
            service.shutdown();
        }

        final ArrayList<Map.Entry<Integer, Double>> result;
        if (includeStrains) {
            var id2DistanceStrain = new HashMap<Integer, Double>();
            for (var taxId : id2distance.keySet()) {
                var parent = this.getTaxonomyParent(taxId);
                var taxonomyChildren = this.getTaxonomyChildren(parent);
                for (var child : taxonomyChildren) {
                    if (!id2DistanceStrain.containsKey(child)) {
                        id2DistanceStrain.put(child, id2distance.get(taxId));
                    }
                }
            }
            result = new ArrayList<>(id2DistanceStrain.entrySet());
        } else {
            result = new ArrayList<>(id2distance.entrySet());
        }

        result.sort(Comparator.comparingDouble(Map.Entry::getValue));
        if (ignoreUnusableTaxa && getUnusableTaxa().size() > 0) {
            return result.stream().filter(entry -> !getUnusableTaxa().contains(entry.getKey())).collect(Collectors.toList());
        }
        return result;
    }

    /**
     * creates a task to submitted to the service. This task will recursively submit further tasks and will call shutdown() once all tasks have been completed
     */
    private Runnable createTasksRec(int taxonId, Collection<MashSketch> querySketches, Set<String> kmers, int minSharedKMers, ConcurrentHashMap<Integer, Double> id2distance,
                                    ProgressListener progress, Single<Exception> exception, AtomicInteger jobCount, ExecutorService service) {
        return () -> {
            if (exception.get() == null) {
                try {
                    final var database = getCopy();
                    final var ids = database.getTaxonomyChildren(taxonId);
                    final var bloomFilters = database.getBloomFilters(ids);
                    for (var pair : bloomFilters) {
                        final var bloomFilter = pair.getSecond();
                        if (bloomFilter == null || bloomFilter.cardinality() < database.getMashS() || bloomFilter.countContainedProbably(kmers) >= minSharedKMers) {
                            final var id = pair.getFirst();
                            jobCount.incrementAndGet();
                            service.submit(createTasksRec(id, querySketches, kmers, minSharedKMers, id2distance, progress, exception, jobCount, service));
                            //System.err.println("Adding bloom filter for " + id);
                        }
                        progress.incrementProgress();
                    }
                    final var mashSketches = database.getMashSketches(ids);
                    for (var pair : mashSketches) {
                        final var mashSketch = pair.getSecond();
                        for (var sketch : querySketches) {
                            if (MashDistance.computeIntersection(mashSketch, sketch) >= minSharedKMers) {
                                final var id = pair.getFirst();
                                final var distance = MashDistance.compute(mashSketch, sketch);
                                synchronized (id2distance) {
                                    if (!id2distance.containsKey(id) || id2distance.get(id) > distance) {
                                        id2distance.put(id, distance);
                                    }
                                }
                                if (verbose)
                                    System.err.printf("Found similar: " + id + " " + database.getName(id) + " JI: %f dist: %.8f%n", MashDistance.computeJaccardIndex(mashSketch, sketch), distance);
                            }
                        }
                        progress.incrementProgress();
                    }
                } catch (IOException | SQLException ex) {
                    exception.setIfCurrentValueIsNull(ex);
                }
            }
            if (jobCount.decrementAndGet() <= 0)
                service.shutdown();
        };
    }

    public Set<Integer> getUnusableTaxa() {
        return unusableTaxa;
    }

    public File getDbFile() {
        return dbFile;
    }

    private AccessReferenceDatabase getCopy() {
        return copiesForSearching[which.incrementAndGet() % copiesForSearching.length];
    }

    public Connection getConnection() {
        return connection;
    }
}

