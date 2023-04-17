package bvv.examples;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bvv.util.BvvFunctions;
import bvv.util.BvvOptions;
import bvv.util.BvvStackSource;
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

import java.io.IOException;
import java.util.Random;

public class Example08 {

    public static void main(String... args) {

        long[] imageVoxelSize = new long[] { 512, 512, 32 };
        int numberOfPoints = 800;

        //RandomAccessibleInterval<FloatType> labelImage = getFloatLabelImage(imageVoxelSize, numberOfPoints);
        //RandomAccessibleInterval<UnsignedByteType> labelImage = get8BitsLabelImage(imageVoxelSize, numberOfPoints);
        RandomAccessibleInterval<UnsignedShortType> labelImage = get16BitsLabelImage(imageVoxelSize, numberOfPoints);

        final BdvSource source = BdvFunctions.show(labelImage,"Label Image" );
        source.setColor(new ARGBType(0xFF00FF00));
        //source.setDisplayRange(0,1); for Float
        source.setDisplayRange(0,256);

        final SharedQueue queue = new SharedQueue( 7 );
        RandomAccessibleInterval<Volatile<UnsignedByteType>> rai = VolatileViews.wrapAsVolatile(get3DBorderLabelImage(labelImage), queue);
        BdvFunctions.show(rai, "Borders", BdvOptions.options().addTo(source));

        final BvvStackSource< ? > bvvSourceBorders = BvvFunctions.show( rai, "Borders");
        bvvSourceBorders.setDisplayRange( 0, 256*256 );

        final BvvStackSource< ? > bvvSourceLabels = BvvFunctions.show( labelImage, "Labels", BvvOptions.options().addTo(bvvSourceBorders));
        //bvvSourceLabels.setDisplayRange( 0, 256*256 );
        bvvSourceLabels.setColor(new ARGBType(0xFF00FF00));

    }

    public static RandomAccessibleInterval<FloatType> getFloatLabelImage(long[] imageVoxelSize, int numberOfPoints) {
        FinalInterval interval = new FinalInterval( imageVoxelSize );
        IterableRealInterval< FloatType > iterableFloat = createFloatRandomPoints( interval, numberOfPoints );
        // Get test label image
        RandomAccessibleInterval< FloatType > labelImage = getTestLabelImage(iterableFloat, imageVoxelSize);
        return labelImage;
    }

    public static RandomAccessibleInterval<UnsignedByteType> get8BitsLabelImage(long[] imageVoxelSize, int numberOfPoints) {
        FinalInterval interval = new FinalInterval( imageVoxelSize );
        IterableRealInterval< UnsignedByteType > iterableFloat = createByteRandomPoints( interval, numberOfPoints );
        // Get test label image
        RandomAccessibleInterval< UnsignedByteType > labelImage = getTestLabelImage(iterableFloat, imageVoxelSize);
        return labelImage;
    }

    public static RandomAccessibleInterval<UnsignedShortType> get16BitsLabelImage(long[] imageVoxelSize, int numberOfPoints) {
        FinalInterval interval = new FinalInterval( imageVoxelSize );
        IterableRealInterval< UnsignedShortType > iterableFloat = createShortRandomPoints( interval, numberOfPoints );
        // Get test label image
        RandomAccessibleInterval< UnsignedShortType > labelImage = getTestLabelImage(iterableFloat, imageVoxelSize);
        return labelImage;
    }

    public static <T extends Type<T> & Comparable<T> > Img<UnsignedByteType> get3DBorderLabelImage(RandomAccessibleInterval<T> lblImg) {
        // Make edge display on demand
        final int[] cellDimensions = new int[] { 32, 32, 32 };

        // Cached Image Factory Options
        final DiskCachedCellImgOptions factoryOptions = DiskCachedCellImgOptions.options()
                .cellDimensions( cellDimensions )
                .cacheType( DiskCachedCellImgOptions.CacheType.BOUNDED )
                .maxCacheSize( 100 );

        // Expand label image by one pixel to avoid out of bounds exception
        final RandomAccessibleInterval<T> lblImgWithBorder =  Views.expandBorder(lblImg, new long[] {1,1,1});

        // Creates cached image factory of Type Byte
        final DiskCachedCellImgFactory< UnsignedByteType > factory = new DiskCachedCellImgFactory<>( new UnsignedByteType(), factoryOptions );

        // Creates shifted views by one pixel in each dimension
        RandomAccessibleInterval<T> lblImgXShift = Views.translate(lblImgWithBorder, new long[] {1,0,0});
        RandomAccessibleInterval<T> lblImgYShift = Views.translate(lblImgWithBorder,new long[] {0,1,0});
        RandomAccessibleInterval<T> lblImgZShift = Views.translate(lblImgWithBorder,new long[] {0,0,1});

        // Creates border image, with cell Consumer method, which creates the image
        final Img<UnsignedByteType> borderLabel = factory.create( lblImg,  cell -> {

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
                    out.next().set( (byte) 126 );
                    inYS.next();
                    inZS.next();
                } else {
                    if (v.compareTo(inYS.next())!=0) {
                        out.next().set( (byte) 126 );
                        inZS.next();
                    } else {
                        if (v.compareTo(inZS.next())!=0) {
                            out.next().set( (byte) 126 );
                        } else {
                            out.next();
                        }
                    }
                }
            }
        }, factoryOptions.options().initializeCellsAsDirty( true ) );

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

    public static IterableRealInterval<UnsignedShortType> createShortRandomPoints(RealInterval interval, int numPoints) {
        // the number of dimensions
        int numDimensions = interval.numDimensions();

        // a random number generator
        Random rnd = new Random( 2001);//System.currentTimeMillis() );

        // a list of Samples with coordinates
        RealPointSampleList< UnsignedShortType > elements =
                new RealPointSampleList<>( numDimensions );

        for ( int i = 0; i < numPoints; ++i )
        {
            RealPoint point = new RealPoint( numDimensions );

            for ( int d = 0; d < numDimensions; ++d )
                point.setPosition( rnd.nextDouble() *
                        ( interval.realMax( d ) - interval.realMin( d ) ) + interval.realMin( d ), d );

            // add a new element with a random intensity in the range 0...1
            elements.add( point, new UnsignedShortType( rnd.nextInt(65535) ) );
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

    public static final <T extends NativeType<T>, A extends ArrayDataAccess<A>, CA extends ArrayDataAccess<CA>> CachedCellImg<T, CA> wrapAsCachedCellImg(
            final RandomAccessibleInterval<T> source,
            final int[] blockSize) throws IOException {

        final long[] dimensions = Intervals.dimensionsAsLongArray(source);
        final CellGrid grid = new CellGrid(dimensions, blockSize);

        final RandomAccessibleCacheLoader<T, A, CA> loader = RandomAccessibleCacheLoader.get(grid, Views.zeroMin(source), AccessFlags.setOf());
        return new ReadOnlyCachedCellImgFactory().createWithCacheLoader(dimensions, source.randomAccess().get(), loader);
    }
}
