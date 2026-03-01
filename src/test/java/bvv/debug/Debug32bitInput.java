package bvv.debug;

import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import bdv.util.volatiles.VolatileViews;
import bvv.vistools.Bvv;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;

import bvv.vistools.BvvStackSource;

/** Test all three data types (unsigned byte/short + float) in one BVV 
 * using two modes: "simplevolume" and "multiresolution" cached **/

public class Debug32bitInput
{
	@SuppressWarnings( "unchecked" )
	public static < T extends RealType< T > & NativeType< T >> void main( final String[] args )
	{
		Bvv bvv = BvvFunctions.show( BvvOptions.options().frameTitle( "Test all types" ));
		
		//setup testing types
		ArrayList<Object> types = new ArrayList<>();
		types.add( new UnsignedByteType() );
		types.add( new UnsignedShortType() );
		types.add( new FloatType() );
		
		String [] typeNamesSimple = new String [] {"SimpleUByte","SimpleUShort", "SimpleFloat"};
		String [] typeNamesCellImg = new String [] {"CellUByte","CellUShort", "CellFloat"};
		
		final double [][] minmax = new double [3][2];
		//limits of the image values
		//unsigned byte
		minmax[0][0] = 0;
		minmax[0][1] = 255;
		//unsigned short
		minmax[1][0] = 0;
		minmax[1][1] = 65535;
		//float, let's get negative
		minmax[2][0] = -1.0;
		minmax[2][1] = 1.0;
		
		//colors 
		ARGBType [] colors = new ARGBType[3];
		colors[0] = new ARGBType( 0xffff0000 );
		colors[1] = new ARGBType( 0xff00ff00 );
		colors[2] = new ARGBType( 0xff0000ff );
		
		////////////////////////
		// test simple volumes
		///////////////////////
		
		AffineTransform3D transform = new AffineTransform3D();
		
		for(int i = 0; i < 3; i++)
		{
			final RandomAccessibleInterval< ? > rai = makeSimpleRAI((T)types.get( i ), minmax[i][0], minmax[i][1] );
			BvvStackSource< ? > source = BvvFunctions.show( rai, 
					typeNamesSimple[i], 
					BvvOptions.options().addTo( bvv ).sourceTransform( transform ) );
			source.setDisplayRangeBounds( minmax[i][0], minmax[i][1] );
			source.setDisplayRange( minmax[i][0], minmax[i][1] );
			source.setColor( colors[i] );
			transform.translate( 110., 0., 0.);
		}

		////////////////////////
		// test multiresolution (well, cached)
		///////////////////////
		
		transform = new AffineTransform3D();
		
		transform.translate( 0, 110, 0 );
		
		for(int i = 0; i < 3; i++)
		{
			final Img< ? > rai = makeCachedCellImg((T)types.get( i ), minmax[i][0], minmax[i][1] );
			BvvStackSource< ? > source = BvvFunctions.show( VolatileViews.wrapAsVolatile(rai), 
					typeNamesCellImg[i], 
					BvvOptions.options().addTo( bvv ).sourceTransform( transform ) );
			source.setDisplayRangeBounds( minmax[i][0], minmax[i][1] );
			source.setDisplayRange( minmax[i][0], minmax[i][1] );
			source.setColor( colors[i] );
			transform.translate( 110., 0., 0.);
		}

	}
	
	static < T extends RealType< T > & NativeType< T >> ArrayImg< T, ? > makeSimpleRAI(final T type, final double min, final double max)
	{
		final long[] dims = new long[] {100, 100, 100};
		ArrayImgFactory<T> factoryImg = new ArrayImgFactory<>(type);
		ArrayImg< T, ? > img = factoryImg.create( dims );
		ArrayLocalizingCursor< T > cursor = img.localizingCursor();

		final double [] pos = new double[3];
		while(cursor.hasNext())
		{
			cursor.fwd();
			cursor.localize( pos );
			double val = gyroid(pos, 25, min, max);
			cursor.get().setReal( val ); 
		}
		return img;
	}
	
	static < T extends RealType< T > & NativeType< T >> Img< T > makeCachedCellImg(final T type, final double min, final double max)
	{
		final long[] dims = new long[] {100, 100, 100};
		final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(
				ReadOnlyCachedCellImgOptions.options().cellDimensions( 32, 32, 32 ) );
			
		final Img< T > cellimg = factory.create( dims, type, cell -> {
			Cursor< T > cursor = cell.localizingCursor();
			final double [] pos = new double[3];
			while(cursor.hasNext())
			{
				cursor.fwd();
				cursor.localize( pos );
				double val = gyroid(pos, 25, min, max);
				cursor.get().setReal( val ); 
			}
			Thread.sleep( 80 );
		});

		return cellimg;
	}
	
	static double gyroid(final double [] pos, final double period, final double minAmp, final double maxAmp) {
		double w = 2.0 * Math.PI / period;
		double g = Math.sin(pos[0] * w) * Math.cos(pos[1] * w) + Math.sin(pos[1] * w) * Math.cos(pos[2] * w) + Math.sin(pos[2] * w) * Math.cos(pos[0] * w);
		g = Math.pow((Math.tanh( g ) + 1) * 0.5, 7);
		return g  * (maxAmp - minAmp) + minAmp;
		}
}
