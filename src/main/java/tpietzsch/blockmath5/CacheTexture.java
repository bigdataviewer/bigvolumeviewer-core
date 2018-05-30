package tpietzsch.blockmath5;

import com.jogamp.opengl.GL3;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import tpietzsch.blockmath3.TextureBlock;

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

import tpietzsch.blocks.CopySubArrayImp;

public class CacheTexture
{
	private final int[] blockSize;

	private final int[] gridSize;

	private final int[] cacheSize;

	private final int numBytesInBlock;

	private int texture;

	private final int numPbos = 5;

	private final int blocksPerPbo = 200;

	private final List< PboBlockBatch > batches = new ArrayList<>();

	public CacheTexture( final int[] blockSize, final int[] gridSize )
	{
		this.blockSize = blockSize.clone();
		this.gridSize = gridSize.clone();
		System.out.println( "gridSize = " + Arrays.toString( gridSize ) );

		cacheSize = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			cacheSize[ d ] = gridSize[ d ] * blockSize[ d ];

		numBytesInBlock = blockSize[ 0 ] * blockSize[ 1 ] * blockSize[ 2 ] * 2;
	}

	public static class UploadBuffer implements CopySubArrayImp.Address
	{
		private final Buffer buffer;

		private final int offset;

		private final PboBlockBatch batch;

		public UploadBuffer( final Buffer buffer, final int offset, final PboBlockBatch batch )
		{
			this.buffer = buffer;
			this.offset = offset;
			this.batch = batch;
		}

		@Override
		public long getAddress()
		{
			return addressOf( buffer ) + offset;
		}
	}

	static class UploadTask
	{
		final int[] pos;

		final long offset;

		public UploadTask( final int[] pos, final long offset )
		{
			this.pos = pos;
			this.offset = offset;
		}
	}

	enum State
	{
		CLEAN,
		MAPPED,
		UNMAPPED
	};

	static class PboBlockBatch
	{
		private final int pbo;

		private final int[] blockSize;

		private final int numBytesInBlock;

		private final int numBlocks;

		private final List< UploadTask > tasks = new ArrayList<>();

		private ByteBuffer buffer;

		private State state;

		private int nextIndex;

		private int outstanding;

		PboBlockBatch( final int pbo, final int[] blockSize, final int numBytesInBlock, final int numBlocks )
		{
			this.pbo = pbo;
			this.blockSize = blockSize;
			this.numBytesInBlock = numBytesInBlock;
			this.numBlocks = numBlocks;
			this.state = State.CLEAN;
		}

		boolean hasNext()
		{
			return nextIndex < numBlocks;
		}

		UploadBuffer takeBlock()
		{
			if ( state != State.MAPPED )
				throw new IllegalStateException();

			if ( !hasNext() )
				throw new NoSuchElementException();

			final UploadBuffer b = new UploadBuffer( buffer, nextIndex * numBytesInBlock, this );
			++outstanding;
			++nextIndex;
			return b;
		}

		void finishBlock( UploadBuffer buffer, TextureBlock pos )
		{
			--outstanding;
			tasks.add( new UploadTask( pos.getPos().clone(), buffer.offset ) );
		}

		boolean hasUnfinishedBlocks()
		{
			return outstanding > 0;
		}

		void flush( final GL3 gl )
		{
			if ( state == State.MAPPED )
				unmap( gl );
			if ( state == State.UNMAPPED )
				upload( gl );
		}

		boolean init = true;

		void map( final GL3 gl )
		{
			if ( state != State.CLEAN )
				throw new IllegalStateException();

			gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, pbo );
			if ( init )
			{
				gl.glBufferData( GL_PIXEL_UNPACK_BUFFER, numBytesInBlock * numBlocks, null, GL_STREAM_DRAW );
				init = false;
			}
			buffer = gl.glMapBuffer( GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY );
			gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, 0 );

			state = State.MAPPED;
			nextIndex = 0;
			outstanding = 0;
		}

		void unmap( final GL3 gl )
		{
			if ( state != State.MAPPED || hasUnfinishedBlocks() )
				throw new IllegalStateException();
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo );
			gl.glUnmapBuffer( GL_PIXEL_UNPACK_BUFFER );
			gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, 0 );

			state = State.UNMAPPED;
		}

		void upload( final GL3 gl )
		{
			if ( state != State.UNMAPPED )
				throw new IllegalStateException();

			final long t0 = System.currentTimeMillis();
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo );
			for ( UploadTask task : tasks )
			{
				final int x = task.pos[ 0 ];
				final int y = task.pos[ 1 ];
				final int z = task.pos[ 2 ];
				final int w = blockSize[ 0 ];
				final int h = blockSize[ 1 ];
				final int d = blockSize[ 2 ];
				gl.glTexSubImage3D( GL_TEXTURE_3D, 0, x, y, z, w, h, d, GL_RED, GL_UNSIGNED_SHORT, task.offset );
			}
//			gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, 340, 340, 34, GL_RED, GL_UNSIGNED_SHORT, 0 );
//			gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 34, 0, 340, 340, 34, GL_RED, GL_UNSIGNED_SHORT, 340 * 340 * 34 * 2 );
			gl.glBindBuffer( GL_PIXEL_UNPACK_BUFFER, 0 );
			final long t1 = System.currentTimeMillis();
			System.out.println( "upload: " + ( t1 - t0 ) + "ms" );

			tasks.clear();
			state = State.CLEAN;
			nextIndex = numBlocks;
		}
	}

	private int pboi = 0;

	public UploadBuffer getNextBuffer( final GL3 gl )
	{
		init( gl );

		PboBlockBatch batch = batches.get( pboi );
		if ( !batch.hasNext() )
		{
			pboi = ( pboi + 1 ) % numPbos;
			batch = batches.get( pboi );
			batch.map( gl );
		}
		return batch.takeBlock();
	}

	public void putBlockData( final GL3 gl, final TextureBlock textureBlock, final UploadBuffer data )
	{
		final PboBlockBatch batch = data.batch;
		batch.finishBlock( data, textureBlock );
		if ( !batch.hasNext() && !batch.hasUnfinishedBlocks() )
		{
			batch.unmap( gl );
			gl.glBindTexture( GL_TEXTURE_3D, texture );
			batch.upload( gl );
		}
	}

	public void flush( final GL3 gl )
	{
		init( gl );

		PboBlockBatch batch = batches.get( pboi );
		gl.glBindTexture( GL_TEXTURE_3D, texture );
		batch.flush( gl );

		pboi = ( pboi + 1 ) % numPbos;
		batch = batches.get( pboi );
		batch.map( gl );
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

		// PBOs for texture upload batches
		final int[] pbos = new int[ numPbos ];
		gl.glGenBuffers( numPbos, pbos, 0 );
		for ( int i = 0; i < numPbos; i++ )
			batches.add( new PboBlockBatch( pbos[ i ], blockSize, numBytesInBlock, blocksPerPbo ) );

		batches.get( pboi ).map( gl );
	}
}
