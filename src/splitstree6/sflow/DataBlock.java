package splitstree6.sflow;

import jloda.util.Basic;

public abstract class DataBlock extends splitstree6.wflow.DataBlock {

	public void clear() {
	}

	public abstract int size();

	public abstract String getInfo();

	public abstract TopFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter();

	/**
	 * creates a new instance
	 *
	 * @return new instance
	 */
	public DataBlock newInstance() {
		try {
			return getClass().getConstructor().newInstance();
		} catch (Exception e) {
			Basic.caught(e);
			return null;
		}
	}
}
