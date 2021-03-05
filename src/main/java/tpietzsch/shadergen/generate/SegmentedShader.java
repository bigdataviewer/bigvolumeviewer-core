package tpietzsch.shadergen.generate;

import java.util.Map;

import tpietzsch.shadergen.*;

public class SegmentedShader extends AbstractShader
{
	private final Map< String, Object > uniforms;

	SegmentedShader( final StringBuilder vpCode, final StringBuilder fpCode, final Map< String, Object > uniforms )
	{
		super( vpCode, fpCode );
		this.uniforms = uniforms;
	}

	@Override
	protected String getUniqueName( final String key )
	{
		final Object o = uniforms.get( key );
		if ( o == SegmentedShaderBuilder.NOT_UNIQUE )
			throw new IllegalArgumentException( "uniform name '" + key + "' is not unique across segments." );
		else if ( o == null )
			return key;
		else
			return ( String ) o;
	}

	public Uniform1i getUniform1i( final Segment segment, final String key )
	{
		return getUniform1i( segment.getSingleIdentifier( key ) );
	}

	public Uniform2i getUniform2i( final Segment segment, final String key )
	{
		return getUniform2i( segment.getSingleIdentifier( key ) );
	}

	public Uniform3i getUniform3i( final Segment segment, final String key )
	{
		return getUniform3i( segment.getSingleIdentifier( key ) );
	}

	public Uniform4i getUniform4i( final Segment segment, final String key )
	{
		return getUniform4i( segment.getSingleIdentifier( key ) );
	}

	public Uniform1iv getUniform1iv(final Segment segment, final String key )
	{
		return getUniform1iv( segment.getSingleIdentifier( key ) );
	}

	public Uniform2iv getUniform2iv(final Segment segment, final String key )
	{
		return getUniform2iv( segment.getSingleIdentifier( key ) );
	}

	public Uniform3iv getUniform3iv(final Segment segment, final String key )
	{
		return getUniform3iv( segment.getSingleIdentifier( key ) );
	}

	public Uniform4iv getUniform4iv(final Segment segment, final String key )
	{
		return getUniform4iv( segment.getSingleIdentifier( key ) );
	}

	public Uniform1f getUniform1f( final Segment segment, final String key )
	{
		return getUniform1f( segment.getSingleIdentifier( key ) );
	}

	public Uniform2f getUniform2f( final Segment segment, final String key )
	{
		return getUniform2f( segment.getSingleIdentifier( key ) );
	}

	public Uniform3f getUniform3f( final Segment segment, final String key )
	{
		return getUniform3f( segment.getSingleIdentifier( key ) );
	}

	public Uniform4f getUniform4f( final Segment segment, final String key )
	{
		return getUniform4f( segment.getSingleIdentifier( key ) );
	}

	public Uniform1fv getUniform1fv(final Segment segment, final String key )
	{
		return getUniform1fv( segment.getSingleIdentifier( key ) );
	}

	public Uniform2fv getUniform2fv(final Segment segment, final String key )
	{
		return getUniform2fv( segment.getSingleIdentifier( key ) );
	}

	public Uniform3fv getUniform3fv( final Segment segment, final String key )
	{
		return getUniform3fv( segment.getSingleIdentifier( key ) );
	}

	public Uniform4fv getUniform4fv(final Segment segment, final String key )
	{
		return getUniform4fv( segment.getSingleIdentifier( key ) );
	}

	public UniformMatrix4f getUniformMatrix4f( final Segment segment, final String key )
	{
		return getUniformMatrix4f( segment.getSingleIdentifier( key ) );
	}

	public UniformSampler getUniformSampler( final Segment segment, final String key )
	{
		return getUniformSampler( segment.getSingleIdentifier( key ) );
	}
}
