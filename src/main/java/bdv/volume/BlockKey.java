package bdv.volume;

import java.util.Arrays;

/**
 * Represents an
 *
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class BlockKey
{
	/**
	 * The cell grid coordinates of the source data.
	 */
	private final int[] cellPos;

	private final int hashcode;

	public BlockKey(
			final int[] cellGridPos,
			final int timepoint,
			final int setup,
			final int level )
	{
		this.cellPos = cellGridPos.clone();

		int value = 17;
		value = 31 * value + cellGridPos[ 0 ];
		value = 31 * value + cellGridPos[ 1 ];
		value = 31 * value + cellGridPos[ 2 ];
		value = 31 * value + timepoint;
		value = 31 * value + setup;
		value = 31 * value + level;
		hashcode = value;
	}

	@Override
	public boolean equals( final Object obj )
	{
		if ( obj instanceof BlockKey )
		{
			final BlockKey b = ( BlockKey ) obj;
			return Arrays.equals( cellPos, b.cellPos );
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return hashcode;
	}
}