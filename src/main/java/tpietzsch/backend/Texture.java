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
package tpietzsch.backend;

public interface Texture
{
	enum InternalFormat
	{
		R8( 1 ),
		R16( 2 ),
		RGBA8( 4 ),
		RGBA8UI( 4 ),
		R32F( 4 ),
		UNKNOWN( -1 );

		InternalFormat( final int bytesPerElement )
		{
			this.bytesPerElement = bytesPerElement;
		}

		public int getBytesPerElement()
		{
			return bytesPerElement;
		}

		private int bytesPerElement;
	}

	enum MagFilter
	{
		NEAREST,
		LINEAR
	}

	enum MinFilter
	{
		NEAREST,
		LINEAR
	}

	enum Wrap
	{
		CLAMP_TO_EDGE,
		CLAMP_TO_BORDER_ZERO,
		REPEAT
	}

	InternalFormat texInternalFormat();

	int texWidth();

	int texHeight();

	int texDepth();

	// whether its a 1D, 2D, or 3D texture
	int texDims();

	MinFilter texMinFilter();

	MagFilter texMagFilter();

	Wrap texWrap();
}
