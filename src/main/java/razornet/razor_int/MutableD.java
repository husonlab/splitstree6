/*
 * MutableD.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package razornet.razor_int;

/**
 * maintains a symmetric integer matrix with zero diagonal, using a lower triangle representation
 * Daniel Huson, 10.2025
 */
public final class MutableD {
	private int[][] lowerTriangleMatrix;

	/**
	 * construct from an existing symmetric square integer matrix with zero diagonal
	 *
	 * @param matrix input matrix
	 * @throws IllegalArgumentException if input matrix is not square, not symmetric or has a non-zero diagonal element
	 */
	public MutableD(int[][] matrix) {
		this(matrix.length);
		for (var i = 0; i < matrix.length; i++) {
			if (matrix[i].length != matrix[0].length) {
				throw new IllegalArgumentException("Matrix is not square");
			}
			for (int j = 0; j < i; j++) {
				if (matrix[i][j] != matrix[j][i]) {
					throw new IllegalArgumentException("Matrix is not symmetric");
				}
				lowerTriangleMatrix[i][j] = matrix[i][j];
			}
		}
	}

	public MutableD(int size) {
		lowerTriangleMatrix = new int[size][];
		for (var i = 0; i < size; i++) {
			lowerTriangleMatrix[i] = new int[i];
		}
	}

	/**
	 * gets the size n of nxn matrix
	 *
	 * @return size
	 */
	public int size() {
		return lowerTriangleMatrix.length;
	}

	/**
	 * gets the value for indices i and j
	 *
	 * @param i one index
	 * @param j other index
	 * @return value
	 */
	public int get(int i, int j) {
		if (i < j)
			return lowerTriangleMatrix[j][i];
		else if (j < i)
			return lowerTriangleMatrix[i][j];
		else return 0;
	}

	/**
	 * sets the value for indices i and j
	 *
	 * @param i     one index
	 * @param j     other index
	 * @param value the new value
	 * @throws IllegalArgumentException if value is negative or diagonal value is non-zero
	 */
	public void set(int i, int j, int value) {
		if (i < j) {
			if (value < 0)
				throw new IllegalArgumentException("Negative value");
			lowerTriangleMatrix[j][i] = value;
		} else if (j < i) {
			if (value < 0)
				throw new IllegalArgumentException("Negative value");
			lowerTriangleMatrix[i][j] = value;
		} else if (value != 0)
			throw new IllegalArgumentException("Non-zero diagonal value");
	}

	/**
	 * Append a new vertex x' with distances cand[0 ... n-1] to existing nodes.
	 *
	 * @param newRowOrColumn new row or column
	 * @return the index of the appended vertex (equal to the old size)
	 * @throws IllegalArgumentException if new row does not have same length as matrix
	 */
	public int appendVertex(int[] newRowOrColumn) {
		var n = newRowCol();
		System.arraycopy(newRowOrColumn, 0, lowerTriangleMatrix[n], 0, n); // copy column to new row
		return n;
	}

	/**
	 * creates a new row/column in the matrix
	 *
	 * @return the index of the new row/column
	 */
	public int newRowCol() {
		var n = size();
		var tmp = new int[n + 1][];
		System.arraycopy(lowerTriangleMatrix, 0, tmp, 0, n); // copy references to existing rows
		tmp[n] = new int[n];// add new row
		lowerTriangleMatrix = tmp;
		return n;
	}
}