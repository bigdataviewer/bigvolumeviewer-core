/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2023 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.shadergen.generate;

import bvv.core.backend.Texture;
import bvv.core.shadergen.Uniform1fv;
import bvv.core.shadergen.Uniform1iv;
import bvv.core.shadergen.Uniform2fv;
import bvv.core.shadergen.Uniform2iv;
import bvv.core.shadergen.Uniform3iv;
import bvv.core.shadergen.Uniform4fv;
import bvv.core.shadergen.Uniform4iv;
import bvv.core.shadergen.UniformMatrix3f;
import java.util.Map;

import bvv.core.shadergen.AbstractShader;
import bvv.core.shadergen.Uniform1f;
import bvv.core.shadergen.Uniform1i;
import bvv.core.shadergen.Uniform2f;
import bvv.core.shadergen.Uniform2i;
import bvv.core.shadergen.Uniform3f;
import bvv.core.shadergen.Uniform3fv;
import bvv.core.shadergen.Uniform3i;
import bvv.core.shadergen.Uniform4f;
import bvv.core.shadergen.Uniform4i;
import bvv.core.shadergen.UniformMatrix4f;
import bvv.core.shadergen.UniformSampler;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

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

	public UniformMatrix3f getUniformMatrix3f( final Segment segment, final String key )
	{
		return getUniformMatrix3f( segment.getSingleIdentifier( key ) );
	}

	public UniformMatrix4f getUniformMatrix4f( final Segment segment, final String key )
	{
		return getUniformMatrix4f( segment.getSingleIdentifier( key ) );
	}

	public UniformSampler getUniformSampler( final Segment segment, final String key )
	{
		return getUniformSampler( segment.getSingleIdentifier( key ) );
	}


	/**
	 * <em>(added for use by scenery)</em>
	 */
	public void setUniformValueByType( Segment segment, String key, int elementSize, Object value )
	{
		if ( value instanceof float[] )
		{
			final float[] array = ( float[] ) value;
			switch ( elementSize )
			{
			case 1:
				getUniform1fv( segment, key ).set( array );
				break;
			case 2:
				getUniform2fv( segment, key ).set( array );
				break;
			case 3:
				getUniform3fv( segment, key ).set( array );
				break;
			case 4:
				getUniform4fv( segment, key ).set( array );
				break;
			default:
				throw new IllegalArgumentException( "Uniform array element size not supported: " + elementSize );
			}
		}
		else if ( value instanceof int[] )
		{
			final int[] array = ( int[] ) value;
			switch ( elementSize )
			{
			case 1:
				getUniform1iv( segment, key ).set( array );
				break;
			case 2:
				getUniform2iv( segment, key ).set( array );
				break;
			case 3:
				getUniform3iv( segment, key ).set( array );
				break;
			case 4:
				getUniform4iv( segment, key ).set( array );
				break;
			default:
				throw new IllegalArgumentException( "Uniform array element size not supported: " + elementSize );
			}
		}
		else
		{
			throw new IllegalArgumentException( "Object type " + value.getClass().getCanonicalName() + " is not usable for uniforms." );
		}
	}

	/**
	 * <em>(added for use by scenery)</em>
	 */
	public void setUniformValueByType( Segment segment, String key, Object value )
	{
		if ( value instanceof Integer )
		{
			getUniform1i( segment, key ).set( ( int ) value );
		}
		else if ( value instanceof Float )
		{
			getUniform1f( segment, key ).set( ( float ) value );
		}
		else if ( value instanceof Vector2f )
		{
			getUniform2f( segment, key ).set( ( Vector2f ) value );
		}
		else if ( value instanceof Vector3f )
		{
			getUniform3f( segment, key ).set( ( Vector3f ) value );
		}
		else if ( value instanceof Vector4f )
		{
			getUniform4f( segment, key ).set( ( Vector4f ) value );
		}
		else if ( value instanceof Vector2i )
		{
			getUniform2i( segment, key ).set( ( Vector2i ) value );
		}
		else if ( value instanceof Vector3i )
		{
			getUniform3i( segment, key ).set( ( Vector3i ) value );
		}
		else if ( value instanceof Vector4i )
		{
			getUniform4i( segment, key ).set( ( Vector4i ) value );
		}
		else if ( value instanceof Matrix3f )
		{
			getUniformMatrix3f( segment, key ).set( ( Matrix3f ) value );
		}
		else if ( value instanceof Matrix4f )
		{
			getUniformMatrix4f( segment, key ).set( ( Matrix4f ) value );
		}
		else if ( value instanceof Texture )
		{
			getUniformSampler( segment, key ).set( ( Texture ) value );
		}
		else
		{
			throw new IllegalArgumentException( "Object type " + value.getClass().getCanonicalName() + " is not usable for uniforms." );
		}
	}
}
