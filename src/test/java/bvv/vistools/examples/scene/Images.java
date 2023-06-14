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
package bvv.vistools.examples.scene;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class Images
{
	public static byte[] loadBytesRGB( InputStream file ) throws IOException
	{
		return loadBytesRGB( file, false );
	}

	public static byte[] loadBytesRGB( InputStream file, boolean flipY ) throws IOException
	{
		BufferedImage bufferedImage = ImageIO.read( file );
		final int w = bufferedImage.getWidth();
		final int h = bufferedImage.getHeight();
		byte[] data = new byte[ w * h * 3 ];
		int i = 0;
		for ( int y = 0; y < h; y++ )
		{
			for ( int x = 0; x < w; x++ )
			{
				final int rgb = bufferedImage.getRGB( x, flipY ? h - y - 1 : y );
				data[ i++ ] = b2( rgb );
				data[ i++ ] = b1( rgb );
				data[ i++ ] = b0( rgb );
			}
		}
		return data;
	}

	public static byte[] loadBytesRGBA( InputStream file ) throws IOException
	{
		return loadBytesRGBA( file, false );
	}

	public static byte[] loadBytesRGBA( InputStream file, boolean flipY ) throws IOException
	{
		BufferedImage bufferedImage = ImageIO.read( file );
		final int w = bufferedImage.getWidth();
		final int h = bufferedImage.getHeight();
		byte[] data = new byte[ w * h * 4 ];
		int i = 0;
		for ( int y = 0; y < h; y++ )
		{
			for ( int x = 0; x < w; x++ )
			{
				final int rgb = bufferedImage.getRGB( x, flipY ? h - y - 1 : y  );
				data[ i++ ] = b2( rgb );
				data[ i++ ] = b1( rgb );
				data[ i++ ] = b0( rgb );
				data[ i++ ] = b3( rgb );
			}
		}
		return data;
	}

	static byte b3( final int value )
	{
		return ( byte ) ( ( value >> 24 ) & 0xff );
	}

	static byte b2( final int value )
	{
		return ( byte ) ( ( value >> 16 ) & 0xff );
	}

	static byte b1( final int value )
	{
		return ( byte ) ( ( value >> 8 ) & 0xff );
	}

	static byte b0( final int value )
	{
		return ( byte ) ( value & 0xff );
	}


//	public static byte[] loadBytes( InputStream file ) throws IOException
//	{
//		BufferedImage bufferedImage = ImageIO.read( file );
//		WritableRaster raster = bufferedImage.getRaster();
//		DataBufferByte data = ( DataBufferByte ) raster.getDataBuffer();
//		return data.getData();
//	}
}
