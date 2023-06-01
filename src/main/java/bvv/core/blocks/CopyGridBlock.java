/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.blocks;

public class CopyGridBlock
{
	private final int[][] spans = new int[ 3 ][ 6 ];
	private final int[] ls = new int[ 3 ];
	private final int[] gmin = new int[ 3 ];
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
			final T dst,
			final GridDataAccess< S > srca,
			final CopySubArray< S, T > copy )
	{
		for ( int d = 0; d < 3; ++d )
		{
			nmin[ d ] = min[ d ];
			ndim[ d ] = dim[ d ];
			css[ d ] = dim[ d ];
			doo[ d ] = doo2[ d ] = 0;
		}

		// check whether dst is completely outside of src
		for ( int d = 2; d >= 0; --d )
			if ( min[ d ] >= srca.imgSize( d ) || min[ d ] + dim[ d ] <= 0 )
			{
				copy.clearsubarray3d( dst, 0,0,0, dim[ 0 ], dim[ 1 ], dim[ 0 ], dim[ 1 ], dim[ 2 ] );
				return true;
			}

		// check whether dst is partially outside of src
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
			final int b = min[ d ] + dim[ d ] - srca.imgSize( d );
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

		return copyNoOob( nmin, ndim, doo, dim, dst, srca, copy );
	}

	/**
	 * Copy (non-aligned) block from cell grid.
	 *
	 * @param min min coordinate of block to copy
	 * @param dim dimensions of block to copy
	 * @param doff offset in destination
	 * @param ddim dimensions of destination
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
			final T dst,
			final GridDataAccess< S > srca,
			final CopySubArray< S, T > copy )
	{
		boolean complete = true;
		for ( int d = 0; d < 3; ++d )
		{
			final int cellsize = srca.cellSize( d );
			final int g0 = min[ d ] / cellsize;
			final int g1 = ( min[ d ] + dim[ d ] - 1 ) / cellsize;
			gmin[ d ] = g0;
			ls[ d ] = g1 - g0 + 1;
			final int spanreq = 3 * ls[ d ];
			if ( spans[ d ].length < spanreq )
				spans[ d ] = new int[ spanreq ];
			final int[] span = spans[ d ];
			int i = 0;
			int o = min[ d ] - g0 * cellsize;
			for ( int g = g0; g < g1; ++g )
			{
				span[ i++ ] = o;
				span[ i++ ] = cellsize - o;
				span[ i++ ] = cellsize;
				o = 0;
			}
			span[ i++ ] = o;
			span[ i++ ] = min[ d ] + dim[ d ] - g1 * cellsize - o;
			span[ i ] = srca.cellSize( d, g1 );
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
						copy.clearsubarray3d( dst, dox, doy, doz, dsx, dsy, sx, sy, sz );
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

	public boolean canLoadCompletely(
			final int[] min,
			final int[] dim,
			final GridDataAccess< ? > srca,
			final boolean failfast )
	{
		// check whether dst is completely outside of src
		for ( int d = 2; d >= 0; --d )
			if ( min[ d ] >= srca.imgSize( d ) || min[ d ] + dim[ d ] <= 0 )
				return true;

		// check whether dst is partially outside of src
		for ( int d = 0; d < 3; ++d )
		{
			final int srcsize = srca.imgSize( d );
			nmin[ d ] = min[ d ];
			ndim[ d ] = dim[ d ];
			if ( min[ d ] < 0 )
			{
				nmin[ d ] = 0;
				ndim[ d ] += min[ d ];
			}
			final int b = min[ d ] + dim[ d ] - srcsize;
			if ( b > 0 )
				ndim[ d ] -= b;
		}

		return canLoadCompletelyNoOob( nmin, ndim, srca, failfast );
	}

	private boolean canLoadCompletelyNoOob(
			final int[] min,
			final int[] dim,
			final GridDataAccess< ? > srca,
			final boolean failfast )
	{
		boolean complete = true;
		for ( int d = 0; d < 3; ++d )
		{
			final int cellsize = srca.cellSize( d );
			final int g0 = min[ d ] / cellsize;
			final int g1 = ( min[ d ] + dim[ d ] - 1 ) / cellsize;
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
					{
						complete = false;
						if( failfast )
							return false;
					}
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
	}


	public boolean canLoadPartially(
			final int[] min,
			final int[] dim,
			final GridDataAccess< ? > srca )
	{
		// check whether dst is completely outside of src
		for ( int d = 2; d >= 0; --d )
			if ( min[ d ] >= srca.imgSize( d ) || min[ d ] + dim[ d ] <= 0 )
				return false;

		// check whether dst is partially outside of src
		for ( int d = 0; d < 3; ++d )
		{
			final int srcsize = srca.imgSize( d );
			nmin[ d ] = min[ d ];
			ndim[ d ] = dim[ d ];
			if ( min[ d ] < 0 )
			{
				nmin[ d ] = 0;
				ndim[ d ] += min[ d ];
			}
			final int b = min[ d ] + dim[ d ] - srcsize;
			if ( b > 0 )
				ndim[ d ] -= b;
		}

		return canLoadPartiallyNoOob( nmin, ndim, srca );
	}

	private boolean canLoadPartiallyNoOob(
			final int[] min,
			final int[] dim,
			final GridDataAccess< ? > srca )
	{
		for ( int d = 0; d < 3; ++d )
		{
			final int cellsize = srca.cellSize( d );
			final int g0 = min[ d ] / cellsize;
			final int g1 = ( min[ d ] + dim[ d ] - 1 ) / cellsize;
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
					if ( srca.get() != null )
						return true;
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

		return false;
	}
}
