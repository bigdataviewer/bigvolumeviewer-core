package bvv.debug;

import net.imglib2.Cursor;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class GenerateVolumes
{

	static < T extends RealType< T > & NativeType< T >> ArrayImg< T, ? > makeSimpleRAI(final T type, final int nEdge, final double minAmp, final double maxAmp, final boolean bConstantMax)
	{
		final long[] dims = new long[] {nEdge, nEdge, nEdge};
		ArrayImgFactory<T> factoryImg = new ArrayImgFactory<>(type);
		ArrayImg< T, ? > img = factoryImg.create( dims );
		ArrayLocalizingCursor< T > cursor = img.localizingCursor();
		double period = nEdge * 0.5 + Math.random() *  nEdge * 0.5;

		final double [] pos = new double[3];
		while(cursor.hasNext())
		{
			cursor.fwd();
			cursor.localize( pos );
			if(bConstantMax)
			{
				cursor.get().setReal( maxAmp ); 
			}
			else
			{
				double val = gyroid(pos, period, minAmp, maxAmp);
				cursor.get().setReal( val ); 
			}
		}
		return img;
	}
	
	/** creates a 3D cube volume with nEdge size filled with gyroid in the range of minAmp and maxAmp.
	 *  The period of gyroid randomly varies from 0.5 to 1.0 nEdge
	 *  If bConstantMax is true, single values volume filled with maxAmp**/
	static < T extends RealType< T > & NativeType< T >> Img< T > makeCachedCellImg(final T type, final int nEdge, final double minAmp, final double maxAmp, final boolean bConstantMax)
	{
		final long[] dims = new long[] {nEdge, nEdge, nEdge};
		final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(
				ReadOnlyCachedCellImgOptions.options().cellDimensions( 32 ) );
		double period = nEdge * 0.5 + Math.random() *  nEdge * 0.5;
		final Img< T > cellimg = factory.create( dims, type, cell -> {
			Cursor< T > cursor = cell.localizingCursor();
			final double [] pos = new double[3];
			while(cursor.hasNext())
			{
				cursor.fwd();
				cursor.localize( pos );
				if( bConstantMax)
				{
					cursor.get().setReal( maxAmp ); 
				}
				else
				{
					final double val = gyroid(pos, period, minAmp, maxAmp);
					cursor.get().setReal( val ); 					
				}
			}
			//Thread.sleep( 80 );
		});

		return cellimg;
	}


	static double gyroid(final double [] pos, final double period, final double minAmp, final double maxAmp) 
	{
		double w = 2.0 * Math.PI / period;

		double g = Math.sin(pos[0] * w ) * Math.cos(pos[1] * w) 
				+ Math.sin(pos[1] * w ) * Math.cos(pos[2] * w ) 
				+ Math.sin(pos[2] * w ) * Math.cos(pos[0] * w );
		g = Math.pow((Math.tanh( g ) + 1) * 0.5, 7);
		return g  * (maxAmp - minAmp) + minAmp;
	}
}