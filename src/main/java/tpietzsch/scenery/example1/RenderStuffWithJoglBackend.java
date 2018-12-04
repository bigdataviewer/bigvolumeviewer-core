package tpietzsch.scenery.example1;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.io.IOException;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.contextsharing.SimpleFrame;
import tpietzsch.util.DefaultQuad;

public class RenderStuffWithJoglBackend implements GLEventListener
{
	public static void main( String[] args ) throws IOException
	{
		new SimpleFrame( "RenderStuffWithJoglBackend", 640, 480, new RenderStuffWithJoglBackend() );
	}

	private final RenderStuff renderStuff;

	private final DefaultQuad quad;

	public RenderStuffWithJoglBackend() throws IOException
	{
		this.renderStuff = new RenderStuff();
		this.quad = new DefaultQuad();
	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		GpuContext context = JoglGpuContext.get( gl );
		renderStuff.draw( context, () -> quad.draw( gl ) );
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
	}
}
