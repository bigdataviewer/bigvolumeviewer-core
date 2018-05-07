package bdv.volume;

import java.util.ArrayList;
import java.util.Arrays;

import net.imglib2.Localizable;

public class RequiredBlocks
{
	private final int n;

	private final ArrayList< int[] > gridPositions;

	private final int[] min;

	private final int[] max;

	public RequiredBlocks( final int numDimensions )
	{
		n = numDimensions;
		gridPositions = new ArrayList<>();
		min = new int[ n ];
		max = new int[ n ];
		Arrays.fill( min, Integer.MAX_VALUE );
		Arrays.fill( max, Integer.MIN_VALUE );
	}

	public void add( final int[] pos )
	{
		for ( int d = 0; d < n; ++d )
		{
			min[ d ] = Math.min( min[ d ], pos[ d ] );
			max[ d ] = Math.max( max[ d ], pos[ d ] );
		}
		gridPositions.add( pos );
	}

	public void add( final Localizable pos )
	{
		final int[] p = new int[ n ];
		pos.localize( p );
		add( p );
	}

	public ArrayList< int[] > getGridPositions()
	{
		return gridPositions;
	}

	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append( "RequiredBlocks( " );
		sb.append( String.format( "(%d, %d, %d) .. (%d, %d, %d) ",
				min[ 0 ], min[ 1 ], min[ 2 ],
				max[ 0 ], max[ 1 ], max[ 2 ] ) );
		sb.append( "size = " );
		sb.append( gridPositions.size() );
		return sb.toString();
	}

	public int[] getMin()
	{
		return min;
	}

	public int[] getMax()
	{
		return max;
	}
}
