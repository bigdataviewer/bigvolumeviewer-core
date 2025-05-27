/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
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
package bvv.vistools;

import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.TransformedSource;
import bdv.util.AxisOrder;
import bdv.util.Bdv;
import bdv.util.BdvHandle;
import bdv.util.BdvHandleFrame;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.util.RandomAccessibleIntervalSource4D;
import bdv.util.RandomAccessibleSource;
import bdv.util.RandomAccessibleSource4D;
import bdv.util.volatiles.VolatileView;
import bdv.util.volatiles.VolatileViewData;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;

/**
 * all show methods return a {@link Bvv} which can be used to add more stuff to the same window
 *
 *
 * @author Tobias Pietzsch
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 * @author Stephan Saalfeld
 */
public class BvvFunctions
{
	public static < T > BvvStackSource< T > show(
			final RandomAccessibleInterval< T > img,
			final String name )
	{
		return show( img, name, Bvv.options() );
	}

	public static Bvv show( final BvvOptions options )
	{
		final BvvHandle handle = getHandle( options );
		handle.createViewer( Collections.emptyList(), Collections.emptyList(), 1 );
		return handle;
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static < T > BvvStackSource< T > show(
			final RandomAccessibleInterval< T > img,
			final String name,
			final BvvOptions options )
	{
		final BvvHandle handle = getHandle( options );
		final AxisOrder axisOrder = AxisOrder.getAxisOrder( options.values.axisOrder(), img, false );
		final AffineTransform3D sourceTransform = options.values.getSourceTransform();
		final T type;
		if ( img instanceof VolatileView )
		{
			final VolatileViewData< ?, ? > viewData = ( ( VolatileView< ?, ? > ) img ).getVolatileViewData();
			type = ( T ) viewData.getVolatileType();
			handle.getCacheControls().addCacheControl( viewData.getCacheControl() );
		}
		else
			type = Util.getTypeFromInterval( img );

		return addRandomAccessibleInterval( handle, ( RandomAccessibleInterval ) img, ( NumericType ) type, name, axisOrder, sourceTransform );
	}

	public static < T > BvvStackSource< T > show(
			final Source< T > source )
	{
		return show( source, Bvv.options() );
	}

	public static < T > BvvStackSource< T > show(
			final Source< T > source,
			final BvvOptions options )
	{
		return show( source, 1, options );
	}

	public static < T > BvvStackSource< T > show(
			final Source< T > source,
			final int numTimePoints )
	{
		return show( source, numTimePoints, Bvv.options() );
	}

	public static < T > BvvStackSource< T > show(
			final Source< T > source,
			final int numTimePoints,
			final BvvOptions options )
	{
		final BvvHandle handle = getHandle( options );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final BvvStackSource< T > stackSource = addSource( handle, ( Source ) source, numTimePoints );
		return stackSource;
	}

	public static < T > BvvStackSource< T > show(
			final SourceAndConverter< T > source )
	{
		return show( source, Bvv.options() );
	}

	public static < T > BvvStackSource< T > show(
			final SourceAndConverter< T > source,
			final BvvOptions options )
	{
		return show( source, 1, options );
	}

	public static < T > BvvStackSource< T > show(
			final SourceAndConverter< T > source,
			final int numTimePoints )
	{
		return show( source, numTimePoints, Bvv.options() );
	}

	public static < T > BvvStackSource< T > show(
			final SourceAndConverter< T > soc,
			final int numTimepoints,
			final BvvOptions options )
	{
		final BvvHandle handle = getHandle( options );
		final T type = soc.getSpimSource().getType();
		final int setupId = handle.getUnusedSetupId();
		final List< ConverterSetup > converterSetups = Collections.singletonList( BigDataViewer.createConverterSetup( soc, setupId ) );
		final List< SourceAndConverter< T > > sources = Collections.singletonList( soc );
		handle.add( converterSetups, sources, numTimepoints );
		final BvvStackSource< T > bdvSource = new BvvStackSource<>( handle, numTimepoints, type, converterSetups, sources );
		handle.addBvvSource( bdvSource );
		return bdvSource;
	}

	public static List< BvvStackSource< ? > > show(
			final AbstractSpimData< ? > spimData )
	{
		return show( spimData, Bvv.options() );
	}

	public static List< BvvStackSource< ? > > show(
			final AbstractSpimData< ? > spimData,
			final BvvOptions options )
	{
		final BvvHandle handle = getHandle( options );
		final AbstractSequenceDescription< ?, ?, ? > seq = spimData.getSequenceDescription();
		final int numTimepoints = seq.getTimePoints().size();
		final VolatileGlobalCellCache cache = ( VolatileGlobalCellCache ) ( ( ViewerImgLoader ) seq.getImgLoader() ).getCacheControl();
		handle.getBvvHandle().getCacheControls().addCacheControl( cache );
		cache.clearCache();

		WrapBasicImgLoader.wrapImgLoaderIfNecessary( spimData );
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		BigDataViewer.initSetups( spimData, new ArrayList<>(), sources );

		final List< BvvStackSource< ? > > bvvSources = new ArrayList<>();
		for ( final SourceAndConverter< ? > source : sources )
			bvvSources.add( addSpimDataSource( handle, source, numTimepoints ) );

		WrapBasicImgLoader.removeWrapperIfPresent( spimData );
		return bvvSources;
	}

	// TODO: move to BdvFunctionUtils
	public static int getUnusedSetupId( final BigDataViewer bdv )
	{
		return getUnusedSetupId( bdv.getSetupAssignments() );
	}

	// TODO: move to BdvFunctionUtils
	public static synchronized int getUnusedSetupId( final SetupAssignments setupAssignments )
	{
		return SetupAssignments.getUnusedSetupId( setupAssignments );
	}

	/**
	 * Get existing {@code BvvHandle} from {@code options} or create a new
	 * {@code BvvHandleFrame}.
	 */
	private static BvvHandle getHandle( final BvvOptions options )
	{
		final Bvv bvv = options.values.addTo();
		return ( bvv == null )
				? new BvvHandleFrame( options )
				: bvv.getBvvHandle();
	}

	/**
	 * Add the given {@link RandomAccessibleInterval} {@code img} to the given
	 * {@link BvvHandle} as a new {@link BvvStackSource}. The {@code img} is
	 * expected to be 2D, 3D, 4D, or 5D with the given {@link AxisOrder}.
	 *
	 * @param handle
	 *            handle to add the {@code img} to.
	 * @param img
	 *            {@link RandomAccessibleInterval} to add.
	 * @param type
	 *            instance of the {@code img} type.
	 * @param name
	 *            name to give to the new source
	 * @param axisOrder
	 *            {@link AxisOrder} of the source, is used to appropriately split {@code img} into channels and timepoints.
	 * @param sourceTransform
	 *            transforms from source coordinates to global coordinates.
	 * @return a new {@link BdvStackSource} handle for the newly added source(s).
	 */
	private static < T extends NumericType< T > > BvvStackSource< T > addRandomAccessibleInterval(
			final BvvHandle handle,
			final RandomAccessibleInterval< T > img,
			final T type,
			final String name,
			final AxisOrder axisOrder,
			final AffineTransform3D sourceTransform )
	{
		final List< ConverterSetup > converterSetups = new ArrayList<>();
		final List< SourceAndConverter< T > > sources = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< T > > stacks = AxisOrder.splitInputStackIntoSourceStacks( img, axisOrder );
		int numTimepoints = 1;
		for ( final RandomAccessibleInterval< T > stack : stacks )
		{
			final Source< T > s;
			if ( stack.numDimensions() > 3 )
			{
				numTimepoints = ( int ) stack.max( 3 ) + 1;
				s = new RandomAccessibleIntervalSource4D<>( stack, type, sourceTransform, name );
			}
			else
			{
				s = new RandomAccessibleIntervalSource<>( stack, type, sourceTransform, name );
			}
			addSourceToListsGenericType( s, handle.getUnusedSetupId(), converterSetups, sources );
		}
		handle.add( converterSetups, sources, numTimepoints );
		final BvvStackSource< T > bvvSource = new BvvStackSource<>( handle, numTimepoints, type, converterSetups, sources );
		handle.addBvvSource( bvvSource );
		return bvvSource;
	}

	/**
	 * Add the given {@link RandomAccessible} {@code img} to the given
	 * {@link BvvHandle} as a new {@link BvvStackSource}. The {@code img} is
	 * expected to be 2D, 3D, 4D, or 5D with the given {@link AxisOrder}.
	 *
	 * @param handle
	 *            handle to add the {@code img} to.
	 * @param img
	 *            {@link RandomAccessible} to add.
	 * @param interval
	 *            interval of the source (this is only used in the navigation
	 *            box overlay in BDV).
	 * @param numTimepoints
	 *            the number of timepoints of the source.
	 * @param type
	 *            instance of the {@code img} type.
	 * @param name
	 *            name to give to the new source
	 * @param axisOrder
	 *            {@link AxisOrder} of the source, is used to appropriately split {@code img} into channels and timepoints.
	 * @param sourceTransform
	 *            transforms from source coordinates to global coordinates.
	 * @return a new {@link BdvStackSource} handle for the newly added source(s).
	 */
	private static < T extends NumericType< T > > BvvStackSource< T > addRandomAccessible(
			final BvvHandle handle,
			final RandomAccessible< T > img,
			final Interval interval,
			final int numTimepoints,
			final T type,
			final String name,
			final AxisOrder axisOrder,
			final AffineTransform3D sourceTransform )
	{
		final List< ConverterSetup > converterSetups = new ArrayList<>();
		final List< SourceAndConverter< T > > sources = new ArrayList<>();
		final Pair< ArrayList< RandomAccessible< T > >, Interval > stacksAndInterval = AxisOrder.splitInputStackIntoSourceStacks(
				img,
				interval,
				axisOrder );
		final ArrayList< RandomAccessible< T > > stacks = stacksAndInterval.getA();
		final Interval stackInterval = stacksAndInterval.getB();
		for ( final RandomAccessible< T > stack : stacks )
		{
			final Source< T > s;
			if ( stack.numDimensions() > 3 )
				s = new RandomAccessibleSource4D<>( stack, stackInterval, type, sourceTransform, name );
			else
				s = new RandomAccessibleSource<>( stack, stackInterval, type, sourceTransform, name );
			addSourceToListsGenericType( s, handle.getUnusedSetupId(), converterSetups, sources );
		}

		handle.add( converterSetups, sources, numTimepoints );
		final BvvStackSource< T > bvvSource = new BvvStackSource<>( handle, numTimepoints, type, converterSetups, sources );
		handle.addBvvSource( bvvSource );
		return bvvSource;
	}

	/**
	 * Add the given {@link Source} to the given {@link BvvHandle} as a new
	 * {@link BvvStackSource}.
	 *
	 * @param handle
	 *            handle to add the {@code source} to.
	 * @param source
	 *            source to add.
	 * @param numTimepoints
	 *            the number of timepoints of the source.
	 * @return a new {@link BdvStackSource} handle for the newly added
	 *         {@code source}.
	 */
	@SuppressWarnings( "rawtypes" )
	private static < T > BvvStackSource< T > addSource(
			final BvvHandle handle,
			final Source< T > source,
			final int numTimepoints )
	{
		final T type = source.getType();
		final List< ConverterSetup > converterSetups = new ArrayList<>();
		final List< SourceAndConverter< T > > sources = new ArrayList<>();
		addSourceToListsGenericType( source, handle.getUnusedSetupId(), converterSetups, sources );
		handle.add( converterSetups, sources, numTimepoints );
		final BvvStackSource< T > bvvSource = new BvvStackSource<>( handle, numTimepoints, type, converterSetups, sources );
		handle.addBvvSource( bvvSource );
		return bvvSource;
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with an appropriate {@link Converter} to
	 * {@link ARGBType} and into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static < T > void addSourceToListsGenericType(
			final Source< T > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		final T type = source.getType();
		if ( type instanceof RealType || type instanceof ARGBType || type instanceof VolatileARGBType )
			addSourceToListsNumericType( ( Source ) source, setupId, converterSetups, ( List ) sources );
		else
			throw new IllegalArgumentException( "Unknown source type. Expected RealType, ARGBType, or VolatileARGBType" );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with an appropriate {@link Converter} to
	 * {@link ARGBType} and into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static < T extends NumericType< T > > void addSourceToListsNumericType(
			final Source< T > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		final T type = source.getType();
		final SourceAndConverter< T > soc = BigDataViewer.wrapWithTransformedSource(
				new SourceAndConverter<>( source, BigDataViewer.createConverterToARGB( type ) ) );
		converterSetups.add( BigDataViewer.createConverterSetup( soc, setupId ) );
		sources.add( soc );
	}

	private static < T > BvvStackSource< T > addSpimDataSource(
			final BvvHandle handle,
			final SourceAndConverter< T > source,
			final int numTimepoints )
	{
		final ConverterSetup setup = BigDataViewer.createConverterSetup( source, handle.getUnusedSetupId() );
		final List< ConverterSetup > setups = Collections.singletonList( setup );
		final List< SourceAndConverter< T > > sources = Collections.singletonList( source );
		handle.add( setups, sources, numTimepoints );

		final T type = source.getSpimSource().getType();
		final BvvStackSource< T > bdvSource = new BvvStackSource<>( handle, numTimepoints, type, setups, sources );
		handle.addBvvSource( bdvSource );

		return bdvSource;
	}
}
