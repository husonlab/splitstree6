package splitstree6.io.utils;

import java.util.ArrayList;

public class ReaderWriterBase {
	private final ArrayList<String> fileExtensions = new ArrayList<>();

	public ArrayList<String> getFileExtensions() {
		return fileExtensions;
	}

	public void setFileExtensions(String... extensions) {
		for (var ex : extensions) {
			if (!fileExtensions.contains(ex))
				fileExtensions.add(ex);
			if (!fileExtensions.contains(ex + ".gz"))
				fileExtensions.add(ex + ".gz");
		}
	}

	public boolean accepts(String file) {
		if (fileExtensions.size() == 0)
			return true;
		else {
			for (var ex : fileExtensions) {
				if (file.endsWith(ex))
					return true;
			}
			return false;
		}
	}
}
