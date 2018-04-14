package tpietzsch.day3;

//https://learnopengl.com/Getting-started/Coordinate-Systems

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_RGBA;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE1;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.joml.Matrix4f;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import tpietzsch.day1.SimpleFrame;
import tpietzsch.day2.Shader;
import tpietzsch.util.Images;

/**
 * Textured rectangle with <em>projection * view * model</em> in vertex shader
 */
public class Example1 implements GLEventListener
{
	private int vao;

	private Shader prog;

	private int texture1;

	private int texture2;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		final float vertices[] = {
				 // positions         // texture coords
				 0.5f,  0.5f, 0.0f,   1.0f, 1.0f,         // top right
				 0.5f, -0.5f, 0.0f,   1.0f, 0.0f,         // bottom right
				-0.5f, -0.5f, 0.0f,   0.0f, 0.0f,         // bottom left
				-0.5f,  0.5f, 0.0f,   0.0f, 1.0f          // top left
		};
		final int[] tmp = new int[ 2 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		// ..:: ELEMENT BUFFER ::..

		final int indices[] = {  // note that we start from 0!
				0, 1, 3,   // first triangle
				1, 2, 3    // second triangle
		};
		gl.glGenBuffers( 1, tmp, 0 );
		final int ebo = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );

		// ..:: TEXTURES ::..

		gl.glGenTextures( 2, tmp, 0 );
		texture1 = tmp[ 0 ];
		texture2 = tmp[ 1 ];
		byte[] data = null;
		try
		{
			data = Images.loadBytesRGB( Example1.class.getResourceAsStream( "container.jpg" ) );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		gl.glBindTexture( GL_TEXTURE_2D, texture1 );
		gl.glTexImage2D( GL_TEXTURE_2D, 0, GL_RGB, 512, 512, 0, GL_RGB, GL_UNSIGNED_BYTE, ByteBuffer.wrap( data ) );
		gl.glGenerateMipmap( GL_TEXTURE_2D );
		try
		{
			data = Images.loadBytesRGBA( Example1.class.getResourceAsStream( "awesomeface.png" ), true );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		gl.glBindTexture( GL_TEXTURE_2D, texture2 );
		gl.glTexImage2D( GL_TEXTURE_2D, 0, GL_RGBA, 512, 512, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap( data ) );
		gl.glGenerateMipmap( GL_TEXTURE_2D );

		// ..:: SHADERS ::..

		prog = new Shader( gl3, "ex1", "ex1" );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl3.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl3.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0 );
		gl3.glEnableVertexAttribArray( 0 );
		gl3.glVertexAttribPointer( 1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES );
		gl3.glEnableVertexAttribArray( 1 );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl3.glBindVertexArray( 0 );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{

	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT );


		final Matrix4f model = new Matrix4f();
		model.rotate( (float) Math.toRadians( -55.0 ), 1.0f, 0.0f, 0.0f );

		final Matrix4f view = new Matrix4f();
		view.translate( 0.0f, 0.0f, -3.0f );

		final Matrix4f projection = new Matrix4f();
		projection.perspective( (float) Math.toRadians( 45.0 ), width / height, 0.1f, 100.0f );

		prog.use( gl3 );
		prog.setUniform( gl3, "texture1", 0 );
		prog.setUniform( gl3, "texture2", 1 );
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "view", view );
		prog.setUniform( gl3, "projection", projection );
		gl3.glActiveTexture( GL_TEXTURE0 );
		gl3.glBindTexture( GL_TEXTURE_2D, texture1 );
		gl3.glActiveTexture( GL_TEXTURE1 );
		gl3.glBindTexture( GL_TEXTURE_2D, texture2 );
		gl3.glBindVertexArray( vao );
		gl3.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
		gl3.glBindVertexArray( 0 );
	}

	int width = 1;
	int height = 1;

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}

	public static void main( final String[] args )
	{
		new SimpleFrame( "Example1", 640, 480, new Example1() );
	}
}
