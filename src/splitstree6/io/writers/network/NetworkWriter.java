package splitstree6.io.writers.network;

import splitstree6.data.NetworkBlock;
import splitstree6.io.utils.DataWriterBase;

public abstract class NetworkWriter extends DataWriterBase<NetworkBlock> {
	public NetworkWriter() {
		super(NetworkBlock.class);
	}
}
