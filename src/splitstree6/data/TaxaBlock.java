/*
 * TaxaBlock.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.data;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

import java.util.*;

public class TaxaBlock extends DataBlock {
	public static final String BLOCK_NAME = "TAXA";

	private int ntax;
	private final ObservableList<Taxon> taxa;
	private final ObservableList<Taxon> unmodifiableTaxa;
	private final Map<Taxon, Integer> taxon2index;
	private final Map<String, Taxon> name2taxon;

	private final ObjectProperty<TraitsBlock> traitsBlock = new SimpleObjectProperty<>();

	/**
	 * constructor
	 */
	public TaxaBlock() {
		taxa = FXCollections.observableArrayList();
		unmodifiableTaxa = FXCollections.unmodifiableObservableList(taxa);
		taxon2index = new HashMap<>();
		name2taxon = new HashMap<>();
		unmodifiableTaxa.addListener((InvalidationListener) observable -> ntax = size());
	}

	public TaxaBlock(TaxaBlock that) {
		this();
		copy(that);
	}

	/**
	 * copy a taxon block
	 *
	 * @param src copy from here
	 */
	public void copy(TaxaBlock src) {
		clear();
		taxa.setAll(src.taxa);
		taxon2index.clear();
		taxon2index.putAll(src.taxon2index);
		name2taxon.clear();
		name2taxon.putAll(src.name2taxon);
		traitsBlock.set(src.traitsBlock.get());
	}

	public Object clone() {
		return new TaxaBlock(this);
	}

	@Override
	public void clear() {
		super.clear();
		taxa.clear();
		taxon2index.clear();
		name2taxon.clear();
	}

	/**
	 * get size
	 *
	 * @return number of taxa
	 */
	@Override
	public int size() {
		return taxa.size();
	}

	/**
	 * get number of taxa
	 *
	 * @return number of taxa
	 */
	public int getNtax() {
		return ntax;
	}

	/**
	 * Set the number of taxa. Note that any change to the list of taxa will reset this
	 *
	 * @param ntax new number
	 */
	public void setNtax(int ntax) {
		this.ntax = ntax;
	}

	public Taxon get(String taxonName) {
		return name2taxon.get(taxonName);
	}

	/**
	 * get taxon
	 *
	 * @param t range 1 to nTax
	 * @return taxon
	 */
	public Taxon get(int t) {
		if (t == 0)
			throw new IndexOutOfBoundsException("0");
		return taxa.get(t - 1);
	}

	/**
	 * get taxon label
	 *
	 * @param t range 1 to nTax
	 * @return taxon
	 */
	public String getLabel(int t) {
		if (t == 0)
			throw new IndexOutOfBoundsException("0");
		return taxa.get(t - 1).getName();
	}

	/**
	 * get index of taxon
	 *
	 * @param taxon a taxon
	 * @return number between 1 and ntax, or -1 if not found
	 */
	public int indexOf(Taxon taxon) {
		if (taxon2index.containsKey(taxon))
			return taxon2index.get(taxon) + 1;
		else
			return -1;
	}

	/**
	 * get index of taxon by label
	 *
	 * @param label the label
	 * @return number between 1 and ntax, or -1 if not found
	 */
	public int indexOf(String label) {
		final int t = getLabels().indexOf(label);
		if (t == -1)
			return -1;
		else
			return t + 1;
	}

	/**
	 * get list of all taxa
	 *
	 * @return taxa
	 */
	public ObservableList<Taxon> getTaxa() {
		return unmodifiableTaxa;
	}

	public void addTaxaByNames(Collection<String> taxonNames) {
		for (String name : taxonNames) {
			if (!name2taxon.containsKey(name)) {
				var taxon = new Taxon(name);
				name2taxon.put(name, taxon);
				taxon2index.put(taxon, taxa.size());
				taxa.add(taxon);
			}
		}
	}

    /**
     * adds a name to the taxa, making it unique, if necessary
     *
     * @param name0 original name
     */
    public void addTaxonByName(String name0) {
        int count = 1;
        String name = name0;
        while (name2taxon.containsKey(name)) {
            name = name0 + "-" + (count++);
		}
		addTaxaByNames(List.of(name));
    }

	/**
	 * computes index map for modified block
	 *
	 * @return modified map
	 */
	public Map<Integer, Integer> computeIndexMap(TaxaBlock modifiedTaxaBlockBlock) {
		final HashMap<Integer, Integer> map = new HashMap<>();
		for (int t = 1; t <= getNtax(); t++) {
			final Taxon taxon = get(t);
			if (modifiedTaxaBlockBlock.getTaxa().contains(taxon)) {
				map.put(t, modifiedTaxaBlockBlock.indexOf(taxon));
			}
		}
		return map;
	}

	/**
	 * Adds taxa. Throws an exception if name already present
	 *
	 * @throws RuntimeException taxon name already present
	 */
	public void add(Collection<Taxon> add) {
		for (var taxon : add) {
			if (taxa.contains(taxon))
				throw new RuntimeException("Duplicate taxon name: " + taxon.getName());
			name2taxon.put(taxon.getName(), taxon);
			taxon2index.put(taxon, taxa.size());
			taxa.add(taxon);
		}
	}

	/**
	 * Adds a taxon
	 *
	 * @throws RuntimeException taxon name already present
	 */
	public void add(Taxon taxon) {
		if (taxa.contains(taxon))
			throw new RuntimeException("Duplicate taxon name: " + taxon.getName());
		name2taxon.put(taxon.getName(), taxon);
		taxon2index.put(taxon, taxa.size());
		taxa.add(taxon);

	}

	/**
	 * get all taxon labels
	 *
	 * @return labels
	 */
	public ArrayList<String> getLabels() {
		return getLabels(taxa);
	}

	/**
	 * get all taxon labels in the given collection
	 *
	 * @return labels
	 */
	public static ArrayList<String> getLabels(Collection<Taxon> taxa) {
		final var labels = new ArrayList<String>();
		for (var taxon : taxa) {
			labels.add(taxon.getName());
		}
		return labels;
	}

	/**
	 * get all taxon labels in the given collection
	 *
	 * @return labels
	 */
	public ArrayList<String> getLabels(Iterable<Integer> taxa) {
		final var labels = new ArrayList<String>();
		for (var t : taxa) {
			labels.add(getLabel(t));
		}
		return labels;
	}

	/**
	 * get the current set of taxa as a bit set, 1-based
	 */
	public BitSet getTaxaSet() {
		final var taxa = new BitSet();
		taxa.set(1, getNtax() + 1);
		return taxa;
	}


	public TraitsBlock getTraitsBlock() {
		return traitsBlock.get();
	}

	public ObjectProperty<TraitsBlock> traitsBlockProperty() {
		return traitsBlock;
	}

	public void setTraitsBlock(TraitsBlock traitsBlock) {
		this.traitsBlock.set(traitsBlock);
	}


	/**
	 * returns true, if any taxon has an info string associated with it
	 *
	 * @return true, if some taxon has info
	 */
	public static boolean hasDisplayLabels(TaxaBlock taxaBlock) {
		for (var t = 1; t <= taxaBlock.getNtax(); t++)
			if (taxaBlock.get(t).getDisplayLabel() != null && !taxaBlock.get(t).getDisplayLabel().equals(taxaBlock.get(t).getName()))
				return true;
		return false;
	}

	/**
	 * returns true, if any taxon has an info string associated with it
	 *
	 * @return true, if some taxon has info
	 */
	public static boolean hasInfos(TaxaBlock taxaBlock) {
		for (var t = 1; t <= taxaBlock.getNtax(); t++)
			if (taxaBlock.get(t).getInfo() != null && taxaBlock.get(t).getInfo().length() > 0)
				return true;
		return false;
	}

	/**
	 * compare two taxon names by the order of their occurrence
	 */
	public int compare(String taxonName1, String taxonName2) {
		return Integer.compare(taxa.indexOf(get(taxonName1)), taxa.indexOf(get(taxonName2)));
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public TaxaBlock newInstance() {
		return (TaxaBlock) super.newInstance();
	}

	@Override
	public void updateShortDescription() {
		setShortDescription(String.format("%,d taxa", getNtax()));
	}


	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

	/**
	 * we apply this to the working taxa when we have read in the data from a Splitstree6 file to ensure that
	 * display labels are handled property
	 *
	 * @param inputTaxaBlock input data block
	 */
	public void overwriteTaxa(TaxaBlock inputTaxaBlock) {
		for (var i = 0; i < taxa.size(); i++) {
			var taxon = taxa.get(i);
			var originalTaxon = inputTaxaBlock.getTaxa().stream().filter(t -> t.getName().equals(taxon.getName())).findAny().orElse(null);
			if (originalTaxon != null) {
				taxa.set(i, originalTaxon);
			}
		}
	}
}
