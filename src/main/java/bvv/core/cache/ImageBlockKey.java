/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
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
package bvv.core.cache;

/**
 * Identifies a particular block in a particular image.
 *
 * @param <T>
 *            image type
 */
public class ImageBlockKey< T >
{
	private final T image;
	private final int x;
	private final int y;
	private final int z;

	/**
	 * @param image one image per timepoint, channel, resolution level, etc
	 * @param x block X coordinate in image grid
	 * @param y block Y coordinate in image grid
	 * @param z block Z coordinate in image grid
	 */
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

	public int pos( final int d )
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
		if ( !( obj instanceof ImageBlockKey ) )
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
