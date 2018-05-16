package tpietzsch.blockmath2;

import bdv.volume.RequiredBlocks;
import com.jogamp.opengl.GL3;
import java.nio.FloatBuffer;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_RGB32F;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;

public class LookupTexture
{
	private final int[] size;

	private final int internalFormat;

	private int textureOffset;

	private int textureScale;

	private boolean textureInitialized = false;

	public LookupTexture( final int[] size )
	{
		this( size, GL_RGB32F );
	}

	public LookupTexture( final int[] size, int internalFormat )
	{
		this.size = size.clone();
		this.internalFormat = internalFormat;
	}

	public void bindTextures( GL3 gl, int scaleTextureUnit, int offsetTextureUnit )
	{
		gl.glActiveTexture( scaleTextureUnit );
		gl.glBindTexture( GL_TEXTURE_3D, textureScale );
		gl.glActiveTexture( offsetTextureUnit );
		gl.glBindTexture( GL_TEXTURE_3D, textureOffset );
	}

	public int[] getSize()
	{
		return size;
	}

	public void resize( GL3 gl, final int[] size )
	{
		this.size[ 0 ] = size[ 0 ];
		this.size[ 1 ] = size[ 1 ];
		this.size[ 2 ] = size[ 2 ];

		final int w = size[ 0 ];
		final int h = size[ 1 ];
		final int d = size[ 2 ];

		final int[] tmp = new int[] { textureOffset, textureScale };
		if ( textureInitialized )
			gl.glDeleteTextures( 2, tmp, 0 );
		else
			textureInitialized = true;
		gl.glGenTextures( 2, tmp, 0 );
		textureOffset = tmp[ 0 ];
		textureScale = tmp[ 1 ];

		gl.glBindTexture( GL_TEXTURE_3D, textureOffset );
		gl.glTexStorage3D( GL_TEXTURE_3D, 1, internalFormat, w, h, d );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE );

		gl.glBindTexture( GL_TEXTURE_3D, textureScale );
		gl.glTexStorage3D( GL_TEXTURE_3D, 1, internalFormat, w, h, d );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE );
	}

	public void set( GL3 gl, final float[] scale, final float[] offset )
	{
		final int w = size[ 0 ];
		final int h = size[ 1 ];
		final int d = size[ 2 ];

		gl.glBindTexture( GL_TEXTURE_3D, textureScale );
		gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, w, h, d, GL_RGB, GL_FLOAT, FloatBuffer.wrap( scale ) );

		gl.glBindTexture( GL_TEXTURE_3D, textureOffset );
		gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, w, h, d, GL_RGB, GL_FLOAT, FloatBuffer.wrap( offset ) );
	}
}
