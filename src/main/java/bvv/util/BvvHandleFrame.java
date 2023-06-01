/*-
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

import bdv.cache.CacheControl.CacheControls;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.ManualTransformationEditor;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import bvv.core.example2.BigVolumeViewer;
import bvv.core.example2.VolumeViewerFrame;
import bvv.core.example2.VolumeViewerOptions;

public class BvvHandleFrame extends BvvHandle
{
	private BigVolumeViewer bvv;

	private final String frameTitle;

	BvvHandleFrame( final BvvOptions options )
	{
		super( options );
		frameTitle = options.values.getFrameTitle();
		bvv = null;
		cacheControls = new CacheControls();
	}

	public BigVolumeViewer getBigVolumeViewer()
	{
		return bvv;
	}

	@Override
	public void close()
	{
		if ( bvv != null )
		{
			final VolumeViewerFrame frame = bvv.getViewerFrame();
			frame.dispatchEvent( new WindowEvent( frame, WindowEvent.WINDOW_CLOSING ) );
			bvv = null;
		}
		super.close();
	}

	@Override
	public ManualTransformationEditor getManualTransformEditor()
	{
		return bvv.getManualTransformEditor();
	}

	@Override
	public InputActionBindings getKeybindings()
	{
		return bvv.getViewerFrame().getKeybindings();
	}

	@Override
	public TriggerBehaviourBindings getTriggerbindings()
	{
		return bvv.getViewerFrame().getTriggerbindings();
	}

	@Override
	boolean createViewer(
			final List< ? extends ConverterSetup > converterSetups,
			final List< ? extends SourceAndConverter< ? > > sources,
			final int numTimepoints )
	{
		final VolumeViewerOptions viewerOptions = bvvOptions.values.getVolumeViewerOptions();
		bvv = new BigVolumeViewer(
				new ArrayList<>( converterSetups ),
				new ArrayList<>( sources ),
				numTimepoints,
				cacheControls,
				frameTitle,
				viewerOptions );
		viewer = bvv.getViewer();
		setupAssignments = bvv.getSetupAssignments();
		setups = bvv.getConverterSetups();

		viewer.setDisplayMode( DisplayMode.FUSED );
		bvv.getViewerFrame().setVisible( true );

		final boolean initTransform = !sources.isEmpty();
		return initTransform;
	}
}
