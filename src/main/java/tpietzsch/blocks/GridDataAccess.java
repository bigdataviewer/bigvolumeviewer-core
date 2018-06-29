package tpietzsch.blocks;

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
