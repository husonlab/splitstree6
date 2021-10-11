package splitstree6.sflow;

/**
 * a workflow node that contains data
 * Daniel Huson, 10.2021
 *
 * @param <S> the data block type
 */
public class DataNode<S extends DataBlock> extends splitstree6.wflow.DataNode {
	public DataNode(Workflow workflow) {
		super(workflow);

		validProperty().addListener((v, o, n) -> {
			if (!n && getDataBlock() != null)
				getDataBlock().clear();
		});
	}

	@Override
	public DataBlock getDataBlock() {
		return (DataBlock) super.getDataBlock();
	}
}
