package tpietzsch.backend.jogl;

import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;

import java.util.HashMap;
import java.util.Map;

import tpietzsch.shadergen.Shader;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.SetUniforms;

public class JoglGpuContext implements GpuContext
{
	@Override
	public void use( final Shader shader )
	{
		final ShaderProgram prog = getShaderProgram( shader );
		gl.glUseProgram( prog.program() );
	}

	@Override
	public SetUniforms getUniformSetter( final Shader shader )
	{
		final ShaderProgram prog = getShaderProgram( shader );
		return new JoglSetUniforms( gl, prog.program() );
	}

	public static JoglGpuContext get( final GL3 gl )
	{
		return contexts.computeIfAbsent( gl, JoglGpuContext::new );
	}

	private static Map< GL3, JoglGpuContext > contexts = new HashMap<>();

	private final GL3 gl;

	private final Map< Shader, ShaderProgram > shaders = new HashMap<>();

	private JoglGpuContext( final GL3 gl )
	{
		this.gl = gl;
	}

	private ShaderProgram getShaderProgram( final Shader shader )
	{
		return shaders.computeIfAbsent( shader, s -> {
			final ShaderCode vs = new ShaderCode( GL_VERTEX_SHADER, 1, new CharSequence[][] { { s.getVertexShaderCode() } } );
			final ShaderCode fs = new ShaderCode( GL_FRAGMENT_SHADER, 1, new CharSequence[][] { { s.getFragementShaderCode() } } );
			vs.defaultShaderCustomization( gl, true, false );
			fs.defaultShaderCustomization( gl, true, false );
			final ShaderProgram prog = new ShaderProgram();
			prog.add( vs );
			prog.add( fs );
			prog.link( gl, System.err );
			vs.destroy( gl );
			fs.destroy( gl );
			return prog;
		} );
	}
}
