package tpietzsch.example2;

import bdv.util.volatiles.VolatileViews;
import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import java.io.IOException;
import java.util.Arrays;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

public class Example03
{
	public static class CheckerboardLoader implements CellLoader< UnsignedShortType >
	{
		private final CellGrid grid;

		public CheckerboardLoader( final CellGrid grid )
		{
			this.grid = grid;
		}

		@Override
		public void load( final SingleCellArrayImg< UnsignedShortType, ? > cell ) throws Exception
		{
			final int n = grid.numDimensions();
			long sum = 0;
			for ( int d = 0; d < n; ++d )
				sum += cell.min( d ) / grid.cellDimension( d );
			final short color = ( short ) ( ( sum & 0x01 ) == 0 ? 0x0000 : 0xffff );

			Thread.sleep( 10 );
			Arrays.fill( ( short[] ) cell.getStorageArray(), color );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 32, 32, 32 };
		final long[] dimensions = new long[] { 256, 256, 256 };
//		final long[] dimensions = new long[] { 640, 640, 640 };

		final DiskCachedCellImgFactory< UnsignedShortType > factory = new DiskCachedCellImgFactory<>( new UnsignedShortType(), options()
				.cellDimensions( cellDimensions )
				.cacheType( DiskCachedCellImgOptions.CacheType.BOUNDED )
				.maxCacheSize( 100 ) );
		final CheckerboardLoader loader = new CheckerboardLoader( new CellGrid( dimensions, cellDimensions ) );
		final Img< UnsignedShortType > img = factory.create( dimensions, loader );

		final Bvv bvv = BvvFunctions.show( VolatileViews.wrapAsVolatile( img ), "Cached", Bvv.options().maxCacheSizeInMB( 1024 ) );
//		final Bvv bvv = BvvFunctions.show( img, "Cached", Bvv.options().maxCacheSizeInMB( 1024 ) );
	}
}
