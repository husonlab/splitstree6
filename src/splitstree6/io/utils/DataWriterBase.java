package splitstree6.io.utils;

import jloda.util.Basic;
import splitstree6.data.DataBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.interfaces.HasFromClass;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public abstract class DataWriterBase<T extends DataBlock> extends ReaderWriterBase implements HasFromClass<T> {
	private final Class<T> fromClass;

	public DataWriterBase(Class<T> fromClass) {
		this.fromClass = fromClass;
	}

	public abstract void write(Writer w, TaxaBlock taxaBlock, T dataBlock) throws IOException;

	public void write(String fileName, TaxaBlock taxaBlock, T dataBlock) throws IOException {
		try (var w = new OutputStreamWriter(Basic.getOutputStreamPossiblyZIPorGZIP(fileName))) {
			write(w, taxaBlock, dataBlock);
		}
	}

	public Class<T> getFromClass() {
		return fromClass;
	}
}
