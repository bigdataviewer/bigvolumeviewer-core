package tpietzsch.scenery.example0;

import java.util.Arrays;
import tpietzsch.backend.GpuContext;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;

/**
 * Needs only {@code GpuContext.use(Shader)}
 */
public class RenderStuff
{
	private final DefaultShader prog;

	public RenderStuff()
	{
		final Segment ex1vp = new SegmentTemplate( RenderStuff.class, "render.vp", Arrays.asList() ).instantiate();
		final Segment ex1fp = new SegmentTemplate( RenderStuff.class, "render.fp", Arrays.asList() ).instantiate();
		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );
	}

	public void draw( GpuContext context, Runnable drawQuad )
	{
		prog.use( context );
		drawQuad.run();
	}
}
