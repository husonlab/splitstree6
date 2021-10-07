package splitstree6.workflow.interfaces;

import splitstree6.data.DataBlock;

public interface HasToClass<T extends DataBlock> {
	Class<T> getToClass();
}
