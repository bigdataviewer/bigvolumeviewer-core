package tpietzsch.blocks;

import net.imglib2.img.cell.CellGrid;

public class CopyGridBlock
{
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
	 * Copy (non-aligned) block from cell grid.
	 *
	 * @param min
	 *            min coordinate of block to copy
	 * @param dim
	 *            size of block to copy
	 * @param srcgrid
	 *            dimensions of the source grid
	 * @param dst
	 * @param srca
	 * @param copy
	 *
	 * @param <S>
	 *            source primitive array type
	 * @param <T>
	 *            destination type (primitive array or buffer)
	 *
	 * @return {@code true}, if {@code dst} was completely loaded
	 */
	public < S, T > boolean copy(
			final int[] min,
			final int[] dim,
			final CellGrid srcgrid,
			final T dst,
			final GridDataAccess< S > srca,
			final CopySubArray< S, T > copy )
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

	/**
	 * Copy (non-aligned) block from cell grid.
	 *
	 * @param min min coordinate of block to copy
	 * @param dim dimensions of block to copy
	 * @param doff offset in destination
	 * @param ddim dimensions of destination
	 * @param srcgrid cell dimensions of the source grid
	 * @param dst destination array
	 * @param srca access to cell storage arrays of source
	 * @param copy functions to copy and clear subarrays
	 *
	 * @param <S>
	 *            source primitive array type
	 * @param <T>
	 *            destination type (primitive array or buffer)
	 *
	 * @return {@code true}, if {@code dst} was completely loaded
	 */
	private < S, T > boolean copyNoOob(
			final int[] min,
			final int[] dim,
			final int[] doff,
			final int[] ddim,
			final CellGrid srcgrid,
			final T dst,
			final GridDataAccess< S > srca,
			final CopySubArray< S, T > copy )
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
					final S src = srca.get();
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

	public boolean canLoadCompletely( final int[] min, final int[] dim, final CellGrid srcgrid, final GridDataAccess< ? > srca )
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

	private boolean canLoadCompletelyNoOob( final int[] min, final int[] dim, final CellGrid srcgrid, final GridDataAccess< ? > srca )
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
}
