/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2024 Tobias Pietzsch
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
package bvv.core.blocks;

import net.imglib2.RandomAccess;
import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;

public class GridDataAccessImp
{
	abstract static class AbstractCells< T , A extends DataAccess > implements GridDataAccess< T >
	{
		final RandomAccess< ? extends Cell< A > > access;

		private final CellGrid grid;

		AbstractCells( final AbstractCellImg< ?, A, ? extends Cell< A >, ? > cellImg )
		{
			access = cellImg.getCells().randomAccess();
			grid = cellImg.getCellGrid();
		}

		@Override
		public void fwd( final int d )
		{
			access.fwd( d );
		}

		@Override
		public void setPosition( final int position, final int d )
		{
			access.setPosition( position, d );
		}

		@Override
		public void setPosition( final int[] position )
		{
			access.setPosition( position );
		}

		@Override
		public int[] getPosition()
		{
			return new int[] { access.getIntPosition( 0 ), access.getIntPosition( 1 ), access.getIntPosition( 2 ) };
		}

		@Override
		public int cellSize( final int d )
		{
			return grid.cellDimension( d );
		}

		@Override
		public int cellSize( final int d, final int cellGridPosition )
		{
			return grid.getCellDimension( d, cellGridPosition );
		}

		@Override
		public int imgSize( final int d )
		{
			return ( int ) grid.imgDimension( d );
		}
	}

	/**
	 * Access to primitive data of {@code Cell}s in a {@code CellImg}.
	 *
	 * @param <T>
	 *            primitive array type of cell data, e.g. {@code short[]}.
	 */
	public static class Cells< T, A extends ArrayDataAccess< ? > > extends AbstractCells< T, A >
	{
		public Cells( final AbstractCellImg< ?, A, ? extends Cell< A >, ? > cellImg )
		{
			super( cellImg );
		}

		@SuppressWarnings( "unchecked" )
		@Override
		public T get()
		{
			return ( T ) access.get().getData().getCurrentStorageArray();
		}
	}

	/**
	 * Access to primitive data of volatile {@code Cell}s in a {@code CellImg}.
	 *
	 * @param <T>
	 *            primitive array type of cell data, e.g. {@code short[]}.
	 */
	public static class VolatileCells< T, A extends VolatileArrayDataAccess< ? > > extends AbstractCells< T, A >
	{
		public VolatileCells( final AbstractCellImg< ?, A, ? extends Cell< A >, ? > cellImg )
		{
			super( cellImg );
		}

		@SuppressWarnings( "unchecked" )
		@Override
		public T get()
		{
			final A data = access.get().getData();
			return data.isValid() ? ( T ) data.getCurrentStorageArray() : null;
		}
	}

	private GridDataAccessImp()
	{}
}
