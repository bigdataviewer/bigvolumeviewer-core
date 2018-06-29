package tpietzsch.blockmath3;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.BenchmarkHelper;
import tpietzsch.blocks.ByteUtils;
import tpietzsch.blocks.CopyGridBlock;
import tpietzsch.blocks.CopySubArray;
import tpietzsch.blocks.CopySubArrayImp;
import tpietzsch.blocks.CopySubArrayImp2;
import tpietzsch.blocks.GridDataAccess;
import tpietzsch.blocks.GridDataAccessImp;
import tpietzsch.day10.BlockKey;
import tpietzsch.day8.BlockTextureUtils;

import static tpietzsch.blocks.ByteUtils.addressOf;

/**
 * Benchmarking block extraction from CellImg
 */
public class Benchmark2
{
	private final List< RaiLevel> raiLevels;

	private final int[] blockSize = { 32, 32, 32 };

	private final int[] paddedBlockSize = { 34, 34, 34 };

	private final int[] cachePadOffset = { 1, 1, 1 };

	public Benchmark2( List< RaiLevel > raiLevels )
	{
		this.raiLevels = raiLevels;
	}

	public void loadBlock( final BlockKey key, final ByteBuffer buffer )
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
		private final CopyGridBlock gcopy = new CopyGridBlock();

		private final GridDataAccess< short[] > dataAccess;

		private final CopySubArray< short[], ByteUtils.Address > subArrayCopy = new CopySubArrayImp.ShortToAddress();

		private final int[] blocksize;

		public Copier( final RandomAccessibleInterval< UnsignedShortType > rai, final int[] blocksize )
		{
			dataAccess = new GridDataAccessImp.Cells<>( ( AbstractCellImg ) rai );
			this.blocksize = blocksize;
		}

		/**
		 * @return {@code true}, if this block can be completely loaded from data currently in the cache
		 */
		public boolean canLoadCompletely( final int[] min )
		{
			return gcopy.canLoadCompletely( min, blocksize, dataAccess );
		}

		/**
		 * @return {@code true}, if this block was completely loaded
		 */
		public boolean toBuffer( final ByteBuffer buffer, final int[] min )
		{
			final long address = addressOf( buffer );
			final boolean complete = gcopy.copy( min, blocksize, () -> address /* TODO */, dataAccess, subArrayCopy );
			return complete;
		}
	}

	public void loadTexture()
	{
		final RandomAccessibleInterval< UnsignedShortType > rai0 = raiLevels.get( 0 ).rai;
		final long sx = rai0.dimension( 0 );
		final long sy = rai0.dimension( 1 );
		final long sz = rai0.dimension( 2 );
		final ByteBuffer buffer = BlockTextureUtils.allocateBlockBuffer( paddedBlockSize );
		buffer.order( ByteOrder.LITTLE_ENDIAN );

		BenchmarkHelper.benchmarkAndPrint( 20,false, () -> {
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
		new CopierNew( rai, paddedBlockSize ).toBuffer( buffer, min );
	}

	public static class CopierNew
	{
		private final CopyGridBlock gcopy = new CopyGridBlock();

		private final GridDataAccess< short[] > dataAccess;

		private final CopySubArray< short[], ByteUtils.Address > subArrayCopy = new CopySubArrayImp2.ShortToAddress();

		private final int[] blocksize;

		public CopierNew( final RandomAccessibleInterval< UnsignedShortType > rai, final int[] blocksize )
		{
			dataAccess = new GridDataAccessImp.Cells<>( ( AbstractCellImg ) rai );

			this.blocksize = blocksize;
		}

		/**
		 * @return {@code true}, if this block can be completely loaded from data currently in the cache
		 */
		public boolean canLoadCompletely( final int[] min )
		{
			return gcopy.canLoadCompletely( min, blocksize, dataAccess );
		}

		/**
		 * @return {@code true}, if this block was completely loaded
		 */
		public boolean toBuffer( final ByteBuffer buffer, final int[] min )
		{
			final long address = addressOf( buffer );
			final boolean complete = gcopy.copy( min, blocksize, () -> address /* TODO */, dataAccess, subArrayCopy );
			return complete;
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

		BenchmarkHelper.benchmarkAndPrint( 20,false, () -> {
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

		Benchmark2 benchmark = new Benchmark2( raiLevels );

		for ( int i = 0; i < 20; ++i )
		{
			System.out.println( "new:" );
			benchmark.loadTextureNew();

			System.out.println( "old:" );
			benchmark.loadTexture();
			System.out.println( "==============\n" );
		}
	}
}
