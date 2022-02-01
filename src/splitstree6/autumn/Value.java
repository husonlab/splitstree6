/*
 * Value.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package splitstree6.autumn;

import jloda.util.Single;

/**
 * single mutable integer value
 * Daniel Huson, 7.2011
 */
public class Value extends Single<Integer> {
	/**
	 * constructor
	 */
	public Value() {
		super(0);
	}

	/**
	 * constructor
	 *
	 * @param value
	 */
	public Value(Integer value) {
		super(value);
	}

	/**
	 * synchronized get
	 *
	 * @return value
	 */
	public Integer get() {
		synchronized (this) {
			return super.get();
		}
	}

	/**
     * synchronized set
     *
     * @param value
     */
    public void set(Integer value) {
        synchronized (this) {
            super.set(value);
        }
    }

	/**
	 * set to lower value. If value is not lower, does nothing
	 *
	 * @param value
	 */
	public void lowerTo(int value) {
		synchronized (this) {
			if (super.get() > value)
				super.set(value);
		}

	}
}
