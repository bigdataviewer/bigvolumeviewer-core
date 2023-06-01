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
package bvv.core.blockmath;

import com.jogamp.opengl.GL3;
import java.nio.ByteBuffer;

import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_WRAP_R;
import static com.jogamp.opengl.GL2ES3.GL_RGBA8UI;
import static com.jogamp.opengl.GL2ES3.GL_RGBA_INTEGER;

public class LookupTextureARGB
{
	private final int[] size;

	private int texture;

	private boolean textureInitialized = false;

	public LookupTextureARGB( final int[] size )
	{
		this.size = size.clone();
	}

	public void bindTextures( final GL3 gl, final int textureUnit )
	{
		gl.glActiveTexture( textureUnit );
		gl.glBindTexture( GL_TEXTURE_3D, texture );
	}

	public int[] getSize()
	{
		return size;
	}

	public void resize( final GL3 gl, final int[] size )
	{
		this.size[ 0 ] = size[ 0 ];
		this.size[ 1 ] = size[ 1 ];
		this.size[ 2 ] = size[ 2 ];

		final int w = size[ 0 ];
		final int h = size[ 1 ];
		final int d = size[ 2 ];

		final int[] tmp = new int[] { texture };
		if ( textureInitialized )
			gl.glDeleteTextures( 1, tmp, 0 );
		else
			textureInitialized = true;
		gl.glGenTextures( 1, tmp, 0 );
		texture = tmp[ 0 ];

		gl.glBindTexture( GL_TEXTURE_3D, texture );
		gl.glTexStorage3D( GL_TEXTURE_3D, 1, GL_RGBA8UI, w, h, d );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE );
	}

	public void set( final GL3 gl, final byte[] lut )
	{
		final int w = size[ 0 ];
		final int h = size[ 1 ];
		final int d = size[ 2 ];

		gl.glBindTexture( GL_TEXTURE_3D, texture );
		gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, w, h, d, GL_RGBA_INTEGER, GL_UNSIGNED_BYTE, ByteBuffer.wrap( lut ) );
	}
}
