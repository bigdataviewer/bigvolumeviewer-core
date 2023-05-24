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
package bvv.examples;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bvv.util.BvvFunctions;
import bvv.util.BvvStackSource;
import java.util.List;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.type.numeric.ARGBType;

public class Example05
{
	/**
	 * Show BDV multi-channel, -angle, etc datasets as cached multi-resolution stacks.
	 */
	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );

		final List< BvvStackSource< ? > > sources = BvvFunctions.show( spimData );

		sources.get( 0 ).setDisplayRange( 0, 6000 );
		sources.get( 1 ).setDisplayRange( 0, 6000 );
		sources.get( 2 ).setDisplayRange( 0, 6000 );
		sources.get( 0 ).setColor( new ARGBType( 0xffff0000 ) );
		sources.get( 1 ).setColor( new ARGBType( 0xff00ff00 ) );
		sources.get( 2 ).setColor( new ARGBType( 0xff0000ff ) );
	}
}
