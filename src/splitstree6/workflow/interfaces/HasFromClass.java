package splitstree6.workflow.interfaces;

import splitstree6.data.DataBlock;

public interface HasFromClass<T extends DataBlock> {
	Class<T> getFromClass();
}
