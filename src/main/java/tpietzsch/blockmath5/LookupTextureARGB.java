package tpietzsch.blockmath5;

import com.jogamp.opengl.GL3;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.jogamp.opengl.GL.GL_BYTE;
import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_RGB32F;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
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
		gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, w, h, d, GL_RGBA_INTEGER, GL_BYTE, ByteBuffer.wrap( lut ) );
	}
}
