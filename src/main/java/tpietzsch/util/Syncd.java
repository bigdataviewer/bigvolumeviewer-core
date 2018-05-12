package tpietzsch.util;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.imglib2.realtransform.AffineTransform3D;
import tpietzsch.blockmath3.RaiLevels;

public class Syncd<T>
{
	private final T t;

	private final BiConsumer< T, T > setter;

	private final Function< T, T > getter;

	public Syncd( T t, BiConsumer< T, T > setter, Function< T, T > getter )
	{
		this.t = t;
		this.setter = setter;
		this.getter = getter;
	}

	public synchronized void set( T t )
	{
		setter.accept( this.t, t );
	}

	public synchronized T get()
	{
		return getter.apply( this.t );
	}

	public static Syncd< AffineTransform3D > affine3D()
	{
		return new Syncd<>(
				new AffineTransform3D(),
				AffineTransform3D::set,
				AffineTransform3D::copy );
	}

	public static Syncd< SourceIntervalAndTransform > intervalAndTransform()
	{
		return new Syncd<>(
				new SourceIntervalAndTransform(),
				SourceIntervalAndTransform::set,
				SourceIntervalAndTransform::copy );
	}

	public static Syncd< RaiLevels > raiLevels()
	{
		return new Syncd<>(
				new RaiLevels(),
				RaiLevels::set,
				RaiLevels::copy );
	}
}
