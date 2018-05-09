package tpietzsch.blockmath3;

import bdv.img.cache.VolatileCachedCellImg;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.BenchmarkHelper;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import tpietzsch.day10.BlockKey;
import tpietzsch.day8.BlockTextureUtils;

/**
 * Benchmarking block extraction from CellImg
 */
public class Benchmark1
{
	private final List< RaiLevel> raiLevels;

	private final int[] blockSize = { 32, 32, 32 };

	private final int[] paddedBlockSize = { 34, 34, 34 };

	private final int[] cachePadOffset = { 1, 1, 1 };

	public Benchmark1( List< RaiLevel > raiLevels )
	{
		this.raiLevels = raiLevels;
	}

	public void loadBlock( final BlockKey key, final ByteBuffer buffer )
	{
		RandomAccessibleInterval< UnsignedShortType > rai = raiLevels.get( key.getLevel() ).rai;
		final int[] gridPos = key.getGridPos();
		long[] min = new long[ 3 ];
		long[] max = new long[ 3 ];
		for ( int d = 0; d < 3; ++d )
		{
			min[ d ] = gridPos[ d ] * blockSize[ d ] - cachePadOffset[ d ];
			max[ d ] = min[ d ] + paddedBlockSize[ d ] - 1;
		}
		BlockTextureUtils.imgToBuffer( Views.interval( Views.extendZero( rai ), min, max ), buffer );
	}

	public void loadTexture()
	{
		final RandomAccessibleInterval< UnsignedShortType > rai0 = raiLevels.get( 0 ).rai;
		final long sx = rai0.dimension( 0 );
		final long sy = rai0.dimension( 1 );
		final long sz = rai0.dimension( 2 );
		final ByteBuffer buffer = BlockTextureUtils.allocateBlockBuffer( paddedBlockSize );
		buffer.order( ByteOrder.LITTLE_ENDIAN );

		BenchmarkHelper.benchmarkAndPrint( 20,true, () -> {
			for ( RaiLevel raiLevel : raiLevels )
			{
				final RandomAccessibleInterval< UnsignedShortType > rai = raiLevel.rai;
				final int level = raiLevel.level;
				final int[] r = raiLevel.r;
				for ( int z = 0; z * blockSize[ 2 ] * r[ 2 ] < sz; ++z )
					for ( int y = 0; y * blockSize[ 1 ] * r[ 1 ] < sy; ++y )
						for ( int x = 0; x * blockSize[ 0 ] * r[ 0 ] < sx; ++x )
							loadBlock( new BlockKey( x, y, z, level ), buffer );
			}
		} );
	}

	public void loadBlockNew( final BlockKey key, final ByteBuffer buffer )
	{
		RandomAccessibleInterval< UnsignedShortType > rai = raiLevels.get( key.getLevel() ).rai;
		final int[] gridPos = key.getGridPos();
		int[] min = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * blockSize[ d ] - cachePadOffset[ d ];
		new Copier( rai, paddedBlockSize ).toBuffer( buffer, min );
	}

	public static class Copier
	{
		private final ArrayGridCopy3D gcopy = new ArrayGridCopy3D();

		private final Example1.ShortCellDataAccess dataAccess;

		private final Example1.ShortSubArrayCopy subArrayCopy = new Example1.ShortSubArrayCopy();

		private final CellGrid grid;

		private final int[] blocksize;

		private final short[] data;

		public Copier( RandomAccessibleInterval< UnsignedShortType > rai, final int[] blocksize )
		{
			final VolatileCachedCellImg< UnsignedShortType, ? > img = ( VolatileCachedCellImg< UnsignedShortType, ? > ) rai;
			grid = img.getCellGrid();
			dataAccess = new Example1.ShortCellDataAccess( ( RandomAccess ) img.getCells().randomAccess() );

			data = new short[ ( int ) Intervals.numElements( blocksize ) ];

			this.blocksize = blocksize;
		}

		public void toBuffer( final ByteBuffer buffer, final int[] min )
		{
			gcopy.copy( min, blocksize, grid, data, dataAccess, subArrayCopy );

			final ShortBuffer sbuffer = buffer.asShortBuffer();
			for ( int i = 0; i < data.length; i++ )
				sbuffer.put( i, data[ i ] );
		}
	}

	public void loadTextureNew()
	{
		final RandomAccessibleInterval< UnsignedShortType > rai0 = raiLevels.get( 0 ).rai;
		final long sx = rai0.dimension( 0 );
		final long sy = rai0.dimension( 1 );
		final long sz = rai0.dimension( 2 );
		final ByteBuffer buffer = BlockTextureUtils.allocateBlockBuffer( paddedBlockSize );
		buffer.order( ByteOrder.LITTLE_ENDIAN );

		BenchmarkHelper.benchmarkAndPrint( 20,true, () -> {
			for ( RaiLevel raiLevel : raiLevels )
			{
				final RandomAccessibleInterval< UnsignedShortType > rai = raiLevel.rai;
				final int level = raiLevel.level;
				final int[] r = raiLevel.r;
				for ( int z = 0; z * blockSize[ 2 ] * r[ 2 ] < sz; ++z )
					for ( int y = 0; y * blockSize[ 1 ] * r[ 1 ] < sy; ++y )
						for ( int x = 0; x * blockSize[ 0 ] * r[ 0 ] < sx; ++x )
							loadBlockNew( new BlockKey( x, y, z, level ), buffer );
			}
		} );
	}

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );

		MultiResolutionSetupImgLoader< UnsignedShortType > sil = ( MultiResolutionSetupImgLoader< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );

		ArrayList< RaiLevel > raiLevels = new ArrayList<>();
		final int numMipmapLevels = sil.numMipmapLevels();
		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			final RandomAccessibleInterval< UnsignedShortType > rai = sil.getImage( 1, level );
			final double[] resolution = sil.getMipmapResolutions()[ level ];
			final RaiLevel raiLevel = new RaiLevel( level, resolution, rai );
			raiLevels.add( raiLevel );
			System.out.println( raiLevel );
		}

		Benchmark1 benchmark = new Benchmark1( raiLevels );
		benchmark.loadTextureNew();
		benchmark.loadTexture();
		benchmark.loadTextureNew();
		benchmark.loadTexture();
	}
}
