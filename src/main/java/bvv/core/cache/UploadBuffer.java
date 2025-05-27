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

import java.nio.Buffer;

import bvv.core.blocks.ByteUtils;

public class UploadBuffer implements ByteUtils.Address
{
	private final Buffer buffer;
	private final int offset;
	private TextureCache.ContentState state;

	public UploadBuffer( final Buffer buffer, final int offset )
	{
		this.buffer = buffer;
		this.offset = offset;
	}

	/**
	 * Stores data for uploading to texture tile.
	 *
	 * @return a direct buffer
	 */
	public Buffer getBuffer()
	{
		return buffer;
	}

	/**
	 * Starting offset for tile in buffer.
	 *
	 * @return offset in bytes
	 */
	public int getOffset()
	{
		return offset;
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

	/**
	 * ...tentative...
	 */
	@Override
	public long getAddress()
	{
		return ByteUtils.addressOf( buffer ) + offset;
	}
}
