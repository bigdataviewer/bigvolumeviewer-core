package tpietzsch.cache;

/**
 * Identifies a particular block in a particular image.
 *
 * @param <T>
 *     image type
 */
public class ImageBlockKey< T >
{
	private final T image;
	private final int x;
	private final int y;
	private final int z;

	public ImageBlockKey( final T image, final int x, final int y, final int z )
	{
		this.image = image;
		this.x = x;
		this.y = y;
		this.z = z;

		int value = image.hashCode();
		value = 31 * value + x;
		value = 31 * value + y;
		value = 31 * value + z;
		hashcode = value;
	}

	public ImageBlockKey( final T image, final int[] pos )
	{
		this( image, pos[ 0 ], pos[ 1 ], pos[ 2 ] );
	}

	public T image()
	{
		return image;
	}

	public int x()
	{
		return x;
	}

	public int y()
	{
		return y;
	}

	public int z()
	{
		return z;
	}

	/*
	 * convenience methods for accessing pos
	 */

	public int[] pos()
	{
		return pos( new int[ 3 ] );
	}

	public int[] pos( final int[] pos )
	{
		pos[ 0 ] = x;
		pos[ 1 ] = y;
		pos[ 2 ] = z;
		return pos;
	}

	public int pos( int d )
	{
		switch ( d )
		{
		case 0:
			return x;
		case 1:
			return y;
		case 2:
			return z;
		default:
			throw new IllegalArgumentException();
		}
	}

	@Override
	public boolean equals( final Object obj )
	{
		if ( ! ( obj instanceof ImageBlockKey ) )
			return false;

		final ImageBlockKey< T > b = ( ImageBlockKey< T > ) obj;
		return x == b.x
				&& y == b.y
				&& z == b.z
				&& image.equals( b.image );
	}

	private final int hashcode;

	@Override
	public int hashCode()
	{
		return hashcode;
	}
}
