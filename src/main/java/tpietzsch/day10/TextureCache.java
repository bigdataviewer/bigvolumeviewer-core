package tpietzsch.day10;

import com.jogamp.opengl.GL3;
import java.nio.ByteBuffer;

import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_R16F;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;

public class TextureCache
{
	private final int[] blockSize;

	private final int[] gridSize;

	private final int[] cacheSize;

	private int texture;

	public TextureCache( final int[] blockSize, final int[] gridSize )
	{
		this.blockSize = blockSize.clone();
		this.gridSize = gridSize.clone();

		cacheSize = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			cacheSize[ d ] = gridSize[ d ] * blockSize[ d ];
	}

	public void putBlockData( GL3 gl, LRUBlockCache.TextureBlock textureBlock, final ByteBuffer data )
	{
		init( gl );

		final int[] pos = textureBlock.getPos();
		final int x = pos[ 0 ];
		final int y = pos[ 1 ];
		final int z = pos[ 2 ];
		final int w = blockSize[ 0 ];
		final int h = blockSize[ 1 ];
		final int d = blockSize[ 2 ];

		gl.glBindTexture( GL_TEXTURE_3D, texture );
		gl.glTexSubImage3D( GL_TEXTURE_3D, 0, x, y, z, w, h, d, GL_RED, GL_UNSIGNED_SHORT, data );
	}

	public void bindTextures( GL3 gl, int textureUnit )
	{
		gl.glActiveTexture( textureUnit );
		gl.glBindTexture( GL_TEXTURE_3D, texture );
	}

	public int[] getBlockSize()
	{
		return blockSize;
	}

	public int[] getGridSize()
	{
		return gridSize;
	}

	public int[] getSize()
	{
		return cacheSize;
	}

	private boolean textureInitialized = false;

	private void init( GL3 gl )
	{
		if ( textureInitialized )
			return;
		textureInitialized = true;

		final int[] tmp = new int[ 1 ];
		gl.glGenTextures( 1, tmp, 0 );
		texture = tmp[ 0 ];
		gl.glBindTexture( GL_TEXTURE_3D, texture );

		final int w = gridSize[ 0 ] * blockSize[ 0 ];
		final int h = gridSize[ 1 ] * blockSize[ 1 ];
		final int d = gridSize[ 2 ] * blockSize[ 2 ];

		gl.glTexStorage3D( GL_TEXTURE_3D, 1, GL_R16F, w, h, d );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
	}
}
