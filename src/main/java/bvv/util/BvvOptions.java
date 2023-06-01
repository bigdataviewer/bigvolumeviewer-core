/*
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
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
package bvv.util;

import bdv.util.AxisOrder;
import bdv.util.BdvFunctions;
import bdv.util.BdvSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import bvv.core.example2.VolumeViewerOptions;

/**
 * Optional parameters for {@link BdvFunctions}.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class BvvOptions
{
	public final Values values = new Values();

	/**
	 * Create default {@link BvvOptions}.
	 * @return default {@link BvvOptions}.
	 */
	public static BvvOptions options()
	{
		return new BvvOptions();
	}

	/**
	 * Set preferred size of {@link ViewerPanel} canvas. (This does not include
	 * the time slider).
	 */
	public BvvOptions preferredSize( final int w, final int h )
	{
		values.width = w;
		values.height = h;
		return this;
	}

	/**
	 * Sets the width of offscreen canvas.
	 *
	 * @param w
	 *            the width.
	 * @return this instance.
	 */
	public BvvOptions renderWidth( final int w )
	{
		values.renderWidth = w;
		return this;
	}

	/**
	 * Sets the height of offscreen canvas.
	 *
	 * @param h
	 *            the height.
	 * @return this instance.
	 */
	public BvvOptions renderHeight( final int h )
	{
		values.renderHeight = h;
		return this;
	}

	/**
	 * Set target per-frame time in milliseconds.
	 *
	 * @param t
	 *            Target rendering time in milliseconds.
	 */
	public BvvOptions maxRenderMillis( final int t )
	{
		values.maxRenderMillis = t;
		return this;
	}

	/**
	 * Sets the dither window width.
	 * E.g., {@code w=3} means {@code 3x3} dither window size.
	 *
	 * @param w
	 * 		dither window width.
	 * @return this instance.
	 */
	public BvvOptions ditherWidth( final int w )
	{
		values.ditherWidth = w;
		return this;
	}

	/**
	 * Sets the number of dither samples.
	 * Pixels are interpolated from this many nearest neighbors when dithering.
	 *
	 * @param n
	 * 		the number of dither samples.
	 *
	 * @return this instance.
	 */
	public BvvOptions numDitherSamples( final int n )
	{
		values.numDitherSamples = n;
		return this;
	}

	/**
	 * Sets the GPU cache tile size.
	 * (not padded)
	 *
	 * @param s
	 * 		the cache tile size.
	 * @return this instance
	 */
	public BvvOptions cacheBlockSize( final int[] s )
	{
		assert s.length == 3;
		values.cacheBlockSize[ 0 ] = s[ 0 ];
		values.cacheBlockSize[ 1 ] = s[ 1 ];
		values.cacheBlockSize[ 2 ] = s[ 2 ];
		return this;
	}

	/**
	 * Sets the GPU cache tile size.
	 * (not padded)
	 *
	 * @param s
	 * 		the cache tile size.
	 * @return this instance
	 */
	public BvvOptions cacheBlockSize( final int s )
	{
		values.cacheBlockSize[ 0 ] = s;
		values.cacheBlockSize[ 1 ] = s;
		values.cacheBlockSize[ 2 ] = s;
		return this;
	}

	/**
	 * Sets the max memory to use for the GPU cache texture in MB.
	 * The size of the GPU cache texture will match this as close as possible with the given tile size.
	 *
	 * @param s
	 * 		the GPU cache size in MB.
	 * @return this instance.
	 */
	public BvvOptions maxCacheSizeInMB( final int s )
	{
		values.maxCacheSizeInMB = s;
		return this;
	}

	/**
	 * Sets the distance from the camera to the z=0 plane. In units of screen pixel width.
	 *
	 * @param d
	 *            camera distance.
	 * @return this instance.
	 */
	public BvvOptions dCam( final double d )
	{
		values.dCam = d;
		return this;
	}

	/**
	 * Sets the visible distance from the z=0 plane towards the camera. In units of screen pixel width.
	 * (Should be smaller than camera distance.)
	 *
	 * @param d
	 *            visible distance from z=0 towards camera.
	 * @return this instance.
	 */
	public BvvOptions dClipNear( final double d )
	{
		values.dClipNear = d;
		return this;
	}

	/**
	 * Sets the visible distance from the z=0 plane away from the camera. In units of screen pixel width.
	 *
	 * @param d
	 *            visible distance from z=0 away from camera.
	 * @return this instance.
	 */
	public BvvOptions dClipFar( final double d )
	{
		values.dClipFar = d;
		return this;
	}

	public BvvOptions dClip( final double d )
	{
		values.dClipNear = d;
		values.dClipFar = d;
		return this;
	}

	public BvvOptions maxAllowedStepInVoxels( final double s )
	{
		values.maxAllowedStepInVoxels = s;
		return this;
	}

	/**
	 * Set how many source groups there are initially.
	 *
	 * @param n
	 *            How many source groups to create initially.
	 */
	public BvvOptions numSourceGroups( final int n )
	{
		values.numSourceGroups = n;
		return this;
	}

	/**
	 * Set the {@link InputTriggerConfig} from which keyboard and mouse action mapping is loaded.
	 *
	 * @param c the {@link InputTriggerConfig} from which keyboard and mouse action mapping is loaded
	 */
	public BvvOptions inputTriggerConfig( final InputTriggerConfig c )
	{
		values.inputTriggerConfig = c;
		return this;
	}

	/**
	 * Set the transform of the {@link BdvSource} to be created.
	 *
	 * @param t
	 *            the source transform.
	 */
	public BvvOptions sourceTransform( final AffineTransform3D t )
	{
		values.sourceTransform.set( t );
		return this;
	}

	/**
	 * Set the title of the BigDataViewer window.
	 *
	 * @param title
	 *            the window title.
	 */
	public BvvOptions frameTitle( final String title )
	{
		values.frameTitle = title;
		return this;
	}

	/**
	 * Set the transform of the {@link BdvSource} to be created to account for
	 * the given calibration (scaling of the source axes).
	 *
	 * @param calibration
	 *            the source calibration (scaling of the source axes).
	 */
	public BvvOptions sourceTransform( final double ... calibration )
	{
		final double sx = calibration.length >= 1 ? calibration[ 0 ] : 1;
		final double sy = calibration.length >= 2 ? calibration[ 1 ] : 1;
		final double sz = calibration.length >= 3 ? calibration[ 2 ] : 1;
		values.sourceTransform.set(
				sx, 0, 0, 0,
				0, sy, 0, 0,
				0, 0, sz, 0 );
		return this;
	}

	/**
	 * Specified when adding a stack. Describes how the axes of the stack are
	 * ordered.
	 *
	 * @param axisOrder
	 *            the axis order of a stack to add.
	 */
	public BvvOptions axisOrder( final AxisOrder axisOrder )
	{
		values.axisOrder = axisOrder;
		return this;
	}

	/**
	 * When showing content using one of the {@link BvvFunctions} methods, this
	 * option can be given to specify that the content should be added to an
	 * existing window. (All {@link BvvFunctions} methods return an instance of
	 * {@link Bvv} that can be used that way).
	 *
	 * @param bvv
	 *            to which viewer should the content be added.
	 */
	public BvvOptions addTo( final Bvv bvv )
	{
		values.addTo = bvv;
		return this;
	}

	/**
	 * Read-only {@link BvvOptions} values.
	 */
	public static class Values
	{
		private int width = -1;
		private int height = -1;
		private String frameTitle = "BigVolumeViewer";

		private int maxRenderMillis = 30;

		private int renderWidth = 512;
		private int renderHeight = 512;
		private int ditherWidth = 3;
		private int numDitherSamples = 8;
		private final int[] cacheBlockSize = new int[] { 32, 32, 32 };
		private int maxCacheSizeInMB = 300;
		private double dCam = 2000;
		private double dClipNear = 1000;
		private double dClipFar = 1000;
		private double maxAllowedStepInVoxels = 1.0;

		private int numSourceGroups = 10;
		private InputTriggerConfig inputTriggerConfig = null;

		private final AffineTransform3D sourceTransform = new AffineTransform3D();
		private AxisOrder axisOrder = AxisOrder.DEFAULT;
		private Bvv addTo = null;

		Values()
		{
			sourceTransform.identity();
		}

		public BvvOptions optionsFromValues()
		{
			final BvvOptions o = new BvvOptions()
					.preferredSize( width, height )
					.renderWidth( renderWidth )
					.renderHeight( renderHeight )
					.maxRenderMillis( maxRenderMillis )
					.ditherWidth( ditherWidth )
					.numDitherSamples( numDitherSamples )
					.maxCacheSizeInMB( maxCacheSizeInMB )
					.dCam( dCam )
					.dClipFar( dClipFar )
					.dClipNear( dClipNear )
					.maxAllowedStepInVoxels( maxAllowedStepInVoxels )
					.numSourceGroups( numSourceGroups )
					.inputTriggerConfig( inputTriggerConfig )
					.sourceTransform( sourceTransform )
					.frameTitle( frameTitle )
					.axisOrder( axisOrder )
					.addTo( addTo );
			return o;
		}

		public VolumeViewerOptions getVolumeViewerOptions()
		{
			final VolumeViewerOptions o = VolumeViewerOptions.options()
					.renderWidth( renderWidth )
					.renderHeight( renderHeight )
					.maxRenderMillis( maxRenderMillis )
					.ditherWidth( ditherWidth )
					.numDitherSamples( numDitherSamples )
					.maxCacheSizeInMB( maxCacheSizeInMB )
					.dCam( dCam )
					.dClipFar( dClipFar )
					.dClipNear( dClipNear )
					.maxAllowedStepInVoxels( maxAllowedStepInVoxels )
					.numSourceGroups( numSourceGroups )
					.inputTriggerConfig( inputTriggerConfig );
			if ( hasPreferredSize() )
				o.width( width ).height( height );
			return o;
		}

		public AffineTransform3D getSourceTransform()
		{
			return sourceTransform;
		}

		public String getFrameTitle()
		{
			return frameTitle;
		}

		public boolean hasPreferredSize()
		{
			return width > 0 && height > 0;
		}

		public AxisOrder axisOrder()
		{
			return axisOrder;
		}

		public InputTriggerConfig getInputTriggerConfig()
		{
			return inputTriggerConfig;
		}

		public Bvv addTo()
		{
			return addTo;
		}
	}
}
