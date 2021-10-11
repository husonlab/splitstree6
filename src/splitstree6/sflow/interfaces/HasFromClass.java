package splitstree6.sflow.interfaces;

import splitstree6.sflow.DataBlock;

public interface HasFromClass<T extends DataBlock> {
	Class<T> getFromClass();
}
