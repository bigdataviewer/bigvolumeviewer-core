package tpietzsch.blockmath1;

import java.util.Arrays;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;

public class RaiLevel
{
	final int level;

	final int[] r;

	final double[] s;

	final RandomAccessibleInterval< UnsignedShortType > rai;

	public RaiLevel( final int level, final double[] resolution, final RandomAccessibleInterval< UnsignedShortType > rai )
	{
		this.level = level;
		this.r = new int[] { (int) resolution[ 0 ], (int) resolution[ 1 ], (int) resolution[ 2 ]  };
		this.s = new double[] { 1 / resolution[ 0 ], 1 / resolution[ 1 ], 1 / resolution[ 2 ] };
		this.rai = rai;
	}

	public int[] imgGridSize( final int[] blockSize )
	{
		final int[] imgGridSize = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			imgGridSize[ d ] = ( int ) ( rai.dimension( d ) - 1 ) / blockSize[ d ] + 1;
		return imgGridSize;
	}

	@Override
	public String toString()
	{
		return "RaiLevel{level=" + level + ", r=" + Arrays.toString( r ) + ", s=" + Arrays.toString( s ) + ", rai=" + Util.printInterval( rai ) + '}';
	}
}
