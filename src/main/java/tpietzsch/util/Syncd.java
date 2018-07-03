package tpietzsch.util;

import java.util.function.BiConsumer;
import java.util.function.Function;

import net.imglib2.realtransform.AffineTransform3D;

public class Syncd<T>
{
	private final T t;

	private final BiConsumer< T, T > setter;

	private final Function< T, T > getter;

	public Syncd( final T t, final BiConsumer< T, T > setter, final Function< T, T > getter )
	{
		this.t = t;
		this.setter = setter;
		this.getter = getter;
	}

	public synchronized void set( final T t )
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
}
