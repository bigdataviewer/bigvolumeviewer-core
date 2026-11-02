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
import java.util.function.BooleanSupplier;

import static bvv.core.cache.TextureCache.ContentState.COMPLETE;
import static bvv.core.cache.TextureCache.ContentState.INCOMPLETE;

public class DefaultFillTask implements FillTask
{
	/**
	 * Fills the tile portion of an upload buffer with one block of image data.
	 */
	@FunctionalInterface
	public interface Filler
	{
		/**
		 * Fill {@code buffer} with the block's data.
		 *
		 * @param buffer
		 * 		this tile's portion of the staging buffer
		 *
		 * @return {@code true} if the block data was complete, {@code false} if
		 * 		parts of it were not available
		 */
		boolean fill( ByteBuffer buffer );
	}

	final ImageBlockKey< ? > key;

	final Filler fill;

	final BooleanSupplier containsData;

	/**
	 * @param key
	 * 		identifies a particular block in a particular image.
	 * @param fill
	 * 		fills an upload buffer with image data of that block.
	 * @param containsData
	 * 		called to determine whether it makes sense to upload
	 * 		a block, before allocating a texture slot or doing any copying work.
	 */
	public DefaultFillTask( final ImageBlockKey< ? > key, final Filler fill, final BooleanSupplier containsData )
	{
		this.key = key;
		this.fill = fill;
		this.containsData = containsData;
	}

	@Override
	public ImageBlockKey< ? > getKey()
	{
		return key;
	}

	@Override
	public boolean containsData()
	{
		return containsData.getAsBoolean();
	}

	@Override
	public void fill( final UploadBuffer buffer )
	{
		final boolean complete = fill.fill( buffer.buffer() );
		buffer.setContentState( complete ? COMPLETE : INCOMPLETE );
	}
}
