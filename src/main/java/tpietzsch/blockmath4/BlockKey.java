package tpietzsch.blockmath4;

import java.util.Arrays;

public class BlockKey
{
	private final int[] gridPos;

	private final Object stack;

	private final int hashcode;

	public BlockKey( final int gridX, final int gridY, final int gridZ, final Object stack )
	{
		this.gridPos = new int[] { gridX, gridY, gridZ };
		this.stack = stack;

		int h = Arrays.hashCode( gridPos );
		h = 31 * h + stack.hashCode();
		hashcode = h;
	}

	public BlockKey( final int[] gridPos, final Object stack )
	{
		this( gridPos[ 0 ], gridPos[ 1 ], gridPos[ 2 ], stack );
	}

	public int[] getGridPos()
	{
		return gridPos;
	}

	public Object getStack()
	{
		return stack;
	}

	@Override
	public boolean equals( final Object obj )
	{
		if ( obj instanceof BlockKey )
		{
			final BlockKey b = ( BlockKey) obj;
			return Arrays.equals( gridPos, b.gridPos )
					&& stack.equals( b.stack );
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return hashcode;
	}
}
