package tpietzsch.blockmath3;

import java.util.Arrays;

public class BlockKey
{
	private final int[] gridPos;

	private final int level;

	// TODO: Instead of timepoint, setup, use StackId or something similar that abstracts those details
	private final int timepoint;
	private final int setup;

	private final int hashcode;

	public BlockKey( final int gridX, final int gridY, final int gridZ, final int level, final int timepoint, final int setup )
	{
		this.gridPos = new int[] { gridX, gridY, gridZ };
		this.level = level;
		this.timepoint = timepoint;
		this.setup = setup;

		int h = Arrays.hashCode( gridPos );
		h = 31 * h + level;
		h = 31 * h + timepoint;
		h = 31 * h + setup;
		hashcode = h;
	}

	public BlockKey( final int[] gridPos, final int level, final int timepoint, final int setup )
	{
		this( gridPos[ 0 ], gridPos[ 1 ], gridPos[ 2 ], level, timepoint, setup );
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
					&& level == b.level
					&& timepoint == b.timepoint
					&& setup == b.setup;
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return hashcode;
	}
}
