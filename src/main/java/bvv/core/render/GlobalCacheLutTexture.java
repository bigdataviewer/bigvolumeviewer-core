package bvv.core.render;

import bvv.core.backend.GpuContext;
import bvv.core.backend.Texture3D;
import static bvv.core.backend.Texture.InternalFormat.RGBA8UI;

import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

public class GlobalCacheLutTexture implements Texture3D
{
	private final int[] size = new int[ 3 ];
	
	private final List<Integer> zOffsetsPerVolume;
	private final List<int []> lutsSizes;
	private final List<ByteBuffer> uploadData;
	
	public GlobalCacheLutTexture()
	{
		zOffsetsPerVolume = new ArrayList<>();
		uploadData = new ArrayList<>();
		lutsSizes = new ArrayList<>();
	}

	public void init( final GpuContext context )
	{
		context.delete( this );
		zOffsetsPerVolume.clear();
		uploadData.clear();
		lutsSizes.clear();
		size[0] = 0;
		size[1] = 0;
		size[2] = 0;
	}

	public void addVolumeLUT(final int[] sizeLut, final ByteBuffer data)
	{
		final int zOffset = this.size[2];
		zOffsetsPerVolume.add( zOffset );
		uploadData.add( data );
		lutsSizes.add( new int[] {sizeLut[0], sizeLut[1], sizeLut[2]} );
		size[2] += sizeLut[2];
		size[0] = Math.max(  sizeLut[0], size[0]);
		size[1] = Math.max(  sizeLut[1], size[1]);	
	}
	
	public void upload(final GpuContext context)
	{
		for(int i = 0; i < uploadData.size(); i++)
		{
			final int [] lutSize = lutsSizes.get( i );
			final ByteBuffer buffer = uploadData.get( i );
			buffer.position( 0 );
			//set the row and depth image strides of the source buffer			
	        context.glPixelStorei( GL2ES2.GL_UNPACK_ROW_LENGTH, lutSize[ 0 ] );
	        context.glPixelStorei( GL2ES3.GL_UNPACK_IMAGE_HEIGHT, lutSize[ 1 ] );
	        
			context.texSubImage3D(this,	0, 0, zOffsetsPerVolume.get( i ), lutSize[ 0 ], lutSize[ 1 ], lutSize[ 2 ], uploadData.get( i )
			);
		}
		// Reset pixel store state back to defaults (0 = tightly packed)
	    context.glPixelStorei( GL2ES2.GL_UNPACK_ROW_LENGTH, 0 );
	    context.glPixelStorei( GL2ES3.GL_UNPACK_IMAGE_HEIGHT, 0 );
	}
	
	public int getGlobalCacheLutZOffset( final int nVolumeIndex )
	{
		return zOffsetsPerVolume.get( nVolumeIndex );
	}

	public Vector3f getSize3f()
	{
		return new Vector3f( size[ 0 ], size[ 1 ], size[ 2 ] );
	}

	// --- Texture3D Implementation ---

	@Override
	public InternalFormat texInternalFormat()
	{
		return RGBA8UI;
	}

	@Override
	public int texWidth()
	{
		return size[0];
	}

	@Override
	public int texHeight()
	{
		return size[1];
	}

	@Override
	public int texDepth()
	{
		return size[2];
	}

	@Override
	public MinFilter texMinFilter()
	{
		return MinFilter.NEAREST;
	}

	@Override
	public MagFilter texMagFilter()
	{
		return MagFilter.NEAREST;
	}

	@Override
	public Wrap texWrap()
	{
		return Wrap.CLAMP_TO_EDGE;
	}
}