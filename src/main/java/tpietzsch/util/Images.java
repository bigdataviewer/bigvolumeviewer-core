package tpietzsch.util;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
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
