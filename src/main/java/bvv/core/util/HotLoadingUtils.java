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
package bvv.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.scijava.listeners.Listeners;

/**
 * Utility to support hot-loading shaders <em>during development</em> (e.g., when running from IDE).
 * <p>
 * Running from IDE, shader resources are located in {@code target/classes/...}.
 * They are copied there during build from the corresponding classes in {@code src/main/resources/...}.
 * <p>
 * This utility watches modified timestamps of registered resources, and if they are changed, copies
 * them to {@code target/classes/...}, so that they will be picked up when the program loads from resources.
 * <p>
 * Note that this class doesn't do any shader loading itself, it's just for support...
 */
public class HotLoadingUtils
{
	static class FilePair
	{
		private final File target;

		private final File resource;

		private long lastModified;

		public final Listeners.List< Runnable > listeners = new Listeners.List<>();

		FilePair( final String targetName )
		{
			target = new File( targetName );
			resource = new File( targetName.replaceAll( "target/classes", "src/main/resources" ) );
			lastModified = resource.lastModified();
		}

		void checkModified() throws IOException
		{
			final long mod = resource.lastModified();
			if ( mod != lastModified )
			{
				lastModified = mod;
				Files.copy( resource.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING );
				listeners.list.forEach( Runnable::run );
			}
		}
	}

	// key is targetName with which FilePair was constructed
	private static final Map< String, FilePair > files = new HashMap<>();

	private static void add( final String f, final Runnable runnable )
	{
		files.computeIfAbsent( f, FilePair::new ).listeners.add( runnable );
	}

	private static void checkModified()
	{
		try
		{
			for ( final FilePair file : files.values() )
				file.checkModified();
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}
	}

	public static class ShaderHotLoader
	{
		private final AtomicBoolean modified;

		private final Runnable setModified;

		public ShaderHotLoader( final String... filesToWatch )
		{
			this( false, filesToWatch );
		}

		public ShaderHotLoader( final boolean initiallyModified, final String... filesToWatch )
		{
			modified = new AtomicBoolean( initiallyModified );
			setModified = () -> modified.set( true );
			for ( final String f : filesToWatch )
				HotLoadingUtils.add( f, setModified );
		}

		public ShaderHotLoader watch( final Class< ? > resourceContext, final String resourceName )
		{
			watch( resourceContext.getResource( resourceName ).getFile() );
			return this;
		}

		public ShaderHotLoader watch( final String filename )
		{
			HotLoadingUtils.add( filename, setModified );
			return this;
		}

		public boolean isModified()
		{
			HotLoadingUtils.checkModified();
			return modified.getAndSet( false );
		}
	}
}
