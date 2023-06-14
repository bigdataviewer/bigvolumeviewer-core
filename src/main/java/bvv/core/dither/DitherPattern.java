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
package bvv.core.dither;

import java.util.ArrayList;
import java.util.Comparator;

public class DitherPattern
{
	/**
	 * @param spw window width
	 * @param step flattened step
	 * @param spox receives x offsets
	 * @param spoy receives y offsets
	 */
	public static void makePattern( final int spw, final int step, final int[] spox, final int[] spoy )
	{
		final int sps = spw * spw;
		for ( int i = 0; i < sps; ++i )
		{
			final int fo = ( i * step ) % sps;
			spoy[ i ] = fo / spw;
			spox[ i ] = fo % spw;
		}
	}

	public static String makeShader( final int numSamples )
	{
		StringBuilder sb = new StringBuilder();
		sb.append( "out vec4 FragColor;\n" );
		sb.append( "\n" );
		sb.append( "uniform sampler2D tex;\n" );
		sb.append( "uniform vec2 spw;\n" );
		sb.append( "uniform vec2 dsp;\n" );
		sb.append( "\n" );
		sb.append( String.format( "uniform vec2 spo[ %d ];\n", numSamples ) );
		sb.append( String.format( "uniform float K[ %d ];\n", numSamples ) );
		sb.append( "\n" );
		sb.append( "void main()\n" );
		sb.append( "{\n" );
		sb.append( "\tvec2 xg = gl_FragCoord.xy + dsp;\n" );
		sb.append( "\n" );
		sb.append( "\tvec2 texsize = textureSize( tex, 0 );\n" );

		for ( int i = 0; i < numSamples; ++i )
			sb.append( String.format( "\tvec4 v%1$d = texture( tex, ( xg + spo[ %1$d ] ) / texsize );\n", i ) );

		sb.append( "\tFragColor = K[ 0 ] * v0" );
		for ( int i = 1; i < numSamples; ++i )
			sb.append( String.format( " + K[ %1$d ] * v%1$d", i ) );
		sb.append( ";\n" );

		sb.append( "}\n" );

//		System.out.println( "-----------------------------------------------------------------" );
//		System.out.println( sb);
//		System.out.println( "-----------------------------------------------------------------" );
//		System.out.println();
		return sb.toString();
	}

	private static class OD
	{
		final int spox;
		final int spoy;
		final int sptx;
		final int spty;
		final double squdist;
		double weight;

		public OD( final int spox, final int spoy, final int sptx, final int spty, final double squdist )
		{
			this.spox = spox;
			this.spoy = spoy;
			this.sptx = sptx;
			this.spty = spty;
			this.squdist = squdist;
		}
	}

	public static class DitherWeights
	{
		public final float[][] spo;
		public final float[] K;

		public DitherWeights( final float[][] spo, final float[] k )
		{
			this.spo = spo;
			K = k;
		}
	}

	public static DitherWeights getWeights(
			final int spw,
			final int tlw, final int tlh,
			final int numValidTextures,
			final int ox, final int oy,
			final int maxNeighbors,
			final double sigma,
			final int[] spox,
			final int[] spoy )
	{
		final ArrayList< OD > squdist = new ArrayList<>();
		for ( int spi = 0; spi < numValidTextures; ++spi )
		{
			int[] spox4 = new int[ 4 ];
			int[] spoy4 = new int[ 4 ];
			if ( ox < spox[ spi ] )
			{
				spox4[ 0 ] = spox[ spi ] - spw;
				spox4[ 1 ] = spox[ spi ];
				spox4[ 2 ] = spox[ spi ] - spw;
				spox4[ 3 ] = spox[ spi ];
			}
			else
			{
				spox4[ 0 ] = spox[ spi ];
				spox4[ 1 ] = spox[ spi ] + spw;
				spox4[ 2 ] = spox[ spi ];
				spox4[ 3 ] = spox[ spi ] + spw;
			}
			if ( oy < spoy[ spi ] )
			{
				spoy4[ 0 ] = spoy[ spi ] - spw;
				spoy4[ 1 ] = spoy[ spi ] - spw;
				spoy4[ 2 ] = spoy[ spi ];
				spoy4[ 3 ] = spoy[ spi ];
			}
			else
			{
				spoy4[ 0 ] = spoy[ spi ];
				spoy4[ 1 ] = spoy[ spi ];
				spoy4[ 2 ] = spoy[ spi ] + spw;
				spoy4[ 3 ] = spoy[ spi ] + spw;
			}

			for ( int i = 0; i < 4; ++i )
			{
				final int kx = Math.abs( ox - spox4[ i ] );
				final int ky = Math.abs( oy - spoy4[ i ] );
				final int sd = kx * kx + ky * ky;
				squdist.add( new OD( spox4[ i ], spoy4[ i ], spox[ spi ], spoy[ spi ], sd ) );
			}
		}

		squdist.sort( Comparator.comparingDouble( o -> o.squdist ) );

		double swu = 0;
		int n = 0;
		for ( OD od : squdist )
		{
			final double sd = od.squdist;

			final double wu = Math.exp( - sd / ( 2 * sigma * sigma ) );
			od.weight = wu;
			swu += wu;

			if ( ++n == maxNeighbors )
				break;
		}

		for ( int i = 0; i < n; ++i )
			squdist.get( i ).weight /= swu;

		float[][] spo = new float[ maxNeighbors ][ 2 ];
		float[] K = new float[ maxNeighbors ];
		for ( int i = 0; i < n; ++i )
		{
			OD od = squdist.get( i );
			spo[ i ][ 0 ] = ( float ) ( ( double ) ( ( od.spox - od.sptx ) - ox ) / spw + od.sptx * tlw + 0.5 );
			spo[ i ][ 1 ] = ( float ) ( ( double ) ( ( od.spoy - od.spty ) - oy ) / spw + od.spty * tlh + 0.5 );
			K[ i ] = ( float ) od.weight;
		}

		return new DitherWeights( spo, K );
	}
}
