package tpietzsch.shadergen;

import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.util.glsl.ShaderCode;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import net.imglib2.RealInterval;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.stringtemplate.v4.ST;

import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static tpietzsch.shadergen.Playground.MinMax.MIN;
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
	}


	// =======================


	static class ShaderFragment
	{
		private final String code;

		private final Map< String, String > keyToIdentifier;

		public ShaderFragment( final String code, final Map< String, String > keyToIdentifier )
		{
			this.code = code;
			this.keyToIdentifier = keyToIdentifier;
		}

		public String getCode()
		{
			return code;
		}

		public String getIdentifier( final String key )
		{
			return keyToIdentifier.get( key );
		}

		public Map< String, String > getKeyToIdentifierMap()
		{
			return keyToIdentifier;
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

	public enum MinMax
	{
		MIN, MAX
	}

	public interface Uniform1i
	{
		void set( int v0 );
	}

	public interface Uniform2i
	{
		void set( int v0, int v1 );
	}

	public interface Uniform3i
	{
		void set( int v0, int v1, int v2 );
	}

	public interface Uniform4i
	{
		void set( int v0, int v1, int v2, int v3 );
	}

	public interface Uniform1f
	{
		void set( float value );
	}

	public interface Uniform2f
	{
		void set( float v0, float v1 );

		default void set( Vector2fc v )
		{
			set( v.x(), v.y() );
		}

		default void set( RealInterval interval, MinMax minmax )
		{
			if ( interval.numDimensions() < 2 )
				throw new IllegalArgumentException(
						"Interval has " + interval.numDimensions() + " dimensions."
								+ "Expected interval of at least dimension 2." );

			if ( minmax == MIN )
				set(
						( float ) interval.realMin( 0 ),
						( float ) interval.realMin( 1 ) );
			else
				set(
						( float ) interval.realMax( 0 ),
						( float ) interval.realMax( 1 ) );
		}
	}

	public interface Uniform3f
	{
		void set( float v0, float v1, float v2 );

		default void set( Vector3fc v )
		{
			set( v.x(), v.y(), v.z() );
		}

		default void set( RealInterval interval, MinMax minmax )
		{
			if ( interval.numDimensions() < 3 )
				throw new IllegalArgumentException(
						"Interval has " + interval.numDimensions() + " dimensions."
								+ "Expected interval of at least dimension 3." );

			if ( minmax == MIN )
				set(
						( float ) interval.realMin( 0 ),
						( float ) interval.realMin( 1 ),
						( float ) interval.realMin( 2 ) );
			else
				set(
						( float ) interval.realMax( 0 ),
						( float ) interval.realMax( 1 ),
						( float ) interval.realMax( 2 ) );
		}
	}

	public interface Uniform4f
	{
		void set( float v0, float v1, float v2, float v3 );

		default void set( Vector4fc v )
		{
			set( v.x(), v.y(), v.z(), v.w() );
		}

		default void set( RealInterval interval, MinMax minmax )
		{
			if ( interval.numDimensions() < 4 )
				throw new IllegalArgumentException(
						"Interval has " + interval.numDimensions() + " dimensions."
								+ "Expected interval of at least dimension 4." );

			if ( minmax == MIN )
				set(
						( float ) interval.realMin( 0 ),
						( float ) interval.realMin( 1 ),
						( float ) interval.realMin( 2 ),
						( float ) interval.realMin( 3 ) );
			else
				set(
						( float ) interval.realMax( 0 ),
						( float ) interval.realMax( 1 ),
						( float ) interval.realMax( 2 ),
						( float ) interval.realMax( 3 ) );
		}
	}

	public interface Uniforms
	{
		void addShaderFragment( ShaderFragment fragment );

		void setUniformValues( UniformContext context );

		void updateUniformValues( UniformContext context );

		Uniform1i getUniform1i( String key );

		Uniform2i getUniform2i( String key );

		Uniform3i getUniform3i( String key );

		Uniform4i getUniform4i( String key );

		Uniform1f getUniform1f( String key );

		Uniform2f getUniform2f( String key );

		Uniform3f getUniform3f( String key );

		Uniform4f getUniform4f( String key );
	}

	public interface UniformContext
	{}

	// =======================

	static class JoglUniforms implements Uniforms
	{
		private final Map< String, String > keyToIdentifier = new HashMap<>();

		private final Map< String, AbstractJoglUniform > keyToUniform = new HashMap<>();

		@Override
		public void addShaderFragment( ShaderFragment fragment )
		{
			final Map< String, String > map = fragment.getKeyToIdentifierMap();
			map.forEach( ( key, identifier ) -> {
				final String existing = keyToIdentifier.put( key, identifier );
				if ( existing != null && !identifier.equals( existing ) )
					throw new IllegalArgumentException(
							"ShaderFragments map key '" + key
									+ "' to distinct identifers '" + identifier
									+ "' and '" + existing + "'" );
			} );
		}

		@Override
		public Uniform1i getUniform1i( final String key )
		{
			return getUniform( key, JoglUniform1i.class, JoglUniform1i::new );
		}

		@Override
		public Uniform2i getUniform2i( final String key )
		{
			return getUniform( key, JoglUniform2i.class, JoglUniform2i::new );
		}

		@Override
		public Uniform3i getUniform3i( final String key )
		{
			return getUniform( key, JoglUniform3i.class, JoglUniform3i::new );
		}

		@Override
		public Uniform4i getUniform4i( final String key )
		{
			return getUniform( key, JoglUniform4i.class, JoglUniform4i::new );
		}

		@Override
		public Uniform1f getUniform1f( final String key )
		{
			return getUniform( key, JoglUniform1f.class, JoglUniform1f::new );
		}

		@Override
		public Uniform2f getUniform2f( final String key )
		{
			return getUniform( key, JoglUniform2f.class, JoglUniform2f::new );
		}

		@Override
		public Uniform3f getUniform3f( final String key )
		{
			return getUniform( key, JoglUniform3f.class, JoglUniform3f::new );
		}

		@Override
		public Uniform4f getUniform4f( final String key )
		{
			return getUniform( key, JoglUniform4f.class, JoglUniform4f::new );
		}

		public void setUniformValues( UniformContext context )
		{
			JoglUniformContext juc = ( JoglUniformContext ) context;
			keyToUniform.values().forEach( juc::setUniformValues );
		}

		public void updateUniformValues( UniformContext context )
		{
			JoglUniformContext juc = ( JoglUniformContext ) context;
			keyToUniform.values().forEach( juc::updateUniformValues );
		}

		private synchronized < T extends AbstractJoglUniform > T getUniform( String key, Class< T > klass, Function< String, T > create )
		{
			final AbstractJoglUniform uniform = keyToUniform.get( key );
			if ( uniform == null )
			{
				final T u = create.apply( keyToIdentifier.get( key ) );
				keyToUniform.put( key, u );
				return u;
			}
			else if ( klass.isInstance( uniform )  )
			{
				return ( T ) uniform;
			}
			else
			{
				throw new IllegalArgumentException(
						"trying to get uniform '" + key
								+ "' of class " + klass.getSimpleName()
								+ " which is already present with class " + uniform.getClass().getSimpleName() );
			}
		}
	}

	static class JoglUniformContext implements UniformContext
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

		void setUniform1i( final AbstractJoglUniform uniform, final int v0 )
		{
			gl().glProgramUniform1i( program(), location( uniform.name ), v0 );
		}

		void setUniform2i( final AbstractJoglUniform uniform, final int v0, final int v1 )
		{
			gl().glProgramUniform2i( program(), location( uniform.name ), v0, v1 );
		}

		void setUniform3i( final AbstractJoglUniform uniform, final int v0, final int v1, final int v2 )
		{
			gl().glProgramUniform3i( program(), location( uniform.name ), v0, v1, v2 );
		}

		void setUniform4i( final AbstractJoglUniform uniform, final int v0, final int v1, final int v2, final int v3 )
		{
			gl().glProgramUniform4i( program(), location( uniform.name ), v0, v1, v2, v3 );
		}

		void setUniform1f( final AbstractJoglUniform uniform, final float v0 )
		{
			gl().glProgramUniform1f( program(), location( uniform.name ), v0 );
		}

		void setUniform2f( final AbstractJoglUniform uniform, final float v0, final float v1 )
		{
			gl().glProgramUniform2f( program(), location( uniform.name ), v0, v1 );
		}

		void setUniform3f( final AbstractJoglUniform uniform, final float v0, final float v1, final float v2 )
		{
			gl().glProgramUniform3f( program(), location( uniform.name ), v0, v1, v2 );
		}

		void setUniform4f( final AbstractJoglUniform uniform, final float v0, final float v1, final float v2, final float v3 )
		{
			gl().glProgramUniform4f( program(), location( uniform.name ), v0, v1, v2, v3 );
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

	static class JoglUniform2i extends AbstractJoglUniform implements Uniform2i
	{
		private int v0;
		private int v1;

		public JoglUniform2i( final String name )
		{
			super( name );
		}

		@Override
		public synchronized void set( final int v0, int v1 )
		{
			if ( this.v0 != v0 || this.v1 != v1)
			{
				this.v0 = v0;
				this.v1 = v1;
				modified = true;
			}
		}

		@Override
		void setInShader( JoglUniformContext context )
		{
			context.setUniform2i( this, v0, v1 );
		}
	}

	static class JoglUniform3i extends AbstractJoglUniform implements Uniform3i
	{
		private int v0;
		private int v1;
		private int v2;

		public JoglUniform3i( final String name )
		{
			super( name );
		}

		@Override
		public synchronized void set( final int v0, int v1, int v2 )
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
		void setInShader( JoglUniformContext context )
		{
			context.setUniform3i( this, v0, v1, v2 );
		}
	}

	static class JoglUniform4i extends AbstractJoglUniform implements Uniform4i
	{
		private int v0;
		private int v1;
		private int v2;
		private int v3;

		public JoglUniform4i( final String name )
		{
			super( name );
		}

		@Override
		public synchronized void set( final int v0, int v1, int v2, int v3 )
		{
			if ( this.v0 != v0 || this.v1 != v1 || this.v2 != v2 || this.v3 != v3 )
			{
				this.v0 = v0;
				this.v1 = v1;
				this.v2 = v2;
				this.v3 = v3;
				modified = true;
			}
		}

		@Override
		void setInShader( JoglUniformContext context )
		{
			context.setUniform4i( this, v0, v1, v2, v3 );
		}
	}

	static class JoglUniform1f extends AbstractJoglUniform implements Uniform1f
	{
		private float v0;

		public JoglUniform1f( final String name )
		{
			super( name );
		}

		@Override
		public void set( final float v0 )
		{
			if ( this.v0 != v0 )
			{
				this.v0 = v0;
				modified = true;
			}
		}

		@Override
		void setInShader( final JoglUniformContext context )
		{
			context.setUniform1f( this, v0 );
		}
	}

	static class JoglUniform2f extends AbstractJoglUniform implements Uniform2f
	{
		private float v0;
		private float v1;

		public JoglUniform2f( final String name )
		{
			super( name );
		}

		@Override
		public void set( final float v0, final float v1 )
		{
			if ( this.v0 != v0 || this.v1 != v1 )
			{
				this.v0 = v0;
				this.v1 = v1;
				modified = true;
			}
		}

		@Override
		void setInShader( final JoglUniformContext context )
		{
			context.setUniform2f( this, v0, v1 );
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

	static class JoglUniform4f extends AbstractJoglUniform implements Uniform4f
	{
		private float v0;
		private float v1;
		private float v2;
		private float v3;

		public JoglUniform4f( final String name )
		{
			super( name );
		}

		@Override
		public void set( final float v0, final float v1, final float v2, float v3 )
		{
			if ( this.v0 != v0 || this.v1 != v1 || this.v2 != v2 || this.v3 != v3 )
			{
				this.v0 = v0;
				this.v1 = v1;
				this.v2 = v2;
				this.v3 = v3;
				modified = true;
			}
		}

		@Override
		void setInShader( final JoglUniformContext context )
		{
			context.setUniform4f( this, v0, v1, v2, v3 );
		}
	}
}
