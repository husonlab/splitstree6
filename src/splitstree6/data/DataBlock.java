package splitstree6.data;

import jloda.util.Basic;
import splitstree6.workflow.NamedBase;
import splitstree6.workflow.TopFilter;

public abstract class DataBlock extends NamedBase {
	public void clear() {
	}

	public abstract int size();

	public abstract String getInfo();

	public abstract String getDisplayText();

	public abstract TopFilter<? extends DataBlock, ? extends DataBlock> createTopFilter();

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
