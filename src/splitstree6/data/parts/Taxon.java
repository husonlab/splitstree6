package splitstree6.data.parts;

public class Taxon {
	private String name;
	private String info;
	private String displayLabel;

	public Taxon() {
	}

	public Taxon(String name) {
		setName(name);
	}

	public Taxon(Taxon src) {
		name = src.name;
		displayLabel = src.displayLabel;
		info = src.info;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDisplayLabel() {
		return displayLabel;
	}

	public String getDisplayLabelOrName() {
		return displayLabel != null ? displayLabel : name;
	}

	public void setDisplayLabel(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

	public String toString() {
		return getName();
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Taxon) {
			Taxon that = (Taxon) other;
			return this.getName().equals(that.getName());
		} else
			return false;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}