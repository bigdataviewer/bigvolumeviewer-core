package tpietzsch.example2;

import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.joml.Vector3f;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture3D;
import tpietzsch.multires.BufferedSimpleStack3D;
import tpietzsch.multires.SimpleStack3D;

// TODO move to scenery package
public class ScenerySimpleStackManager extends DefaultSimpleStackManager
{
	@Override
	public synchronized SimpleVolume getSimpleVolume( final GpuContext context, final SimpleStack3D< ? > simpleStack )
	{
		if ( ! ( simpleStack instanceof BufferedSimpleStack3D ) )
			return super.getSimpleVolume( context, simpleStack );


		final BufferedSimpleStack3D< ? > stack = ( BufferedSimpleStack3D< ? > ) simpleStack;
		final Object type = stack.getType();

		final int dim[] = stack.getDimensions();
		final Vector3f sourceMin = new Vector3f();
		final Vector3f sourceMax = new Vector3f( dim[ 0 ], dim[ 1 ], dim[ 2 ] );

		final Texture3D texture;
		if ( type instanceof UnsignedByteType )
		{
			texture = texturesU8.computeIfAbsent( stack, s -> {
				final VolumeTextureU8 tex = new VolumeTextureU8();
				tex.init( dim );
				tex.upload( context, stack.getBuffer() );
				return tex;
			} );
		}
		else if ( type instanceof UnsignedShortType )
		{
			texture = texturesU16.computeIfAbsent( stack, s -> {
				final VolumeTextureU16 tex = new VolumeTextureU16();
				tex.init( dim );
				tex.upload( context, stack.getBuffer() );
				return tex;
			} );
		}
		else
			throw new IllegalArgumentException( "BufferedSimpleStack3D of type " + stack.getType() + " are not supported." );

		timestamps.put( texture, currentTimestamp );
		return new SimpleVolume( texture, stack.getSourceTransform(), sourceMin, sourceMax );
	}
}
