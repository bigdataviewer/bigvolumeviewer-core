package tpietzsch.scenery.example1;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture;
import tpietzsch.backend.Texture3D;
import tpietzsch.scene.TexturedUnitCube;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.Shader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.util.Images;

/**
 * Needs
 * 	{@link GpuContext#use(Shader)}
 * 	{@link GpuContext#getUniformSetter(Shader)}
 *  {@link GpuContext#texSubImage3D(Texture3D, int, int, int, int, int, int, Buffer)}
 *  {@link GpuContext#bindTexture(Texture)}
 */
public class RenderStuff
{
	private final DefaultShader prog;

	private final byte[] textureData;

	private final Texture3D texture;

	public RenderStuff() throws IOException
	{
		final Segment ex1vp = new SegmentTemplate( RenderStuff.class, "render.vp", Arrays.asList() ).instantiate();
		final Segment ex1fp = new SegmentTemplate( RenderStuff.class, "render.fp", Arrays.asList() ).instantiate();
		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );

		textureData = Images.loadBytesRGBA( TexturedUnitCube.class.getResourceAsStream( "scijava.png" ) );

		texture = new Texture3D()
		{
			@Override
			public InternalFormat texInternalFormat()
			{
				return InternalFormat.RGBA8UI;
			}

			@Override
			public int texWidth()
			{
				return 512;
			}

			@Override
			public int texHeight()
			{
				return 512;
			}

			@Override
			public int texDepth()
			{
				return 1;
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
		};

		prog.getUniformSampler( "texture1" ).set( texture );
	}

	private boolean textureLoaded = false;

	public void draw( final GpuContext context, final Runnable drawQuad )
	{
		if ( !textureLoaded )
		{
			context.texSubImage3D( texture, 0, 0, 0, 512, 512, 1, ByteBuffer.wrap( textureData ) );
			textureLoaded = true;
		}

		prog.use( context );
		prog.bindSamplers( context );
		prog.setUniforms( context );
		drawQuad.run();
	}
}
