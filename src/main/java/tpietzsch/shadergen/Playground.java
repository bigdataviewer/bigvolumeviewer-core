package tpietzsch.shadergen;

import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.util.glsl.ShaderCode;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3fc;
import org.stringtemplate.v4.ST;

import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static tpietzsch.shadergen.StringTemplateStuff.clearAttributes;

public class Playground
{

	public static void main( String[] args ) throws IOException
	{
		final Class< ? > resourceContext = Playground.class;
		final String resourceName = "ex1.fp";
		final List< String > keys = Arrays.asList( "rgb" );
		final ShaderFragmentTemplate template = new ShaderFragmentTemplate( resourceContext, resourceName, keys );
		final ShaderFragment shaderFragment = template.instantiate();
		final ShaderCode fs = new ShaderCode( GL_FRAGMENT_SHADER, 1, new CharSequence[][] { { new StringBuilder( shaderFragment.code ) } } );




//		vs.defaultShaderCustomization( gl3, true, false );
//		fs.defaultShaderCustomization( gl3, true, false );
//
//		prog = new ShaderProgram();
//		prog.add( vs );
//		prog.add( fs );
//		prog.link( gl3, System.err );
//		vs.destroy( gl3 );
//		fs.destroy( gl3 );

	}


	// =======================


	static class ShaderFragment
	{
		private final String code;

		private final Map< String, String > keyToInstance;

		public ShaderFragment( final String code, final Map< String, String > keyToInstance )
		{
			this.code = code;
			this.keyToInstance = keyToInstance;
		}

		public String getCode()
		{
			return code;
		}

		public String getName( final String key )
		{
			return keyToInstance.get( key );
		}
	}

	static class ShaderFragmentTemplate
	{
		private final ST st;

		private final List< String > keys;

		private static final AtomicInteger idGen = new AtomicInteger();

		public ShaderFragmentTemplate(
				final Class< ? > resourceContext,
				final String resourceName,
				final List< String > keys ) throws IOException

		{
			st = StringTemplateStuff.loadAndPatchSnippet( resourceContext, resourceName, keys );
			this.keys = keys;
		}

		public Map< String, String > proposeKeyToIdentifierMap()
		{
			Map< String, String > keyToIdentifier = new HashMap<>();
			int baseId = idGen.getAndAdd( keys.size() );
			for ( String key : keys )
			{
				String instance = String.format( "%s__%d__", key, baseId++ );
				keyToIdentifier.put( key, instance );
			}
			return keyToIdentifier;
		}

		public ShaderFragment instantiate()
		{
			return instantiate( proposeKeyToIdentifierMap() );
		}

		public ShaderFragment instantiate( Map< String, String > keyToIdentifier )
		{
			clearAttributes( st );
			keys.forEach( key -> st.add( key, keyToIdentifier.get( key ) ) );
			return new ShaderFragment( st.render(), keyToIdentifier );
		}
	}

	// =======================


	public interface Uniform1i
	{
		void set( int value );
	}

	public interface Uniform3f
	{
		void set( float v0, float v1, float v2 );

		default void set( Vector3fc v )
		{
			set( v.x(), v.y(), v.z() );
		}
	}


	// =======================


	static class JoglUniformContext
	{
		private final GL2ES2 gl;

		private final int program;

		public JoglUniformContext( GL2ES2 gl, int program )
		{
			this.gl = gl;
			this.program = program;
		}

		private GL2ES2 gl()
		{
			return gl;
		}

		private int program()
		{
			return program;
		}

		private int location( final String name )
		{
			return gl().glGetUniformLocation( program(), name );
		}

		void setUniform1i( final AbstractJoglUniform uniform, final int value )
		{
			gl().glProgramUniform1i( program(), location( uniform.name ), value );
		}

		void setUniform3f( final AbstractJoglUniform uniform, final float v0, final float v1, final float v2 )
		{
			gl().glProgramUniform3f( program(), location( uniform.name ), v0, v1, v2 );
		}

		void updateUniformValues( final AbstractJoglUniform uniform )
		{
			synchronized ( uniform )
			{
				if ( uniform.modified )
				{
					System.out.println( "JoglUniformContext.updateUniformValues actually setting value" );
					uniform.setInShader( this );
					uniform.modified = false;
				}
			}
		}

		void setUniformValues( final AbstractJoglUniform uniform )
		{
			synchronized ( uniform )
			{
				uniform.setInShader( this );
				uniform.modified = false;
			}
		}
	}

	static abstract class AbstractJoglUniform
	{
		final String name;

		boolean modified;

		public AbstractJoglUniform( final String name )
		{
			this.name = name;
			modified = true;
		}

		abstract void setInShader( JoglUniformContext context );

	}

	static class JoglUniform1i extends AbstractJoglUniform implements Uniform1i
	{
		private int v0;

		public JoglUniform1i( final String name )
		{
			super( name );
		}

		@Override
		public synchronized void set( final int v0 )
		{
			if ( this.v0 != v0 )
			{
				this.v0 = v0;
				modified = true;
			}
		}

		@Override
		void setInShader( JoglUniformContext context )
		{
			context.setUniform1i( this, v0 );
		}
	}

	static class JoglUniform3f extends AbstractJoglUniform implements Uniform3f
	{
		private float v0;
		private float v1;
		private float v2;

		public JoglUniform3f( final String name )
		{
			super( name );
		}

		@Override
		public void set( final float v0, final float v1, final float v2 )
		{
			if ( this.v0 != v0 || this.v1 != v1 || this.v2 != v2 )
			{
				this.v0 = v0;
				this.v1 = v1;
				this.v2 = v2;
				modified = true;
			}
		}

		@Override
		void setInShader( final JoglUniformContext context )
		{
			context.setUniform3f( this, v0, v1, v2 );
		}
	}
}
