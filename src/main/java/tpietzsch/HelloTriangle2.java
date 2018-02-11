package tpietzsch;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.FloatBuffer;

import javax.swing.JFrame;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;

import tpietzsch.util.GuiUtil;
import tpietzsch.util.PainterThread;

public class HelloTriangle2 extends GLCanvas implements GLEventListener, PainterThread.Paintable
{
	private static final long serialVersionUID = 1L;

	private static final String vsrc =
			"#version 330\n" +
			"\n" +
			"layout(location = 0) in vec4 position;\n" +
			"void main()\n" +
			"{\n" +
			"    gl_Position = position;\n" +
			"}";

	private static final String fsrc = "#version 330\n" +
			"\n" +
			"out vec4 outputColor;\n" +
			"void main()\n" +
			"{\n" +
			"   outputColor = vec4(1.0f, 1.0f, 1.0f, 1.0f);\n" +
			"}";

	private static ShaderProgram buildShaders( final GL2ES2 gl )
	{
		final ShaderCode vs = new ShaderCode( GL2ES2.GL_VERTEX_SHADER, 1, new String[][] { { vsrc } } );
		final ShaderCode fs = new ShaderCode( GL2ES2.GL_FRAGMENT_SHADER, 1, new String[][] { { fsrc } } );
		final ShaderProgram prog = new ShaderProgram();
		prog.add( vs );
		prog.add( fs );
		prog.link( gl, System.err );
		vs.destroy( gl );
		fs.destroy( gl );
		return prog;
	}

	private static final float[] vertexPositions = new float[] {
		0.75f, 0.75f, 0.0f, 1.0f,
		0.75f, -0.75f, 0.0f, 1.0f,
		-0.75f, -0.75f, 0.0f, 1.0f };

	private static int initializeVertexBuffer( final GL gl )
	{
		final int[] tmp = new int[ 1 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int positionBufferObject = tmp[ 0 ];

		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, positionBufferObject );
		{
//			final FloatBuffer buffer = GLBuffers.newDirectFloatBuffer( vertexPositions );
			final FloatBuffer buffer = FloatBuffer.wrap( vertexPositions );
			gl.glBufferData( GL.GL_ARRAY_BUFFER, vertexPositions.length * 4, buffer, GL.GL_STATIC_DRAW );
		}
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		return positionBufferObject;
	}

	public static void main( final String[] args )
	{
		final HelloTriangle2 canvas = new HelloTriangle2();
		canvas.setPreferredSize( new Dimension( 640, 480 ) );

		final PainterThread painterThread = new PainterThread( canvas );

//		final GraphicsConfiguration gc = GuiUtil.getSuitableGraphicsConfiguration( GuiUtil.ARGB_COLOR_MODEL );
		final GraphicsConfiguration gc = GuiUtil.getSuitableGraphicsConfiguration( GuiUtil.RGB_COLOR_MODEL );
		final JFrame frame = new JFrame( "Hello Triangle 2", gc );
		frame.getRootPane().setDoubleBuffered( true );
		frame.getContentPane().add( canvas, BorderLayout.CENTER );
		frame.pack();
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				System.out.println( "stopping painterThread..." );
				painterThread.interrupt();
			}
		} );

		frame.setVisible( true );
		painterThread.start();
	}

	private ShaderProgram prog;

	private int vb;

	private int vao;

	public HelloTriangle2()
	{
		super( new GLCapabilities( GLProfile.getGL2GL3() ) );
		addGLEventListener( this );
	}

	@Override
	public void paint()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		prog = buildShaders( gl );
		vb = initializeVertexBuffer( gl );

		final int[] tmp = new int[1];
		gl.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl.glBindVertexArray( vao );
		{
			gl.glBindBuffer( GL3.GL_ARRAY_BUFFER, vb );
			gl.glEnableVertexAttribArray( 0 );
			gl.glVertexAttribPointer( 0, 4, GL3.GL_FLOAT, false, 0, 0 );
		}
		gl.glBindVertexArray( 0 );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glClearColor( 0.0f, 0.0f, 0.0f, 1.0f );
		gl.glClear( GL3.GL_COLOR_BUFFER_BIT );

		prog.useProgram( gl, true );
		{
			gl.glBindVertexArray( vao );
			gl.glDrawArrays( GL3.GL_TRIANGLES, 0, 3 );
		}
		prog.useProgram( gl, false );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glViewport( 0, 0, width, height );
	}

}
