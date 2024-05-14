/*
 *  ListIterator.java Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.utils;

import jloda.util.ICloseableIterator;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

public class ListIterator<T> implements ICloseableIterator<T> {
	private final List<T> list;
	private int pos = 0;

	public ListIterator(List<T> list) {
		this.list = list;
	}

	@Override
	public void close() throws IOException {

	}

	@Override
	public long getMaximumProgress() {
		return list.size();
	}

	@Override
	public long getProgress() {
		return pos;
	}

	@Override
	public boolean hasNext() {
		return pos < list.size();
	}

	@Override
	public T next() {
		if (pos == list.size())
			throw new
					NoSuchElementException();
		return list.get(pos++);
	}
}
