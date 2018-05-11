package tpietzsch.blockmath3;

import bdv.ViewerSetupImgLoader;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.ImageJ;
import java.util.Arrays;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.AbstractShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;

public class Example1
{

	public static void main( String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > sil = ( ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );

		final RandomAccessibleInterval< UnsignedShortType > rai = sil.getImage( 1, 0 );
		System.out.println( "rai = " + rai );

		final VolatileCachedCellImg< UnsignedShortType, ? > img = ( VolatileCachedCellImg< UnsignedShortType, ? > ) rai;
		final CellGrid grid = img.getCellGrid();
		final RandomAccess< ? extends Cell< ? extends AbstractShortArray< ? > > > a = ( RandomAccess ) img.getCells().randomAccess();

		int[] dataDim = { 800, 800, 100 };
		int[] dataMin = { 300, -200, -30 };
		short[] data = new short[ ( int ) Intervals.numElements( dataDim ) ];


		long[] lsrcsize = new long[ 3 ];
		img.dimensions( lsrcsize );
		int[] srcsize = Util.long2int( lsrcsize );

		System.out.println( "dataDim = " + Arrays.toString( dataDim ) );
		System.out.println( "dataMin = " + Arrays.toString( dataMin ) );
		int[] cellsize = new int[ 3 ];
		grid.cellDimensions( cellsize );
		System.out.println( "cellsize = " + Arrays.toString( cellsize ) );
		System.out.println( "srcsize = " + srcsize );

		final ArrayGridCopy3D gcopy = new ArrayGridCopy3D();

		gcopy.copy( dataMin, dataDim, grid, data, new ArrayGridCopy3D.ShortCellDataAccess( a ), new ArrayGridCopy3D.ShortSubArrayCopy() );

		new ImageJ();
		ImageJFunctions.show( ArrayImgs.shorts( data, dataDim[ 0 ], dataDim[ 1 ], dataDim[ 2 ] ) );
	}
}
