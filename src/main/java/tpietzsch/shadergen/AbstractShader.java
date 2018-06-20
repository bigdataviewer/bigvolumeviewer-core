package tpietzsch.shadergen;

import com.jogamp.common.nio.Buffers;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.joml.Matrix4fc;

import tpietzsch.shadergen.backend.GpuContext;
import tpietzsch.shadergen.backend.SetUniforms;

public abstract class AbstractShader implements Shader
{
	private final StringBuilder vpCode;

	private final StringBuilder fpCode;

	private final Map< String, UniformImp > uniforms = new HashMap<>();

	public AbstractShader( final String vpCode, final String fpCode )
	{
		this.vpCode = new StringBuilder( vpCode );
		this.fpCode = new StringBuilder( fpCode );
	}

	public AbstractShader( final StringBuilder vpCode, final StringBuilder fpCode )
	{
		this.vpCode = vpCode;
		this.fpCode = fpCode;
	}

	protected abstract String getUniqueName( final String key  );

	@Override
	public Uniform1i getUniform1i( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp1i.class, UniformImp1i::new );
	}

	@Override
	public Uniform2i getUniform2i( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp2i.class, UniformImp2i::new );
	}

	@Override
	public Uniform3i getUniform3i( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp3i.class, UniformImp3i::new );
	}

	@Override
	public Uniform4i getUniform4i( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp4i.class, UniformImp4i::new );
	}

	@Override
	public Uniform1f getUniform1f( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp1f.class, UniformImp1f::new );
	}

	@Override
	public Uniform2f getUniform2f( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp2f.class, UniformImp2f::new );
	}

	@Override
	public Uniform3f getUniform3f( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp3f.class, UniformImp3f::new );
	}

	@Override
	public Uniform4f getUniform4f( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp4f.class, UniformImp4f::new );
	}

	@Override
	public Uniform3fv getUniform3fv( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImp3fv.class, UniformImp3fv::new );
	}

	@Override
	public UniformMatrix4f getUniformMatrix4f( final String key )
	{
		return getUniform( getUniqueName( key ), UniformImpMatrix4f.class, UniformImpMatrix4f::new );
	}

	@Override
	public void use( final GpuContext gpu )
	{
		gpu.use( this );
	}

	@Override
	public void setUniforms( final GpuContext gpu )
	{
		final SetUniforms visitor = gpu.getUniformSetter( this );
		for ( final UniformImp uniform : uniforms.values() )
		{
			synchronized ( uniform )
			{
				if ( visitor.shouldSet( uniform.modified ) )
				{
					uniform.setInShader( visitor );
					uniform.modified = false;
				}
			}
		}
	}

	@Override
	public StringBuilder getVertexShaderCode()
	{
		return vpCode;
	}

	@Override
	public StringBuilder getFragementShaderCode()
	{
		return fpCode;
	}

	/*
	 * PRIVATE
	 */

	private synchronized < T extends UniformImp > T getUniform( final String name, final Class< T > klass, final Function< String, T > create )
	{
		final UniformImp uniform = uniforms.get( name );
		if ( uniform == null )
		{
			final T u = create.apply( name );
			uniforms.put( name, u );
			return u;
		}
		else if ( klass.isInstance( uniform )  )
		{
			return ( T ) uniform;
		}
		else
		{
			throw new IllegalArgumentException(
					"trying to get uniform '" + name
							+ "' of class " + klass.getSimpleName()
							+ " which is already present with class " + uniform.getClass().getSimpleName() );
		}
	}

	/*
	 * UNIFORM IMPLEMENTATIONS
	 */

	abstract static class UniformImp
	{
		final String name;

		boolean modified;

		UniformImp( final String name )
		{
			this.name = name;
			modified = true;
		}

		abstract void setInShader( SetUniforms visitor );
	}

	static class UniformImp1i extends UniformImp implements Uniform1i
	{
		private int v0;

		public UniformImp1i( final String name )
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
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform1i( name, v0 );
		}
	}

	static class UniformImp2i extends UniformImp implements Uniform2i
	{
		private int v0;
		private int v1;

		public UniformImp2i( final String name )
		{
			super( name );
		}

		@Override
		public synchronized void set( final int v0, final int v1 )
		{
			if ( this.v0 != v0 || this.v1 != v1)
			{
				this.v0 = v0;
				this.v1 = v1;
				modified = true;
			}
		}

		@Override
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform2i( name, v0, v1 );
		}
	}

	static class UniformImp3i extends UniformImp implements Uniform3i
	{
		private int v0;
		private int v1;
		private int v2;

		public UniformImp3i( final String name )
		{
			super( name );
		}

		@Override
		public synchronized void set( final int v0, final int v1, final int v2 )
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
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform3i( name, v0, v1, v2 );
		}
	}

	static class UniformImp4i extends UniformImp implements Uniform4i
	{
		private int v0;
		private int v1;
		private int v2;
		private int v3;

		public UniformImp4i( final String name )
		{
			super( name );
		}

		@Override
		public synchronized void set( final int v0, final int v1, final int v2, final int v3 )
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
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform4i( name, v0, v1, v2, v3 );
		}
	}

	static class UniformImp1f extends UniformImp implements Uniform1f
	{
		private float v0;

		public UniformImp1f( final String name )
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
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform1f( name, v0 );
		}
	}

	static class UniformImp2f extends UniformImp implements Uniform2f
	{
		private float v0;
		private float v1;

		public UniformImp2f( final String name )
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
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform2f( name, v0, v1 );
		}
	}

	static class UniformImp3f extends UniformImp implements Uniform3f
	{
		private float v0;
		private float v1;
		private float v2;

		public UniformImp3f( final String name )
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
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform3f( name, v0, v1, v2 );
		}
	}

	static class UniformImp4f extends UniformImp implements Uniform4f
	{
		private float v0;
		private float v1;
		private float v2;
		private float v3;

		public UniformImp4f( final String name )
		{
			super( name );
		}

		@Override
		public void set( final float v0, final float v1, final float v2, final float v3 )
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
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniform4f( name, v0, v1, v2, v3 );
		}
	}

	static class UniformImp3fv extends UniformImp implements Uniform3fv
	{
		private float[] v;

		public UniformImp3fv( final String name )
		{
			super( name );
		}

		@Override
		void setInShader( final SetUniforms visitor )
		{
			if ( v != null )
				visitor.setUniform3fv( name, v.length / 3, v );
		}

		@Override
		public void set( final float[] value )
		{
			this.v = value.clone();
			modified = true;
		}
	}

	static class UniformImpMatrix4f extends UniformImp implements UniformMatrix4f
	{
		private final FloatBuffer value = Buffers.newDirectFloatBuffer( 16 );

		public UniformImpMatrix4f( final String name )
		{
			super( name );
		}

		@Override
		public void set( final Matrix4fc m44 )
		{
			m44.get( 0, value );
			modified = true;
		}

		@Override
		void setInShader( final SetUniforms visitor )
		{
			visitor.setUniformMatrix4f( name, false, value );
		}
	}
}
