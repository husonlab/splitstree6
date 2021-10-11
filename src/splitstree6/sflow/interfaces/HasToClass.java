package splitstree6.sflow.interfaces;

import splitstree6.sflow.DataBlock;

public interface HasToClass<T extends DataBlock> {
	Class<T> getToClass();
}
