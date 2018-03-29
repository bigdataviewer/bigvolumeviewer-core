package tpietzsch.day2;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;

import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;

public class Shader
{
	private final String vpName;

	private final String fpName;

	private final ShaderProgram prog;

	public Shader( final GL2ES2 gl, final String vpName, final String fpName )
	{
		this( gl, vpName, fpName, tryGetContext() );
	}

	private static Class<?> tryGetContext()
	{
		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		try
		{
			final Class< ? > klass = Shader.class.getClassLoader().loadClass( stackTrace[ 3 ].getClassName() );
			System.out.println( "klass = " + klass );
			return klass;
		}
		catch ( ClassNotFoundException e )
		{
			throw new RuntimeException( e );
		}
	}

	public Shader( final GL2ES2 gl, final String vpName, final String fpName, final Class<?> context )
	{
		this.vpName = vpName;
		this.fpName = fpName;

		ShaderCode vs = ShaderCode.create( gl, GL_VERTEX_SHADER, context, "", null, vpName, true );
		ShaderCode fs = ShaderCode.create( gl, GL_FRAGMENT_SHADER, context, "", null, fpName, true );
		vs.defaultShaderCustomization( gl, true, false );
		fs.defaultShaderCustomization( gl, true, false );

		prog = new ShaderProgram();
		prog.add( vs );
		prog.add( fs );
		prog.link( gl, System.err );
		vs.destroy( gl );
		fs.destroy( gl );
	}

	public void use( final GL2ES2 gl )
	{
		gl.glUseProgram( prog.program() );
	}

	public void setUniform( final GL2ES2 gl, String name, boolean value )
	{
		gl.glProgramUniform1i( prog.program(), gl.glGetUniformLocation( prog.program(), name ), value ? 1 : 0 );
	}

	public void setUniform( final GL2ES2 gl, String name, int value )
	{
		gl.glProgramUniform1i( prog.program(), gl.glGetUniformLocation( prog.program(), name ), value );
	}

	public void setUniform( final GL2ES2 gl, String name, float value )
	{
		gl.glProgramUniform1f( prog.program(), gl.glGetUniformLocation( prog.program(), name ), value );
	}

	public void setUniform( final GL2ES2 gl, String name, float v0, float v1, float v2, float v3 )
	{
		gl.glProgramUniform4f( prog.program(), gl.glGetUniformLocation( prog.program(), name ), v0, v1, v2, v3 );
	}
}
