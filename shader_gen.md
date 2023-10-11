
Shader generation:
==================


Connecting shader segments
--------------------------
Just use identifiers to be coupled in both segments. E.g.

```glsl
// a.fp:
vec4 color = vec4(
  convertR( rgb.r ), // supposed to connect to a convert()
  convertG( rgb.g ), // supposed to connect to a convert()
  1, 1 );
```

```glsl
// b.fp:
uniform float scale;
uniform float offset;
float convert( float v ) { // to be repeated 3 times with different parameters
  return scale * v + offset;
}
```

Load the segments as `SegmentTemplate`, specify what are the "special" identifiers (that should be repeated or connected)
```
templateFragConvert = new SegmentTemplate( "b.fp", "offset", "scale", "convert" );
templateFragMain = new SegmentTemplate( "a.fp", "rgb", "convertR", "convertG" );
```

`SegmentTemplate` stores `List<String> keys` (special identifiers).
`SegmentTemplate.instantiate` creates a new `Segment` instance where keys are mapped to new `SegmentTemplate.Identifier`s.
E.g.
```
Segment fragConvertR = templateFragConvert.instantiate();
Segment fragConvertR = templateFragConvert.instantiate();
```

`Segment.bind(key, other_segment, key_in_othersegment)` binds `segment.key` to `other_segment.key`, meaning they will both be replaced by the same string eventually.
Technically this is done by pointing them to the same `Identifier`. There is no union-find or similar going on, therefore order matters and it maybe buggy.
E.g.
```
Segment fragMain = templateFragMain.instantiate()
  .bind( "convertR", fragConvertR, "convert" )
  .bind( "convertG", fragConvertG, "convert" );
```
`Identifier` is either a single single unique String assigned on construction or a list (see below).
Assemble final shader:
```
shader = new SegmentedShaderBuilder().fragment(fragConvertR ).fragment( fragConvertG ).fragment( fragMain ).vertex( vertMain ).build();
```

How it's done
-------------
Fragment is loaded and specified "special" identifiers are patched with `$` limiters
```glsl
// b.fp:
uniform float scale;
uniform float offset;
float convert( float v ) { // to be repeated 3 times with different parameters
  return scale * v + offset;
}
```
becomes
```glsl
// b.fp:
uniform float $scale$;
uniform float $offset$;
float $convert$( float v ) { // to be repeated 3 times with different parameters
	return $scale$ * v + $offset$;
}
```
which can then be processed by StringTemplate.




Repeats (unrolled loops)
------------------------
Special syntax in shader. Block to repeat is encapsulated by special comments, e.g.
```glsl
// $repeat:{vis,blockTexture,convert|
if ( vis )
{
  float x = blockTexture( wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset );
  v = max( v, convert( x ) );
}
// }$
```
is patched to
```glsl
// $vis,blockTexture,convert:{vis,blockTexture,convert|

if ( $vis$ )
{
  float x = $blockTexture$( wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset );
  v = max( v, $convert$( x ) );
\}
// }$
```

The identifiers in `repeat:{<IDENTIFIER LIST>|` will be instantiated for each repeat of the block.
The number of repeats is given by the number of instantiations for each identifier (must be same number).
```
fp.repeat( "vis", numVolumes );
```
creates a list of new unique Strings for `vis` (which is a `Identifier` holding a `List<String>`).
```
fp.bind( "convert", i, colConv, "convert" );
```
binds the *i*th `convert` to the `colConv.convert` identifier.
