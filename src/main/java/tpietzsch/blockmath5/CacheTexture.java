package tpietzsch.blockmath5;

import com.jogamp.opengl.GL3;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import tpietzsch.blockmath3.TextureBlock;
import tpietzsch.blocks.ByteUtils;

import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL.GL_WRITE_ONLY;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_STREAM_DRAW;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_WRAP_R;
import static com.jogamp.opengl.GL2ES3.GL_PIXEL_UNPACK_BUFFER;
import static com.jogamp.opengl.GL2GL3.GL_R16;
import static tpietzsch.blocks.ByteUtils.addressOf;

import com.jogamp.opengl.GL3;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import tpietzsch.blockmath3.TextureBlock;
import tpietzsch.blocks.CopySubArrayImp;

public class CacheTexture
{
	private final int[] blockSize;

	private final int[] gridSize;

	private final int[] cacheSize;

	private final int numBytesInBlock;

	private int texture;

	private final int numPbos = 500;

	private final int pbo[] = new int[ numPbos ];

	private int pboi = 0;

	public CacheTexture( final int[] blockSize, final int[] gridSize )
	{
		this.blockSize = blockSize.clone();
		this.gridSize = gridSize.clone();

		cacheSize = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			cacheSize[ d ] = gridSize[ d ] * blockSize[ d ];

		numBytesInBlock = blockSize[ 0 ] * blockSize[ 1 ] * blockSize[ 2 ] * 2;
	}

	public static class UploadBuffer implements CopySubArrayImp.Address
	{
		private final Buffer buffer;

		private final int offset;

		private final int pbo;

		public UploadBuffer( final Buffer buffer, final int offset, final int pbo )
		{
			this.buffer = buffer;
			this.offset = offset;
			this.pbo = pbo;
		}

		Buffer getBuffer()
		{
			return buffer;
		}

		public int getOffset()
		{
			return offset;
		}

		@Override
		public long getAddress()
		{
			return addressOf( buffer ) + offset;
		}
	}

	public UploadBuffer getNextBuffer( final GL3 gl )
	{
		init( gl );

		final int pbo = this.pbo[ pboi ];
		pboi = ( pboi + 1 ) % numPbos;

		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo );
		gl.glBufferData( GL_PIXEL_UNPACK_BUFFER, numBytesInBlock, null, GL_STREAM_DRAW );
		final ByteBuffer buffer = gl.glMapBuffer( GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY );
		gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, 0 );

		return new UploadBuffer( buffer, 0, pbo );
	}


	public void putBlockData( final GL3 gl, final TextureBlock textureBlock, final UploadBuffer data )
	{
		init( gl );

		final int[] pos = textureBlock.getPos();
		final int x = pos[ 0 ];
		final int y = pos[ 1 ];
		final int z = pos[ 2 ];
		final int w = blockSize[ 0 ];
		final int h = blockSize[ 1 ];
		final int d = blockSize[ 2 ];

//		gl.glBindTexture( GL_TEXTURE_3D, texture );
//		gl.glTexSubImage3D( GL_TEXTURE_3D, 0, x, y, z, w, h, d, GL_RED, GL_UNSIGNED_SHORT, data );

		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, data.pbo );
		gl.glUnmapBuffer( GL_PIXEL_UNPACK_BUFFER );
		gl.glBindTexture( GL_TEXTURE_3D, texture );
		gl.glTexSubImage3D( GL_TEXTURE_3D, 0, x, y, z, w, h, d, GL_RED, GL_UNSIGNED_SHORT, 0 );
		gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, 0 );
	}

	public void bindTextures( final GL3 gl, final int textureUnit )
	{
		init( gl );

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

	private void init( final GL3 gl )
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

		gl.glTexStorage3D( GL_TEXTURE_3D, 1, GL_R16, w, h, d );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE );

		// PBO

		final int[] tmpPbo = new int[ numPbos ];
		gl.glGenBuffers( numPbos, tmpPbo, 0 );
		for ( int i = 0; i < numPbos; i++ )
		{
			pbo[ i ] = tmpPbo[ i ];
			gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, pbo[ i ] );
			gl.glBufferData( GL_PIXEL_UNPACK_BUFFER, numBytesInBlock, null, GL_STREAM_DRAW );
			gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, 0 );
		}
	}
}
