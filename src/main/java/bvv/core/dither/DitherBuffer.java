/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
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
package bvv.core.dither;

import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.offscreen.OffScreenFrameBuffer;
import bvv.core.shadergen.DefaultShader;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import bvv.core.util.DefaultQuad;
import com.jogamp.opengl.GL3;
import java.util.function.IntFunction;
import org.joml.Matrix4f;
import org.joml.Vector2f;

import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_RGBA8;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;

public class DitherBuffer
{
	private final int paddedWidth;
	private final int paddedHeight;

	private final int spw;
	private final int sps;

	private final int[] spox;
	private final int[] spoy;
	private final DitherPattern.DitherWeights[] onws;

	private final DefaultQuad quad = new DefaultQuad();
	private final DefaultShader progDither;
	private final DefaultShader progStitch;

	private final OffScreenFrameBuffer dither;
	private final OffScreenFrameBuffer stitch;

	private final int we;

	private final int he;

	public DitherBuffer( final int width, final int height )
	{
		this( width, height, 4, 9, 8 );
	}

	public DitherBuffer( final int width, final int height, final int spw, final int step, final int numSamples )
	{
		this( width, height, spw, step, numSamples, maxsp -> {
			final double minf = 0.2;
			final double maxf = 2.0;
			final double sps = spw * spw;
			final int numValidTextures = maxsp + 1;
			final double sigma = Math.sqrt( sps / numValidTextures )
					* ( minf + ( maxf - minf ) * ( (sps - maxsp - 1) / ( sps - 1 ) ) );
			return sigma;
		} );
	}

	public DitherBuffer( final int width, final int height, final int spw, final int step, final int numSamples, final IntFunction< Double > sampleToSigma )
	{
		this.we = ( width - 1 ) / spw + 1;
		this.he = ( height - 1 ) / spw + 1;
		this.spw = spw;
		this.paddedWidth = spw * we;
		this.paddedHeight = spw * he;

		sps = spw * spw;
		spox = new int[ sps ];
		spoy = new int[ sps ];
		DitherPattern.makePattern( spw, step, spox, spoy );

		onws = new DitherPattern.DitherWeights[ sps * sps ];
		for ( int maxsp = 0; maxsp < sps; ++maxsp )
		{
			for ( int oy = 0; oy < spw; ++oy )
			{
				for ( int ox = 0; ox < spw; ++ox )
				{
					final double sigma = sampleToSigma.apply( maxsp );
					final int i = ox + spw * oy + maxsp * sps;
					onws[ i ] = DitherPattern.getWeights( spw, we, he, maxsp + 1, ox, oy, numSamples, sigma, spox, spoy );
				}
			}
		}

		final Segment ditherVp = new SegmentTemplate( DitherBuffer.class, "dither.vp" ).instantiate();
		progDither = new DefaultShader( ditherVp.getCode(), DitherPattern.makeShader( numSamples ) );

		final Segment stitchVp = new SegmentTemplate( DitherBuffer.class, "stitch.vp" ).instantiate();
		final Segment stichFp = new SegmentTemplate( DitherBuffer.class, "stitch.fp" ).instantiate();
		progStitch = new DefaultShader( stitchVp.getCode(), stichFp.getCode() );

		dither = new OffScreenFrameBuffer( paddedWidth, paddedHeight, GL_RGBA8 );
		stitch = new OffScreenFrameBuffer( paddedWidth, paddedHeight, GL_RGBA8 );
	}

	public int numSteps()
	{
		return sps;
	}

	public int effectiveViewportWidth()
	{
		return we;
	}

	public int effectiveViewportHeight()
	{
		return he;
	}

	public Vector2f fragShift( int step )
	{
		return new Vector2f(
				( float ) deltaSp( spw, spox[ step ], we ),
				( float ) deltaSp( spw, spoy[ step ], he ) );
	}

	public Matrix4f ndcTransform( int step )
	{
		final float wvByWe = ( float ) paddedWidth / we;
		final float hvByHe = ( float ) paddedHeight / he;
		return new Matrix4f()
				.scale( 1.0f / wvByWe, 1.0f / hvByHe, 1 )
				.translate( 1 + 2 * spox[ step ] - wvByWe, 1 + 2 * spoy[ step ] - hvByHe, 0 );
	}

	public void bind( final GL3 gl )
	{
		dither.bind( gl, false );
	}

	public void unbind( final GL3 gl )
	{
		dither.unbind( gl, false );
	}

	public void dither( final GL3 gl, final int stepsCompleted, final int destWidth, final int destHeight )
	{
		final byte[] tmp = new byte[ 1 ];
		gl.glGetBooleanv( GL_BLEND, tmp, 0 );
		boolean restoreBlend = tmp[ 0 ] != 0;

		final JoglGpuContext context = JoglGpuContext.get( gl );

		final int maxsp = stepsCompleted - 1;

		stitch.bind( gl );
		gl.glDisable( GL_BLEND );
		gl.glActiveTexture( GL_TEXTURE0 );
		gl.glBindTexture( GL_TEXTURE_2D, dither.getTexColorBuffer() );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
		progDither.getUniform1i( "tex" ).set( 0 );

		progDither.use( context );
		progDither.getUniform2f( "spw" ).set( spw, spw );
		for ( int i = 0; i < sps; i++ )
		{
			DitherPattern.DitherWeights onw = onws[ spox[ i ] + spoy[ i ] * spw + maxsp * sps ];
			progDither.getUniform2f( "dsp" ).set(
					( float ) ( deltaSp( spw, spox[ i ], we ) - 0.5 ),
					( float ) ( deltaSp( spw, spoy[ i ], he ) - 0.5 ) );
			final float wvByWe = ( float ) paddedWidth / we;
			final float hvByHe = ( float ) paddedHeight / he;
			Matrix4f transform = new Matrix4f()
					.scale( 1.0f / wvByWe, 1.0f / hvByHe, 1 )
					.translate( 1 + 2 * spox[ i ] - wvByWe, 1 + 2 * spoy[ i ] - hvByHe, 0 );
			progDither.getUniformMatrix4f( "transform" ).set( transform );
			progDither.getUniform2fv( "spo" ).set( onw.spo );
			progDither.getUniform1fv( "K" ).set( onw.K );
			progDither.setUniforms( context );
			quad.draw( gl );
		}
		stitch.unbind( gl, false );

		gl.glBindTexture( GL_TEXTURE_2D, stitch.getTexColorBuffer() );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
		progStitch.getUniform1i( "tex" ).set( 0 );
		progStitch.getUniform2f( "viewportScale" ).set(
				( float ) paddedWidth / destWidth,
				( float ) paddedHeight / destHeight );
		progStitch.getUniform2f( "spw" ).set( spw, spw );
		progStitch.getUniform2f( "tls" ).set( we, he );
		progStitch.setUniforms( context );
		progStitch.use( context );
		if ( restoreBlend )
			gl.glEnable( GL_BLEND );
		quad.draw( gl );

		gl.glBindTexture( GL_TEXTURE_2D, 0 );
	}

	private double deltaSp( final int sps, final int spo, final int se )
	{
		return ( spo + 0.5 ) / sps - 0.5 - spo * se;
	}

	// for debug
	public OffScreenFrameBuffer getDitherBuffer()
	{
		return dither;
	}

	// for debug
	public OffScreenFrameBuffer getStitchBuffer()
	{
		return stitch;
	}
}
