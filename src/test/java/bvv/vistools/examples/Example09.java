package bvv.vistools.examples;

import bdv.cache.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;
import bvv.vistools.BvvStackSource;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.RandomAccessibleCacheLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import java.util.Random;

/**
 * Show both a 8-bit and a 16-bit cached image
 * A precomputed label image made of 3D voronoi labels is displayed and an on-the-fly computed
 * image consisting of the border between the different labels is displayed.
 *
 * In order to show the on the fly computation, there's a sleep time of 1 second for each computed block (64 64 64)
 *
 * @author Nicolas Chiaruttini, EPFL, 2025
 */
public class Example09 {

    public static void main(String... args) {

        long[] imageVoxelSize = new long[] { 512, 512, 512 };
        int numberOfPoints = 800;

        RandomAccessibleInterval<UnsignedByteType> labelImage_1 = get8BitsLabelImage(imageVoxelSize, numberOfPoints);

        RandomAccessibleInterval<UnsignedByteType> labelImage_2 = get8BitsLabelImage(imageVoxelSize, numberOfPoints/2);

        final SharedQueue queue_1 = new SharedQueue( Runtime.getRuntime().availableProcessors()-1 );
        Img<UnsignedByteType> nonVRAI8bits_1 = get3DBorderLabelImage8Bits(labelImage_1, 64, 64, 64);
        RandomAccessibleInterval<Volatile<UnsignedByteType>> rai8bits_1 = VolatileViews.wrapAsVolatile(nonVRAI8bits_1, queue_1);

        final SharedQueue queue_2 = new SharedQueue( Runtime.getRuntime().availableProcessors()-1 );
        Img<UnsignedByteType> nonVRAI8bits_2 = get3DBorderLabelImage8Bits(labelImage_2, 32, 32, 32);
        RandomAccessibleInterval<Volatile<UnsignedByteType>> rai8bits_2 = VolatileViews.wrapAsVolatile(nonVRAI8bits_2, queue_2);

        Img<UnsignedShortType> nonVRAI16bits = get3DBorderLabelImage16Bits(labelImage_1);
        RandomAccessibleInterval<Volatile<UnsignedShortType>> rai16bits = VolatileViews.wrapAsVolatile(nonVRAI16bits, queue_1);

        final BvvStackSource< ? > bvvSourceBorders8bits_1 = BvvFunctions.show( rai8bits_1, "Borders 8 bits 1");
        bvvSourceBorders8bits_1.setDisplayRange( 0, 2 );
        bvvSourceBorders8bits_1.setColor(new ARGBType(0x00FF00FF));


        final BvvStackSource< ? > bvvSourceBorders8bits_2 = BvvFunctions.show( rai8bits_2, "Borders 8 bits 2", BvvOptions.options().addTo(bvvSourceBorders8bits_1));
        bvvSourceBorders8bits_2.setDisplayRange( 0, 2 );
        bvvSourceBorders8bits_2.setColor(new ARGBType(0x000000FF));

        final BvvStackSource< ? > bvvSourceBorders16bits = BvvFunctions.show( rai16bits, "Borders 16 bits", BvvOptions.options().addTo(bvvSourceBorders8bits_1));
        bvvSourceBorders16bits.setDisplayRange( 0, 512 );
        bvvSourceBorders16bits.setColor(new ARGBType(0x0000FFFF));

    }

    public static RandomAccessibleInterval<FloatType> getFloatLabelImage(long[] imageVoxelSize, int numberOfPoints) {
        FinalInterval interval = new FinalInterval( imageVoxelSize );
        IterableRealInterval< FloatType > iterableFloat = createFloatRandomPoints( interval, numberOfPoints );
        // Get test label image
        return getTestLabelImage(iterableFloat, imageVoxelSize);
    }

    public static RandomAccessibleInterval<UnsignedByteType> get8BitsLabelImage(long[] imageVoxelSize, int numberOfPoints) {
        FinalInterval interval = new FinalInterval( imageVoxelSize );
        IterableRealInterval< UnsignedByteType > iterableFloat = createByteRandomPoints( interval, numberOfPoints );
        // Get test label image
        return getTestLabelImage(iterableFloat, imageVoxelSize);
    }

    public static <T extends Type<T> & Comparable<T> > Img<UnsignedByteType> get3DBorderLabelImage8Bits(RandomAccessibleInterval<T> lblImg, int... cellDs) {
        // Make edge display on demand
        final int[] cellDimensions = cellDs;//new int[] { 64, 64, 64 };

        // Cached Image Factory Options
        final DiskCachedCellImgOptions factoryOptions = DiskCachedCellImgOptions.options()
                .cellDimensions( cellDimensions )
                .cacheType( DiskCachedCellImgOptions.CacheType.BOUNDED )
                .maxCacheSize( 100 );

        // Expand label image by one pixel to avoid out of bounds exception
        final RandomAccessibleInterval<T> lblImgWithBorder =  Views.expandBorder(lblImg, 1,1,1);

        // Creates cached image factory of Type Byte
        final DiskCachedCellImgFactory< UnsignedByteType > factory = new DiskCachedCellImgFactory<>( new UnsignedByteType(), factoryOptions );

        // Creates shifted views by one pixel in each dimension
        RandomAccessibleInterval<T> lblImgXShift = Views.translate(lblImgWithBorder, 1,0,0);
        RandomAccessibleInterval<T> lblImgYShift = Views.translate(lblImgWithBorder, 0,1,0);
        RandomAccessibleInterval<T> lblImgZShift = Views.translate(lblImgWithBorder, 0,0,1);

        // Creates border image, with cell Consumer method, which creates the image
        final Img<UnsignedByteType> borderLabel = factory.create( lblImg,  cell -> {

            Thread.sleep(1000);

            // Cursor on the source image
            final Cursor<T> inNS = Views.flatIterable( Views.interval( lblImg, cell ) ).cursor();

            // Cursor on shifted source image
            final Cursor<T> inXS = Views.flatIterable( Views.interval( lblImgXShift, cell ) ).cursor();
            final Cursor<T> inYS = Views.flatIterable( Views.interval( lblImgYShift, cell ) ).cursor();
            final Cursor<T> inZS = Views.flatIterable( Views.interval( lblImgZShift, cell ) ).cursor();

            // Cursor on output image
            final Cursor<UnsignedByteType> out = Views.flatIterable( cell ).cursor();

            // Loops through voxels
            while ( out.hasNext() ) {
                T v = inNS.next();
                if (v.compareTo(inXS.next())!=0) {
                    out.next().set( (byte) 1 );
                    inYS.next();
                    inZS.next();
                } else {
                    if (v.compareTo(inYS.next())!=0) {
                        out.next().set( (byte) 2 );
                        inZS.next();
                    } else {
                        if (v.compareTo(inZS.next())!=0) {
                            out.next().set( (byte) 3 );
                        } else {
                            out.next();
                        }
                    }
                }
            }
        }, DiskCachedCellImgOptions.options().initializeCellsAsDirty( true ) );

        return borderLabel;
    }

    public static <T extends Type<T> & Comparable<T> > Img<UnsignedShortType> get3DBorderLabelImage16Bits(RandomAccessibleInterval<T> lblImg) {
        // Make edge display on demand
        final int[] cellDimensions = new int[] { 64, 64, 64 };

        // Cached Image Factory Options
        final DiskCachedCellImgOptions factoryOptions = DiskCachedCellImgOptions.options()
                .cellDimensions( cellDimensions )
                .cacheType( DiskCachedCellImgOptions.CacheType.BOUNDED )
                .maxCacheSize( 100 );

        // Expand label image by one pixel to avoid out of bounds exception
        final RandomAccessibleInterval<T> lblImgWithBorder =  Views.expandBorder(lblImg, 1,1,1);

        // Creates cached image factory of Type Byte
        final DiskCachedCellImgFactory< UnsignedShortType > factory = new DiskCachedCellImgFactory<>( new UnsignedShortType(), factoryOptions );

        // Creates shifted views by one pixel in each dimension
        RandomAccessibleInterval<T> lblImgXShift = Views.translate(lblImgWithBorder, 1,0,0);
        RandomAccessibleInterval<T> lblImgYShift = Views.translate(lblImgWithBorder, 0,1,0);
        RandomAccessibleInterval<T> lblImgZShift = Views.translate(lblImgWithBorder, 0,0,1);

        // Creates border image, with cell Consumer method, which creates the image
        final Img<UnsignedShortType> borderLabel = factory.create( lblImg,  cell -> {

            Thread.sleep(1000);

            // Cursor on the source image
            final Cursor<T> inNS = Views.flatIterable( Views.interval( lblImg, cell ) ).cursor();

            // Cursor on shifted source image
            final Cursor<T> inXS = Views.flatIterable( Views.interval( lblImgXShift, cell ) ).cursor();
            final Cursor<T> inYS = Views.flatIterable( Views.interval( lblImgYShift, cell ) ).cursor();
            final Cursor<T> inZS = Views.flatIterable( Views.interval( lblImgZShift, cell ) ).cursor();

            // Cursor on output image
            final Cursor<UnsignedShortType> out = Views.flatIterable( cell ).cursor();

            // Loops through voxels
            while ( out.hasNext() ) {
                T v = inNS.next();
                if (v.compareTo(inXS.next())!=0) {
                    out.next().set( (byte) 255 );
                    inYS.next();
                    inZS.next();
                } else {
                    if (v.compareTo(inYS.next())!=0) {
                        out.next().set( (byte) 512 );
                        inZS.next();
                    } else {
                        if (v.compareTo(inZS.next())!=0) {
                            out.next().set( (byte) 1024 );
                        } else {
                            out.next();
                        }
                    }
                }
            }
        }, DiskCachedCellImgOptions.options().initializeCellsAsDirty( true ) );

        return borderLabel;
    }


    //------------------------------------- METHODS TO CREATE TEST LABEL IMAGE

    public static <T extends RealType<T>&NativeType<T>> RandomAccessibleInterval< T > getTestLabelImage(IterableRealInterval< T > realInterval, final long[] imgTestSize) {

        // the interval in which to create random points

        // create an IterableRealInterval
        // using nearest neighbor search we will be able to return a value an any position in space
        NearestNeighborSearch< T > search =
                new NearestNeighborSearchOnKDTree<>(
                        new KDTree<>( realInterval ) );

        // make it into RealRandomAccessible using nearest neighbor search
        RealRandomAccessible< T > realRandomAccessible =
                Views.interpolate( search, new NearestNeighborSearchInterpolatorFactory<>() );

        // convert it into a RandomAccessible which can be displayed
        RandomAccessible< T > randomAccessible = Views.raster( realRandomAccessible );

        // set the initial interval as area to view
        RandomAccessibleInterval< T > labelImage = Views.interval( randomAccessible, new FinalInterval(imgTestSize) );

        final RandomAccessibleInterval< T > labelImageCopy = new ArrayImgFactory( Util.getTypeFromInterval( labelImage ) ).create( labelImage );

        // Image copied to avoid computing it on the fly
        // https://github.com/imglib/imglib2-algorithm/blob/47cd6ed5c97cca4b316c92d4d3260086a335544d/src/main/java/net/imglib2/algorithm/util/Grids.java#L221 used for parallel copy
        Grids.collectAllContainedIntervals(imgTestSize, new int[] {128,128,32}).parallelStream().forEach( blockinterval->
                copy(labelImage, Views.interval(labelImageCopy, blockinterval))
        );

        // Returning it
        return labelImageCopy;
    }


    /**
     * Copy from a source that is just RandomAccessible to an IterableInterval. Latter one defines
     * size and location of the copy operation. It will query the same pixel locations of the
     * IterableInterval in the RandomAccessible. It is up to the developer to ensure that these
     * coordinates match.
     *
     * Note that both, input and output could be Views, Img or anything that implements
     * those interfaces.
     *
     * @param source - a RandomAccess as source that can be infinite
     * @param target - an IterableInterval as target
     */
    public static < T extends Type< T >> void copy(final RandomAccessible< T > source,
                                                   final IterableInterval< T > target )
    {
        // create a cursor that automatically localizes itself on every move
        Cursor< T > targetCursor = target.localizingCursor();
        RandomAccess< T > sourceRandomAccess = source.randomAccess();

        // iterate over the input cursor
        while ( targetCursor.hasNext())
        {
            // move input cursor forward
            targetCursor.fwd();

            // set the output cursor to the position of the input cursor
            sourceRandomAccess.setPosition( targetCursor );

            // set the value of this pixel of the output image, every Type supports T.set( T type )
            targetCursor.get().set( sourceRandomAccess.get() );
        }

    }


    /**
     * Create a number of n-dimensional random points in a certain interval
     * having a random intensity 0...1
     *
     * @param interval - the interval in which points are created
     * @param numPoints - the amount of points
     *
     * @return a RealPointSampleList (which is an IterableRealInterval)
     */
    public static RealPointSampleList<FloatType> createFloatRandomPoints(
            RealInterval interval, int numPoints )
    {
        // the number of dimensions
        int numDimensions = interval.numDimensions();

        // a random number generator
        Random rnd = new Random( 2001);//System.currentTimeMillis() );

        // a list of Samples with coordinates
        RealPointSampleList< FloatType > elements =
                new RealPointSampleList<>( numDimensions );

        for ( int i = 0; i < numPoints; ++i )
        {
            RealPoint point = new RealPoint( numDimensions );

            for ( int d = 0; d < numDimensions; ++d )
                point.setPosition( rnd.nextDouble() *
                        ( interval.realMax( d ) - interval.realMin( d ) ) + interval.realMin( d ), d );

            // add a new element with a random intensity in the range 0...1
            elements.add( point, new FloatType( rnd.nextFloat() ) );
        }

        return elements;
    }

    /**
     * Create a number of n-dimensional random points in a certain interval
     * having a random intensity 0...1
     *
     * @param interval - the interval in which points are created
     * @param numPoints - the amount of points
     *
     * @return a RealPointSampleList (which is an IterableRealInterval)
     */
    public static RealPointSampleList<UnsignedByteType> createByteRandomPoints(
            RealInterval interval, int numPoints )
    {
        // the number of dimensions
        int numDimensions = interval.numDimensions();

        // a random number generator
        Random rnd = new Random( 2001);//System.currentTimeMillis() );

        // a list of Samples with coordinates
        RealPointSampleList< UnsignedByteType > elements =
                new RealPointSampleList<>( numDimensions );

        for ( int i = 0; i < numPoints; ++i )
        {
            RealPoint point = new RealPoint( numDimensions );

            for ( int d = 0; d < numDimensions; ++d )
                point.setPosition( rnd.nextDouble() *
                        ( interval.realMax( d ) - interval.realMin( d ) ) + interval.realMin( d ), d );

            // add a new element with a random intensity in the range 0...1
            elements.add( point, new UnsignedByteType( rnd.nextInt(255) ) );
        }

        return elements;
    }

}
