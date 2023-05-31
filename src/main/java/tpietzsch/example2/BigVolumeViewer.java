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
package tpietzsch.example2;

import static bdv.BigDataViewer.initSetups;

import bdv.tools.PreferencesDialog;
import bdv.tools.bookmarks.BookmarksEditor;
import bdv.tools.transformation.ManualTransformationEditor;
import bdv.ui.appearance.AppearanceManager;
import bdv.ui.appearance.AppearanceSettingsPage;
import bdv.ui.keymap.Keymap;
import bdv.ui.keymap.KeymapManager;
import bdv.ui.keymap.KeymapSettingsPage;
import bdv.viewer.ConverterSetups;
import bdv.viewer.NavigationActions;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerState;
import com.jogamp.opengl.GL3;

import dev.dirs.ProjectDirectories;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

import net.imglib2.util.Util;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.scijava.Context;
import org.scijava.plugin.PluginService;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.InitializeViewerState;
import bdv.tools.VisibilityAndGroupingDialog;
import bdv.tools.bookmarks.Bookmarks;
import bdv.tools.brightness.BrightnessDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.ManualTransformation;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.SpimDataException;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionsBuilder;
import org.scijava.ui.behaviour.util.Actions;
import tpietzsch.scene.TexturedUnitCube;

public class BigVolumeViewer
{
	public static String configDir = ProjectDirectories.from( "sc", "fiji", "bigvolumeviewer" ).configDir;

	// ... BDV ...
	private final VolumeViewerFrame viewerFrame;
	private final VolumeViewerPanel viewer;
	private final ManualTransformation manualTransformation;
	private final Bookmarks bookmarks;
	private final SetupAssignments setupAssignments;
	final BrightnessDialog brightnessDialog;

	private final KeymapManager keymapManager;
	private final AppearanceManager appearanceManager;
	final PreferencesDialog preferencesDialog;
	final ManualTransformationEditor manualTransformationEditor;
	final BookmarksEditor bookmarkEditor;

	private final JFileChooser fileChooser;
	private File proposedSettingsFile;

	/**
	 *
	 * @param converterSetups
	 *            list of {@link ConverterSetup} that control min/max and color
	 *            of sources.
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints
	 *            number of available timepoints.
	 * @param cacheControl
	 *            handle to cache. This is used to control io timing.
	 * @param windowTitle
	 *            title of the viewer window.
	 * @param options
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public BigVolumeViewer(
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< ? > > sources,
			final int numTimepoints,
			final CacheControl cacheControl,
			final String windowTitle,
			final VolumeViewerOptions options )
	{
		final KeymapManager optionsKeymapManager = options.values.getKeymapManager();
		final AppearanceManager optionsAppearanceManager = options.values.getAppearanceManager();
		keymapManager = optionsKeymapManager != null ? optionsKeymapManager : createDefaultKeymapManager();
		appearanceManager = optionsAppearanceManager != null ? optionsAppearanceManager : new AppearanceManager( configDir );

		InputTriggerConfig inputTriggerConfig = options.values.getInputTriggerConfig();
		final Keymap keymap = this.keymapManager.getForwardSelectedKeymap();
		if ( inputTriggerConfig == null )
			inputTriggerConfig = keymap.getConfig();

		viewerFrame = new VolumeViewerFrame( sources, numTimepoints, cacheControl, this::renderScene, options.inputTriggerConfig( inputTriggerConfig ) );
		if ( windowTitle != null )
			viewerFrame.setTitle( windowTitle );
		viewer = viewerFrame.getViewerPanel();

		manualTransformation = new ManualTransformation( viewer );
		manualTransformationEditor = new ManualTransformationEditor( viewer, viewerFrame.getKeybindings() );

		bookmarks = new Bookmarks();
		bookmarkEditor = new BookmarksEditor( viewer, viewerFrame.getKeybindings(), bookmarks );

		final ConverterSetups setups = viewerFrame.getConverterSetups();
		if ( converterSetups.size() != sources.size() )
			System.err.println( "WARNING! Constructing BigDataViewer, with converterSetups.size() that is not the same as sources.size()." );
		final int numSetups = Math.min( converterSetups.size(), sources.size() );
		for ( int i = 0; i < numSetups; ++i )
		{
			final SourceAndConverter< ? > source = sources.get( i );
			final ConverterSetup setup = converterSetups.get( i );
			if ( setup != null )
				setups.put( source, setup );
		}

		setupAssignments = new SetupAssignments( new ArrayList<>( converterSetups ), 0, 65535 );
		if ( setupAssignments.getMinMaxGroups().size() > 0 )
		{
			final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( 0 );
			for ( final ConverterSetup setup : setupAssignments.getConverterSetups() )
				setupAssignments.moveSetupToGroup( setup, group );
		}

		brightnessDialog = new BrightnessDialog( viewerFrame, setupAssignments );

		fileChooser = new JFileChooser();
		fileChooser.setFileFilter( new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "xml files";
			}

			@Override
			public boolean accept( final File f )
			{
				if ( f.isDirectory() )
					return true;
				if ( f.isFile() )
				{
					final String s = f.getName();
					final int i = s.lastIndexOf( '.' );
					if ( i > 0 && i < s.length() - 1 )
					{
						final String ext = s.substring( i + 1 ).toLowerCase();
						return ext.equals( "xml" );
					}
				}
				return false;
			}
		} );

		preferencesDialog = new PreferencesDialog( viewerFrame, keymap, new String[] { KeyConfigContexts.BIGVOLUMEVIEWER } );
		preferencesDialog.addPage( new AppearanceSettingsPage( "Appearance", appearanceManager ) );
		preferencesDialog.addPage( new KeymapSettingsPage( "Keymap", this.keymapManager, this.keymapManager.getCommandDescriptions() ) );
		appearanceManager.appearance().updateListeners().add( viewerFrame::repaint );
		appearanceManager.addLafComponent( fileChooser );
		SwingUtilities.invokeLater(() -> appearanceManager.updateLookAndFeel());

		final Actions navActions = new Actions( inputTriggerConfig, KeyConfigContexts.BIGVOLUMEVIEWER, "navigation" );
		navActions.install( viewerFrame.getKeybindings(), "navigation" );
		NavigationActions.install( navActions, viewer, false );

		final Actions bvvActions = new Actions( inputTriggerConfig, KeyConfigContexts.BIGVOLUMEVIEWER );
		bvvActions.install( viewerFrame.getKeybindings(), "bdv" );
		BigVolumeViewerActions.install( bvvActions, this );

		keymap.updateListeners().add( () -> {
			navActions.updateKeyConfig( keymap.getConfig() );
			bvvActions.updateKeyConfig( keymap.getConfig() );
			viewerFrame.getTransformBehaviours().updateKeyConfig( keymap.getConfig() );
		} );
	}

	private static KeymapManager createDefaultKeymapManager()
	{
		final KeymapManager manager = new KeymapManager( configDir );
		final CommandDescriptionsBuilder builder = new CommandDescriptionsBuilder();
		final Context context = new Context( PluginService.class );
		context.inject( builder );
		builder.discoverProviders( KeyConfigScopes.BIGVOLUMEVIEWER );
		context.dispose();
		manager.setCommandDescriptions( builder.build() );
		return manager;
	}

	public VolumeViewerPanel getViewer()
	{
		return viewer;
	}

	public VolumeViewerFrame getViewerFrame()
	{
		return viewerFrame;
	}

	public ConverterSetups getConverterSetups()
	{
		return viewerFrame.getConverterSetups();
	}

	/**
	 * @deprecated Instead {@code getViewer().state()} returns the {@link ViewerState} that can be modified directly.
	 */
	@Deprecated
	public SetupAssignments getSetupAssignments()
	{
		return setupAssignments;
	}

	public ManualTransformationEditor getManualTransformEditor()
	{
		return manualTransformationEditor;
	}

	public KeymapManager getKeymapManager()
	{
		return keymapManager;
	}

	public AppearanceManager getAppearanceManager()
	{
		return appearanceManager;
	}

	// -------------------------------------------------------------------------------------------------------
	// BDV ViewerPanel equivalents

	public void loadSettings()
	{
		fileChooser.setSelectedFile( proposedSettingsFile );
		final int returnVal = fileChooser.showOpenDialog( null );
		if ( returnVal == JFileChooser.APPROVE_OPTION )
		{
			proposedSettingsFile = fileChooser.getSelectedFile();
			try
			{
				loadSettings( proposedSettingsFile.getCanonicalPath() );
			}
			catch ( final Exception e )
			{
				e.printStackTrace();
			}
		}
	}

	public void loadSettings( final String xmlFilename ) throws IOException, JDOMException
	{
		final SAXBuilder sax = new SAXBuilder();
		final Document doc = sax.build( xmlFilename );
		final Element root = doc.getRootElement();
		viewer.stateFromXml( root );
		setupAssignments.restoreFromXml( root );
		manualTransformation.restoreFromXml( root );
		bookmarks.restoreFromXml( root );
		viewer.requestRepaint();
	}

	public boolean tryLoadSettings( final String xmlFilename )
	{
		proposedSettingsFile = null;
		if ( xmlFilename.endsWith( ".xml" ) )
		{
			final String settings = xmlFilename.substring( 0, xmlFilename.length() - ".xml".length() ) + ".settings" + ".xml";
			proposedSettingsFile = new File( settings );
			if ( proposedSettingsFile.isFile() )
			{
				try
				{
					loadSettings( settings );
					return true;
				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	public void saveSettings()
	{
		fileChooser.setSelectedFile( proposedSettingsFile );
		final int returnVal = fileChooser.showSaveDialog( null );
		if ( returnVal == JFileChooser.APPROVE_OPTION )
		{
			proposedSettingsFile = fileChooser.getSelectedFile();
			try
			{
				saveSettings( proposedSettingsFile.getCanonicalPath() );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
	}

	public void saveSettings( final String xmlFilename ) throws IOException
	{
		final Element root = new Element( "Settings" );
		root.addContent( viewer.stateToXml() );
		root.addContent( setupAssignments.toXml() );
		root.addContent( manualTransformation.toXml() );
		root.addContent( bookmarks.toXml() );
		final Document doc = new Document( root );
		final XMLOutputter xout = new XMLOutputter( Format.getPrettyFormat() );
		xout.output( doc, new FileWriter( xmlFilename ) );
	}

	public void expandAndFocusCardPanel()
	{
		viewerFrame.getSplitPanel().setCollapsed( false );
		viewerFrame.getSplitPanel().getRightComponent().requestFocusInWindow();
	}

	public void collapseCardPanel()
	{
		viewerFrame.getSplitPanel().setCollapsed( true );
		viewer.requestFocusInWindow();
	}

	// -------------------------------------------------------------------------------------------------------
	// ... "pre-existing" scene...

	private final TexturedUnitCube[] cubes = new TexturedUnitCube[]{
			new TexturedUnitCube("imglib2.png" ),
			new TexturedUnitCube("fiji.png" ),
			new TexturedUnitCube("imagej2.png" ),
			new TexturedUnitCube("scijava.png" ),
			new TexturedUnitCube("container.jpg" )
	};
	static class CubeAndTransform {
		final TexturedUnitCube cube;
		final Matrix4f model;
		public CubeAndTransform( final TexturedUnitCube cube, final Matrix4f model )
		{
			this.cube = cube;
			this.model = model;
		}
	}
	private final ArrayList< CubeAndTransform > cubeAndTransforms = new ArrayList<>();

	private void renderScene( final GL3 gl, final RenderData data )
	{
		synchronized ( cubeAndTransforms )
		{
			for ( final CubeAndTransform cubeAndTransform : cubeAndTransforms )
			{
				cubeAndTransform.cube.draw( gl, new Matrix4f( data.getPv() ).mul( cubeAndTransform.model ) );
			}
		}
	}

	private final Random random = new Random();

	void removeRandomCube()
	{
		synchronized ( cubeAndTransforms )
		{
			if ( !cubeAndTransforms.isEmpty() )
				cubeAndTransforms.remove( random.nextInt( cubeAndTransforms.size() ) );
		}
		viewer.requestRepaint();
	}

	void addRandomCube()
	{
		final AffineTransform3D sourceToWorld = new AffineTransform3D();
		final Interval interval;
		final SynchronizedViewerState state = viewer.state();
		final int t = state.getCurrentTimepoint();
		final SourceAndConverter< ? > source = state.getCurrentSource();
		source.getSpimSource().getSourceTransform( t, 0, sourceToWorld );
		interval = source.getSpimSource().getSource( t, 0 );

		final double[] zero = new double[ 3 ];
		final double[] tzero = new double[ 3 ];
		for ( int d = 0; d < 3; ++d )
			zero[ d ] = interval.min( d );
		sourceToWorld.apply( zero, tzero );

		final double[] one = new double[ 3 ];
		final double[] tone = new double[ 3 ];
		final double[] size = new double[ 3 ];
		for ( int i = 0; i < 3; ++i )
		{
			for ( int d = 0; d < 3; ++d )
				one[ d ] = d == i ? interval.max( d ) + 1 : interval.min( d );
			sourceToWorld.apply( one, tone );
			LinAlgHelpers.subtract( tone, tzero, tone );
			size[ i ] = LinAlgHelpers.length( tone );
		}
		final TexturedUnitCube cube = cubes[ random.nextInt( cubes.length ) ];
		final Matrix4f model = new Matrix4f()
				.translation(
						( float ) ( tzero[ 0 ] + random.nextDouble() * size[ 0 ] ),
						( float ) ( tzero[ 1 ] + random.nextDouble() * size[ 1 ] ),
						( float ) ( tzero[ 2 ] + random.nextDouble() * size[ 1 ] ) )
				.scale(
						( float ) ( ( random.nextDouble() + 1 ) * size[ 0 ] * 0.05 )	)
				.rotate(
						( float ) ( random.nextDouble() * Math.PI ),
						new Vector3f( random.nextFloat(), random.nextFloat(), random.nextFloat() ).normalize()
				);

		synchronized ( cubeAndTransforms )
		{
			cubeAndTransforms.add( new CubeAndTransform( cube, model ) );
		}
		viewer.requestRepaint();
	}

	// -------------------------------------------------------------------------------------------------------

	public static void run(
			final String xmlFilename,
			final int windowWidth,
			final int windowHeight,
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int numDitherSamples,
			final int cacheBlockSize,
			final int maxCacheSizeInMB,
			final double dCam,
			final double dClip ) throws SpimDataException
	{
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );

		final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		initSetups( spimData, converterSetups, sources );


		final SequenceDescriptionMinimal seq = spimData.getSequenceDescription();
		final int numTimepoints = seq.getTimePoints().size();
		final CacheControl cacheControl = ( ( ViewerImgLoader ) seq.getImgLoader() ).getCacheControl();

		final BigVolumeViewer bvv = new BigVolumeViewer( converterSetups, sources, numTimepoints, cacheControl, new File( xmlFilename ).getName(),
				VolumeViewerOptions.options().
						width( windowWidth ).
						height( windowHeight ).
						renderWidth( renderWidth ).
						renderHeight( renderHeight ).
						ditherWidth( ditherWidth ).
						numDitherSamples( numDitherSamples ).
						cacheBlockSize( cacheBlockSize ).
						maxCacheSizeInMB( maxCacheSizeInMB ).
						dCam( dCam ).
						dClip( dClip ) );

		final VolumeViewerFrame frame = bvv.viewerFrame;
		final VolumeViewerPanel viewer = bvv.viewer;

		final AffineTransform3D resetTransform = InitializeViewerState.initTransform( windowWidth, windowHeight, false, viewer.state() );
		viewer.state().setViewerTransform( resetTransform );
		final Actions actions = new Actions( new InputTriggerConfig() );
		actions.install( frame.getKeybindings(), "additional" );
		actions.runnableAction( () -> {
			viewer.state().setViewerTransform( resetTransform );
		}, "reset transform", "R" );

		if ( ! bvv.tryLoadSettings( xmlFilename ) )
			InitializeViewerState.initBrightness( 0.001, 0.999, viewer.state(), viewer.getConverterSetups() );

		frame.setVisible( true );
	}

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/TGMM_METTE/Pdu_H2BeGFP_CAAXmCherry_0123_20130312_192018.corrected/dataset_hdf5.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/MAMUT/MaMuT_demo_dataset/MaMuT_Parhyale_demo.xml";

		final int windowWidth = 640;
		final int windowHeight = 480;
		final int renderWidth = 512;
		final int renderHeight = 512;

//		final int windowWidth = 1280;
//		final int windowHeight = 960;
//		final int renderWidth = 1280;
//		final int renderHeight = 960;

//		final int renderWidth = 3840;
//		final int renderHeight = 1600;
		final int ditherWidth = 8;
		final int numDitherSamples = 8;
		final int cacheBlockSize = 64;
		final int maxCacheSizeInMB = 4000;
		final double dCam = 2000;
		final double dClip = 1000;

		run( xmlFilename, windowWidth, windowHeight, renderWidth, renderHeight, ditherWidth, numDitherSamples, cacheBlockSize, maxCacheSizeInMB, dCam, dClip );
	}
}
