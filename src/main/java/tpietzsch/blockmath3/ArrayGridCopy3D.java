package tpietzsch.blockmath3;

import java.util.Arrays;

import net.imglib2.RandomAccess;
import net.imglib2.img.basictypeaccess.array.AbstractShortArray;
import net.imglib2.img.basictypeaccess.volatiles.array.AbstractVolatileShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;

public class ArrayGridCopy3D
{
	// e.g., T == short[]
	public interface CellDataAccess< T >
	{
		void fwd( int d );

		void setPosition( int position, int d );

		void setPosition( int[] position );

		// for debugging ...
		int[] getPosition();

		// returns null if not valid, otherwise current storage array
		T get();
	}

	// e.g., T == short[]
	public interface SubArrayCopy< T >
	{
		// dox, doy, doz: start offset in dst
		// dsx, dsy: dimensions of dst
		// csx, csy, csz: dimensions of block to clear
		void clearsubarray3d(
				final T dst,
				final int dox, final int doy, final int doz,
				final int dsx, final int dsy,
				final int csx, final int csy, final int csz );

		// sox, soy, soz: start offset in src
		// ssx, ssy: dimensions of src
		// dox, doy, doz: start offset in dst
		// dsx, dsy: dimensions of dst
		// csx, csy, csz: dimensions of block to copy
		void copysubarray3d(
				final T src,
				final int sox, final int soy, final int soz,
				final int ssx, final int ssy,
				final T dst,
				final int dox, final int doy, final int doz,
				final int dsx, final int dsy,
				final int csx, final int csy, final int csz );
	}

	private final int[][] spans = new int[ 3 ][ 6 ];
	private final int[] ls = new int[ 3 ];
	private final int[] gmin = new int[ 3 ];
	private final int[] srcsize = new int[ 3 ];
	private final int[] cellsize = new int[ 3 ];
	private final int nmin[] = new int[ 3 ];
	private final int ndim[] = new int[ 3 ];
	private final int css[] = new int[ 3 ];
	private final int[] doo = new int[ 3 ];
	private final int[] doo2 = new int[ 3 ];

	/**
	 *
	 * @param min min coordinate of block to copy
	 * @param dim size of block to copy
	 * @param srcgrid dimensions of the source grid
	 * @param dst
	 * @param srca
	 * @param copy
	 * @param <T>
	 *
	 * @return {@code true}, if this block was completely loaded
	 */
	public < T > boolean copy( final int[] min, final int[] dim, final CellGrid srcgrid, final T dst, final CellDataAccess< T > srca, final SubArrayCopy< T > copy )
	{
		for ( int d = 0; d < 3; ++d )
		{
			srcsize[ d ] = ( int ) srcgrid.imgDimension( d );
			nmin[ d ] = min[ d ];
			ndim[ d ] = dim[ d ];
			css[ d ] = dim[ d ];
			doo[ d ] = doo2[ d ] = 0;
		}

		// TODO check whether dst is completely outside of src

		for ( int d = 2; d >= 0; --d )
		{
			if ( min[ d ] < 0 )
			{
				css[ d ] = -min[ d ];
				copy.clearsubarray3d( dst, doo[ 0 ], doo[ 1 ], doo[ 2 ], dim[ 0 ], dim[ 1 ], css[ 0 ], css[ 1 ], css[ 2 ] );
				nmin[ d ] = 0;
				ndim[ d ] -= css[ d ];
				doo[ d ] = css[ d ];
			}
			final int b = min[ d ] + dim[ d ] - srcsize[ d ];
			if ( b > 0 )
			{
				doo2[ d ] = dim[ d ] - b;
				css[ d ] = b;
				copy.clearsubarray3d( dst, doo2[ 0 ], doo2[ 1 ], doo2[ 2 ], dim[ 0 ], dim[ 1 ], css[ 0 ], css[ 1 ], css[ 2 ] );
				ndim[ d ] -= css[ d ];
			}
			css[ d ] = ndim[ d ];
			doo2[ d ] = doo[ d ];
		}

		return copyNoOob( nmin, ndim, doo, dim, srcgrid, dst, srca, copy );
	}

	public boolean canLoadCompletely( final int[] min, final int[] dim, final CellGrid srcgrid, final CellDataAccess< ? > srca )
	{
		for ( int d = 0; d < 3; ++d )
		{
			srcsize[ d ] = ( int ) srcgrid.imgDimension( d );
			nmin[ d ] = min[ d ];
			ndim[ d ] = dim[ d ];
			if ( min[ d ] < 0 )
			{
				nmin[ d ] = 0;
				ndim[ d ] += min[ d ];
			}
			final int b = min[ d ] + dim[ d ] - srcsize[ d ];
			if ( b > 0 )
				ndim[ d ] -= b;
		}
		// TODO check whether dst is completely outside of src

		return canLoadCompletelyNoOob( nmin, ndim, srcgrid, srca );
	}

	private boolean canLoadCompletelyNoOob( final int[] min, final int[] dim, final CellGrid srcgrid, final CellDataAccess< ? > srca )
	{
		boolean complete = true;
		srcgrid.cellDimensions( cellsize );
		for ( int d = 0; d < 3; ++d )
		{
			final int g0 = min[ d ] / cellsize[ d ];
			final int g1 = ( min[ d ] + dim[ d ] - 1 ) / cellsize[ d ];
			gmin[ d ] = g0;
			ls[ d ] = g1 - g0 + 1;
		}

		srca.setPosition( gmin );
		final int gsx = ls[ 0 ];
		final int gsy = ls[ 1 ];
		final int gsz = ls[ 2 ];
		for ( int gz = 0; gz < gsz; ++gz )
		{
			for ( int gy = 0; gy < gsy; ++gy )
			{
				for ( int gx = 0; gx < gsx; ++gx )
				{
					if ( srca.get() == null )
						complete = false;
//						return false;
					if ( gx < gsx - 1 )
						srca.fwd( 0 );
				}
				if ( gsx > 1 )
					srca.setPosition( gmin[ 0 ], 0 );
				if ( gy < gsy - 1 )
					srca.fwd( 1 );
			}
			if ( gsy > 1 )
				srca.setPosition( gmin[ 1 ], 1 );
			if ( gz < gsz - 1 )
				srca.fwd( 2 );
		}

		return complete;
//		return true;
	}

	/**
	 * @param min min coordinate of block to copy
	 * @param dim dimensions of block to copy
	 * @param doff offset in destination
	 * @param ddim dimensions of destination
	 * @param srcgrid cell dimensions of the source grid
	 * @param dst destination array
	 * @param srca access to cell storage arrays of source
	 * @param copy functions to copy and clear subarrays
	 * @param <T> source and destination array type
	 */
	private < T > boolean copyNoOob( final int[] min, final int[] dim, final int[] doff, final int[] ddim, final CellGrid srcgrid, final T dst, final CellDataAccess< T > srca, final SubArrayCopy< T > copy )
	{
		boolean complete = true;
		srcgrid.cellDimensions( cellsize );
		for ( int d = 0; d < 3; ++d )
		{
			final int g0 = min[ d ] / cellsize[ d ];
			final int g1 = ( min[ d ] + dim[ d ] - 1 ) / cellsize[ d ];
			gmin[ d ] = g0;
			ls[ d ] = g1 - g0 + 1;
			final int spanreq = 3 * ls[ d ];
			if ( spans[ d ].length < spanreq )
				spans[ d ] = new int[ spanreq ];
			final int[] span = spans[ d ];
			int i = 0;
			int o = min[ d ] - g0 * cellsize[ d ];
			for ( int g = g0; g < g1; ++g )
			{
				span[ i++ ] = o;
				span[ i++ ] = cellsize[ d ] - o;
				span[ i++ ] = cellsize[ d ];
				o = 0;
			}
			span[ i++ ] = o;
			span[ i++ ] = min[ d ] + dim[ d ] - g1 * cellsize[ d ] - o;
			span[ i ] = srcgrid.getCellDimension( d, g1 );
		}

		srca.setPosition( gmin );
		final int gsx = ls[ 0 ];
		final int gsy = ls[ 1 ];
		final int gsz = ls[ 2 ];
		final int[] spanx = spans[ 0 ];
		final int[] spany = spans[ 1 ];
		final int[] spanz = spans[ 2 ];
		final int dsx = ddim[ 0 ];
		final int dsy = ddim[ 1 ];
		int doz = doff[ 2 ];
		for ( int gz = 0; gz < gsz; ++gz )
		{
			final int oz = spanz[ 3 * gz ];
			final int sz = spanz[ 3 * gz + 1 ];
			int doy = doff[ 1 ];
			for ( int gy = 0; gy < gsy; ++gy )
			{
				final int oy = spany[ 3 * gy ];
				final int sy = spany[ 3 * gy + 1 ];
				final int ssy = spany[ 3 * gy + 2 ];
				int dox = doff[ 0 ];
				for ( int gx = 0; gx < gsx; ++gx )
				{
					final int ox = spanx[ 3 * gx ];
					final int sx = spanx[ 3 * gx + 1 ];
					final int ssx = spanx[ 3 * gx + 2 ];
					final T src = srca.get();
					if ( src == null )
					{
						complete = false;
					}
					else
						copy.copysubarray3d( src, ox, oy, oz, ssx, ssy, dst, dox, doy, doz, dsx, dsy, sx, sy, sz );
					dox += sx;
					if ( gx < gsx - 1 )
						srca.fwd( 0 );
				}
				doy += sy;
				if ( gsx > 1 )
					srca.setPosition( gmin[ 0 ], 0 );
				if ( gy < gsy - 1 )
					srca.fwd( 1 );
			}
			doz += sz;
			if ( gsy > 1 )
				srca.setPosition( gmin[ 1 ], 1 );
			if ( gz < gsz - 1 )
				srca.fwd( 2 );
		}

		return complete;
	}

	public static class ShortCellDataAccess implements CellDataAccess< short[] >
	{
		private final RandomAccess< ? extends Cell< ? extends AbstractShortArray< ? > > > cellAccess;

		public ShortCellDataAccess( final RandomAccess< ? extends Cell< ? extends AbstractShortArray< ? > > > cellAccess )
		{
			this.cellAccess = cellAccess;
		}

		@Override
		public void fwd( final int d )
		{
			cellAccess.fwd( d );
		}

		@Override
		public void setPosition( final int position, final int d )
		{
			cellAccess.setPosition( position, d );
		}

		@Override
		public void setPosition( final int[] position )
		{
			cellAccess.setPosition( position );
		}

		@Override
		public int[] getPosition()
		{
			return new int[] { cellAccess.getIntPosition( 0 ), cellAccess.getIntPosition( 1 ), cellAccess.getIntPosition( 2 ) };
		}

		@Override
		public short[] get()
		{
			return cellAccess.get().getData().getCurrentStorageArray();
		}
	}

	public static class VolatileShortCellDataAccess implements CellDataAccess< short[] >
	{
		private final RandomAccess< ? extends Cell< ? extends AbstractVolatileShortArray< ? > > > cellAccess;

		public VolatileShortCellDataAccess( final RandomAccess< ? extends Cell< ? extends AbstractVolatileShortArray< ? > > > cellAccess )
		{
			this.cellAccess = cellAccess;
		}

		@Override
		public void fwd( final int d )
		{
			cellAccess.fwd( d );
		}

		@Override
		public void setPosition( final int position, final int d )
		{
			cellAccess.setPosition( position, d );
		}

		@Override
		public void setPosition( final int[] position )
		{
			cellAccess.setPosition( position );
		}

		@Override
		public int[] getPosition()
		{
			return new int[] { cellAccess.getIntPosition( 0 ), cellAccess.getIntPosition( 1 ), cellAccess.getIntPosition( 2 ) };
		}

		@Override
		public short[] get()
		{
			final AbstractVolatileShortArray< ? > data = cellAccess.get().getData();
			return data.isValid() ? data.getCurrentStorageArray() : null;
		}
	}

	public static class ShortSubArrayCopy implements SubArrayCopy< short[] >
	{
		@Override
		public void clearsubarray3d( final short[] dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			SubArrays.fillsubarray3d( ( short ) 0, dst, dox, doy, doz, dsx, dsy, csx, csy, csz );
		}

		@Override
		public void copysubarray3d( final short[] src, final int sox, final int soy, final int soz, final int ssx, final int ssy, final short[] dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			SubArrays.copysubarray3d( src, sox, soy, soz, ssx, ssy, dst, dox, doy, doz, dsx, dsy, csx, csy, csz );
		}
	}
}
