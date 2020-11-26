/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
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
package tpietzsch.example2;

import org.scijava.ui.behaviour.util.Actions;

import static bdv.viewer.NavigationActions.ALIGN_XY_PLANE;
import static bdv.viewer.NavigationActions.ALIGN_XY_PLANE_KEYS;
import static bdv.viewer.NavigationActions.ALIGN_XZ_PLANE;
import static bdv.viewer.NavigationActions.ALIGN_XZ_PLANE_KEYS;
import static bdv.viewer.NavigationActions.ALIGN_ZY_PLANE;
import static bdv.viewer.NavigationActions.ALIGN_ZY_PLANE_KEYS;
import static bdv.viewer.NavigationActions.installModeActions;
import static bdv.viewer.NavigationActions.installSourceActions;
import static bdv.viewer.NavigationActions.installTimeActions;

public class NavigationActions
{
	/**
	 * Create navigation actions and install them in the specified
	 * {@link Actions}.
	 *
	 * @param actions
	 *            navigation actions are installed here.
	 * @param viewer
	 *            Navigation actions are targeted at this {@link VolumeViewerPanel}.
	 */
	public static void install( final Actions actions, final VolumeViewerPanel viewer )
	{
		installModeActions( actions, viewer.state() );
		installSourceActions( actions, viewer.state() );
		installTimeActions( actions, viewer.state() );
		installAlignPlaneActions( actions, viewer );
	}

	public static void installAlignPlaneActions( final Actions actions, final VolumeViewerPanel viewer )
	{
		actions.runnableAction( () -> viewer.align( VolumeViewerPanel.AlignPlane.XY ), ALIGN_XY_PLANE, ALIGN_XY_PLANE_KEYS );
		actions.runnableAction( () -> viewer.align( VolumeViewerPanel.AlignPlane.ZY ), ALIGN_ZY_PLANE, ALIGN_ZY_PLANE_KEYS );
		actions.runnableAction( () -> viewer.align( VolumeViewerPanel.AlignPlane.XZ ), ALIGN_XZ_PLANE, ALIGN_XZ_PLANE_KEYS );
	}
}
