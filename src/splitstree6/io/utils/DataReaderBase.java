package splitstree6.io.utils;

import jloda.util.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.sflow.DataBlock;

import java.io.IOException;

public abstract class DataReaderBase<T extends DataBlock> extends ReaderWriterBase {
	private final Class<T> toClass;

	public DataReaderBase(Class<T> toClass) {
		this.toClass = toClass;
	}

	public abstract void read(ProgressListener progress, String fileName, TaxaBlock taxaBlock, T dataBlock) throws IOException;

	public Class<T> getToClass() {
		return toClass;
	}
}
