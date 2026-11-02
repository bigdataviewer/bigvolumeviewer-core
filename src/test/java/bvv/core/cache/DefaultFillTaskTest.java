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

import static bvv.core.cache.TextureCache.ContentState.COMPLETE;
import static bvv.core.cache.TextureCache.ContentState.INCOMPLETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

/**
 * {@code DefaultFillTask} is the adapter between a {@link DefaultFillTask.Filler}
 * — which fills bytes and says whether the block was complete — and the {@code
 * ContentState} the texture cache stores on the tile. Getting that mapping
 * backwards would leave complete tiles marked {@code INCOMPLETE} (re-requested
 * forever) or incomplete ones marked {@code COMPLETE} (never refreshed).
 */
public class DefaultFillTaskTest
{
	private static final int SIZE = 16;

	private static UploadBuffer uploadBuffer()
	{
		return new UploadBuffer( ByteBuffer.allocateDirect( SIZE ), 0, SIZE );
	}

	private static ImageBlockKey< Object > key()
	{
		return new ImageBlockKey<>( new Object(), new int[] { 1, 2, 3 } );
	}

	@Test
	public void completeFillIsMarkedComplete()
	{
		final UploadBuffer buffer = uploadBuffer();
		new DefaultFillTask( key(), buf -> true, () -> true ).fill( buffer );
		assertEquals( COMPLETE, buffer.getContentState() );
	}

	@Test
	public void incompleteFillIsMarkedIncomplete()
	{
		final UploadBuffer buffer = uploadBuffer();
		new DefaultFillTask( key(), buf -> false, () -> true ).fill( buffer );
		assertEquals( INCOMPLETE, buffer.getContentState() );
	}

	@Test
	public void fillerWritesIntoTheTileBuffer()
	{
		final UploadBuffer buffer = uploadBuffer();
		final byte[] data = new byte[ SIZE ];
		for ( int i = 0; i < SIZE; ++i )
			data[ i ] = ( byte ) ( i + 1 );

		new DefaultFillTask( key(), buf -> {
			buf.put( data );
			return true;
		}, () -> true ).fill( buffer );

		for ( int i = 0; i < SIZE; ++i )
			assertEquals( "byte " + i, data[ i ], buffer.buffer().get( i ) );
	}

	@Test
	public void keyAndContainsDataAreForwarded()
	{
		final ImageBlockKey< Object > key = key();
		assertSame( key, new DefaultFillTask( key, buf -> true, () -> true ).getKey() );
		assertTrue( new DefaultFillTask( key, buf -> true, () -> true ).containsData() );
		assertFalse( new DefaultFillTask( key, buf -> true, () -> false ).containsData() );
	}
}
