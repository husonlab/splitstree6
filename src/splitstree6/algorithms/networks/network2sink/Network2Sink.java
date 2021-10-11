package splitstree6.algorithms.networks.network2sink;

import splitstree6.data.NetworkBlock;
import splitstree6.data.SinkBlock;
import splitstree6.methods.IgnoredInMethodsText;
import splitstree6.sflow.Algorithm;

public abstract class Network2Sink extends Algorithm<NetworkBlock, SinkBlock> implements IgnoredInMethodsText {
	public Network2Sink() {
		super(NetworkBlock.class, SinkBlock.class);
	}
}
