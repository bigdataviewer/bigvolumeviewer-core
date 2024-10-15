/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2024 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.blocks;

/**
 * Access to data at positions in a cell grid.
 *
 * @param <T>
 *            primitive array type of cell data, e.g. {@code short[]}.
 */
public interface GridDataAccess< T >
{
	void fwd( int d );

	void setPosition( int position, int d );

	void setPosition( int[] position );

	// for debugging ...
	int[] getPosition();

	/**
	 * Get the primitive array with data of grid cell at current position.
	 *
	 * @return data at current position, or {@code null} if data is not valid.
	 */
	T get();


	/*
	 * ====================================================
	 * image and cell dimensions
	 * ====================================================
	 */

	/**
	 * Get the size of a standard cell in dimension {@code d}.
	 * Cells on the max border of the image may be truncated and may therefore have
	 * different size.
	 *
	 * @param d
	 *            dimension index
	 */
	int cellSize( int d );

	/**
	 * From the position of a cell in the grid, compute the size of the cell in
	 * dimension {@code d}. The size will be the standard cell size, unless the
	 * cell is at the border of the image, in which case it might be truncated.
	 *
	 * @param d
	 *            dimension index
	 * @param cellGridPosition
	 *            grid coordinates of the cell in dimension {@code d}.
	 * @return size of the cell in dimension {@code d}.
	 */
	int cellSize( int d, int cellGridPosition );

	/**
	 * Get the image size in dimension {@code d} in pixels. Note, that this
	 * is the number of pixels in all cells combined, not the number of cells!
	 *
	 * @param d
	 *            dimension index
	 */
	int imgSize( int d );
}
