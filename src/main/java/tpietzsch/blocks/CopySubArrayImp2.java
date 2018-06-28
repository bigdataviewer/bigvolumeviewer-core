package tpietzsch.blocks;

import tpietzsch.blocks.CopySubArrayImp.Address;

public class CopySubArrayImp2
{
	// -------------------------------------------------------------
	// short[] to address

	public static class ShortToAddress implements CopySubArray< short[], Address >
	{
		@Override
		public void clearsubarray3d( final Address dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			final ArrayFill fill = ( o, l ) -> ByteUtils.setShorts( ( short ) 0, dst.getAddress() + 2 * o, l );
			CopySubArrayImp2.fillsubarray3dn( fill, dox, doy, doz, dsx, dsy, csx, csy, csz );
		}

		@Override
		public void copysubarray3d( final short[] src, final int sox, final int soy, final int soz, final int ssx, final int ssy, final Address dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
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
