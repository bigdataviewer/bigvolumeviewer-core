package tpietzsch.scenery.example0;

import java.util.Arrays;
import tpietzsch.backend.GpuContext;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.Shader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;

/**
 * Needs
 * 	{@link GpuContext#use(Shader)}
 * 	{@link GpuContext#getUniformSetter(Shader)}
 *
 * And
 *
 */
public class RenderStuff2
{
	private final DefaultShader prog;

	public RenderStuff2()
	{
		final Segment ex1vp = new SegmentTemplate( RenderStuff2.class, "render.vp", Arrays.asList() ).instantiate();
		final Segment ex1fp = new SegmentTemplate( RenderStuff2.class, "render.fp", Arrays.asList() ).instantiate();
		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );
		prog.getUniform4f( "color1" ).set( 1.0f, 0.0f, 1.0f, 1.0f );
		prog.getUniform4f( "color2" ).set( 0.0f, 1.0f, 0.0f, 1.0f );
		prog.getUniform4f( "color3" ).set( 0.2f, 0.2f, 0.2f, 1.0f );
	}

	public void draw( GpuContext context, Runnable drawQuad )
	{
		prog.use( context );
		prog.setUniforms( context );
		drawQuad.run();
	}
}
