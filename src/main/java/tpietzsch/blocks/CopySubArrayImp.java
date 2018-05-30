package tpietzsch.blocks;

import static tpietzsch.blocks.ByteUtils.addressOf;

import java.nio.Buffer;

public class CopySubArrayImp
{
	// -------------------------------------------------------------
	// short[] to short[]

	public static class ShortToShort implements CopySubArray< short[], short[] >
	{
		@Override
		public void clearsubarray3d( final short[] dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			CopySubArrayImp.fillsubarray3d( ( short ) 0, dst, dox, doy, doz, dsx, dsy, csx, csy, csz );
		}

		@Override
		public void copysubarray3d( final short[] src, final int sox, final int soy, final int soz, final int ssx, final int ssy, final short[] dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			CopySubArrayImp.copysubarray3d( src, sox, soy, soz, ssx, ssy, dst, dox, doy, doz, dsx, dsy, csx, csy, csz );
		}
	}

	public static void copysubarray3d(
			final short[] src,
			final int sox,
			final int soy,
			final int soz,
			final int ssx,
			final int ssy,
			final short[] dst,
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
			copysubarray2d( src, sox, soy + soz * ssy + z * ssy, ssx, dst, dox, doy + doz * dsy + z * dsy, dsx, csx, csy );
	}

	public static void copysubarray2d(
			final short[] src,
			final int sox,
			final int soy,
			final int ssx,
			final short[] dst,
			final int dox,
			final int doy,
			final int dsx,
			final int csx,
			final int csy )
	{
		for ( int y = 0; y < csy; ++y )
			copysubarray1d( src, sox + soy * ssx + y * ssx, dst, dox + doy * dsx + y * dsx, csx );
	}

	public static void copysubarray1d(
			final short[] src,
			final int sox,
			final short[] dst,
			final int dox,
			final int csx )
	{
		for ( int x = 0; x < csx; ++x )
			dst[ dox + x ] = src[ sox + x ];
	}

	public static void fillsubarray3d(
			final short src,
			final short[] dst,
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
			fillsubarray2d( src, dst, dox, doy + doz * dsy + z * dsy, dsx, csx, csy );
	}

	public static void fillsubarray2d(
			final short src,
			final short[] dst,
			final int dox,
			final int doy,
			final int dsx,
			final int csx,
			final int csy )
	{
		for ( int y = 0; y < csy; ++y )
			fillsubarray1d( src, dst, dox + doy * dsx + y * dsx, csx );
	}

	public static void fillsubarray1d(
			final short src,
			final short[] dst,
			final int dox,
			final int csx )
	{
		for ( int x = 0; x < csx; ++x )
			dst[ dox + x ] = src;
	}

	// -------------------------------------------------------------
	// short[] to address

	public static class ShortToBuffer implements CopySubArray< short[], Buffer >
	{
		@Override
		public void clearsubarray3d( final Buffer dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			CopySubArrayImp.fillsubarray3dn( ( short ) 0, addressOf( dst ), dox, doy, doz, dsx, dsy, csx, csy, csz );
		}

		@Override
		public void copysubarray3d( final short[] src, final int sox, final int soy, final int soz, final int ssx, final int ssy, final Buffer dst, final int dox, final int doy, final int doz, final int dsx, final int dsy, final int csx, final int csy, final int csz )
		{
			CopySubArrayImp.copysubarray3dn( src, sox, soy, soz, ssx, ssy, addressOf( dst ), dox, doy, doz, dsx, dsy, csx, csy, csz );
		}
	}

	public static void copysubarray3dn(
			final short[] src,
			final int sox,
			final int soy,
			final int soz,
			final int ssx,
			final int ssy,
			final long dst,
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
			copysubarray2dn( src, sox, soy + soz * ssy + z * ssy, ssx, dst, dox, doy + doz * dsy + z * dsy, dsx, csx, csy );
	}

	public static void copysubarray2dn(
			final short[] src,
			final int sox,
			final int soy,
			final int ssx,
			final long dst,
			final int dox,
			final int doy,
			final int dsx,
			final int csx,
			final int csy )
	{
		for ( int y = 0; y < csy; ++y )
			copysubarray1dn( src, sox + soy * ssx + y * ssx, dst, dox + doy * dsx + y * dsx, csx );
	}

	public static void copysubarray1dn(
			final short[] src,
			final int sox,
			final long dst,
			final int dox,
			final int csx )
	{
		ByteUtils.copyShorts( src, dst + 2 * dox, sox, csx );
	}

	public static void fillsubarray3dn(
			final short src,
			final long dst,
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
			fillsubarray2dn( src, dst, dox, doy + doz * dsy + z * dsy, dsx, csx, csy );
	}

	public static void fillsubarray2dn(
			final short src,
			final long dst,
			final int dox,
			final int doy,
			final int dsx,
			final int csx,
			final int csy )
	{
		for ( int y = 0; y < csy; ++y )
			fillsubarray1dn( src, dst, dox + doy * dsx + y * dsx, csx );
	}

	public static void fillsubarray1dn(
			final short src,
			final long dst,
			final int dox,
			final int csx )
	{
		ByteUtils.setShorts( src, dst + 2 * dox, csx );
	}
}
