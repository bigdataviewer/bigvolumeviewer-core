package tpietzsch.blocks;

/**
 * Access to data at positions in a cell grid.
 *
 * @param <T>
 *            primitve array type of cell data, e.g. {@code short[]}.
 */
public interface GridDataAccess< T >
{
	void fwd( int d );

	void setPosition( int position, int d );

	void setPosition( int[] position );

	// for debugging ...
	int[] getPosition();

	/**
	 * Get the primitve array with data of grid cell at current position.
	 *
	 * @return data at current position, or {@code null} if data is not valid.
	 */
	T get();
}
