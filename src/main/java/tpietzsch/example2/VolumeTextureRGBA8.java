package tpietzsch.example2;

import java.nio.Buffer;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture3D;

import static tpietzsch.backend.Texture.InternalFormat.RGBA8;

public class VolumeTextureRGBA8 implements Texture3D
{
	private final int[] size = new int[ 3 ];

	/**
	 * Reinitialize.
	 */
	public void init( final int[] size )
	{
		for ( int d = 0; d < 3; d++ )
			this.size[ d ] = size[ d ];
	}

	public void upload( final GpuContext context, final Buffer data )
	{
		context.delete( this ); // TODO: is this necessary everytime?
		context.texSubImage3D( this, 0, 0, 0, texWidth(), texHeight(), texDepth(), data );
	}

	@Override
	public InternalFormat texInternalFormat()
	{
		return RGBA8;
	}

	@Override
	public int texWidth()
	{
		return size[ 0 ];
	}

	@Override
	public int texHeight()
	{
		return size[ 1 ];
	}

	@Override
	public int texDepth()
	{
		return size[ 2 ];
	}

	@Override
	public MinFilter texMinFilter()
	{
		return MinFilter.LINEAR;
	}

	@Override
	public MagFilter texMagFilter()
	{
		return MagFilter.LINEAR;
	}

	@Override
	public Wrap texWrap()
	{
		return Wrap.CLAMP_TO_BORDER_ZERO;
	}
}
