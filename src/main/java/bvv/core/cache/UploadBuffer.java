/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2026 Tobias Pietzsch
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One tile's portion of a staging buffer, to be filled with image block data and
 * then uploaded to the {@link TextureCache}.
 * <p>
 * The staging buffer is shared by many tiles, so {@link #buffer()} is a view
 * limited to this tile's portion.
 * <p>
 * {@code UploadBuffer} is created by {@code PboChain.take}, filled once, and
 * discarded once it was uploaded to the {@link TextureCache}
 */
public class UploadBuffer
{
	private final ByteBuffer buffer;

	private TextureCache.ContentState state;

	/**
	 * @param buffer
	 * 		the staging buffer this tile is part of
	 * @param offset
	 * 		start of this tile in {@code buffer}, in bytes
	 * @param size
	 * 		size of this tile, in bytes
	 */
	public UploadBuffer( final ByteBuffer buffer, final int offset, final int size )
	{
		final ByteBuffer dup = buffer.duplicate();
		dup.position( offset );
		dup.limit( offset + size );
		// NB: order() has to be set after slice(), which does not inherit it.
		this.buffer = dup.slice().order( ByteOrder.nativeOrder() );
	}

	/**
	 * This tile's portion of the staging buffer, in native byte order. Writing
	 * more than {@code size} bytes to it throws rather than running into the
	 * next tile.
	 */
	public ByteBuffer buffer()
	{
		return buffer;
	}

	/**
	 * Called by FillTask to say whether stored image block data was complete.
	 */
	public void setContentState( final TextureCache.ContentState state )
	{
		this.state = state;
	}

	/**
	 * Was stored image block data complete?
	 */
	public TextureCache.ContentState getContentState()
	{
		return state;
	}
}
