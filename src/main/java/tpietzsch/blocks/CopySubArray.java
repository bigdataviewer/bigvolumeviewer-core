package tpietzsch.blocks;

// e.g., S == short[], T = ByteBuffer
public interface CopySubArray< S, T >
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
			final S src,
			final int sox, final int soy, final int soz,
			final int ssx, final int ssy,
			final T dst,
			final int dox, final int doy, final int doz,
			final int dsx, final int dsy,
			final int csx, final int csy, final int csz );
}
