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

public class CopySubArrayImp
{
	// -------------------------------------------------------------
	// short[] to address

	public static class ShortToAddress implements CopySubArray< short[], ByteUtils.Address >
	{
		@Override
		public void clearsubarray3d( final ByteUtils.Address dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			final ArrayFill fill = ( o, l ) -> ByteUtils.setShorts( ( short ) 0, dst.getAddress() + 2 * o, l );
			fillsubarray3dn( fill, dox, doy, doz, dsx, dsy, csx, csy, csz );
		}

		@Override
		public void copysubarray3d( final short[] src, final int sox, final int soy, final int soz, final int ssx, final int ssy, final ByteUtils.Address dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			final ArrayCopy copy = ( so, o, l ) -> ByteUtils.copyShorts( src, dst.getAddress() + 2 * o, so, l );
			copysubarray3dn( copy, sox, soy, soz, ssx, ssy, dox, doy, doz, dsx, dsy, csx, csy, csz );
		}
	}

	static void copysubarray3dn(
			ArrayCopy copysubarray1dn,
			final int sox,
			final int soy,
			final int soz,
			final int ssx,
			final int ssy,
			final int dox,
			final int doy,
			final int doz,
			final int dsx,
			final int dsy,
			final int csx,
			final int csy,
			final int csz )
	{
		for ( int z = 0; z < csz; ++z )
			copysubarray2dn( copysubarray1dn, sox, soy + soz * ssy + z * ssy, ssx, dox, doy + doz * dsy + z * dsy, dsx, csx, csy );
	}

	static void copysubarray2dn(
			ArrayCopy copysubarray1dn,
			final int sox,
			final int soy,
			final int ssx,
			final int dox,
			final int doy,
			final int dsx,
			final int csx,
			final int csy )
	{
		for ( int y = 0; y < csy; ++y )
			copysubarray1dn.copy( sox + soy * ssx + y * ssx, dox + doy * dsx + y * dsx, csx );
	}

	@FunctionalInterface
	interface ArrayCopy
	{
		void copy( int sox, int dox, int csx );
	}

	static void fillsubarray3dn(
			final ArrayFill fillsubarray1dn,
			final int dox,
			final int doy,
			final int doz,
			final int dsx,
			final int dsy,
			final int csx,
			final int csy,
			final int csz )
	{
		for ( int z = 0; z < csz; ++z )
			fillsubarray2dn( fillsubarray1dn, dox, doy + doz * dsy + z * dsy, dsx, csx, csy );
	}

	static void fillsubarray2dn(
			final ArrayFill fillsubarray1dn,
			final int dox,
			final int doy,
			final int dsx,
			final int csx,
			final int csy )
	{
		for ( int y = 0; y < csy; ++y )
			fillsubarray1dn.fill( dox + doy * dsx + y * dsx, csx );
	}

	@FunctionalInterface
	interface ArrayFill
	{
		void fill( int dox, int csx );
	}
}
