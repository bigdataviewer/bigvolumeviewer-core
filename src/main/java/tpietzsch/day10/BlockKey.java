package tpietzsch.day10;

import java.util.Arrays;

public class BlockKey
{
	private final int[] gridPos;

	private final int level;

	private final int hashcode;

	public BlockKey( final int gridX, final int gridY, final int gridZ, final int level )
	{
		this.gridPos = new int[] { gridX, gridY, gridZ };
		this.level = level;

		int h = Arrays.hashCode( gridPos );
		h = 31 * h + level;
		hashcode = h;
	}

	public BlockKey( final int[] gridPos, final int level )
	{
		this( gridPos[ 0 ], gridPos[ 1 ], gridPos[ 2 ], level );
	}

	public int[] getGridPos()
	{
		return gridPos;
	}

	public int getLevel()
	{
		return level;
	}

	@Override
	public boolean equals( final Object obj )
	{
		if ( obj instanceof BlockKey )
		{
			final BlockKey b = ( BlockKey ) obj;
			return Arrays.equals( gridPos, b.gridPos )
					&& level == b.level;
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return hashcode;
	}
}
