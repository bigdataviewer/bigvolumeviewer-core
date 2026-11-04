#define NUM_BLOCK_SCALES 10

uniform mat4 im;
uniform vec3 sourcemin;
uniform vec3 sourcemax;

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
	vec4 mfront = im * wfront;
	vec4 mback = im * wback;
	intersectBox( mfront.xyz, (mback - mfront).xyz, sourcemin, sourcemax, tnear, tfar );
}

uniform sampler3D volumeCache;

// -- comes from CacheSpec -----
uniform vec3 blockSize;
uniform vec3 paddedBlockSize;
uniform vec3 cachePadOffset;

// -- comes from TextureCache --
uniform vec3 cacheSize;// TODO: get from texture!?


uniform usampler3D lutSampler;
uniform vec3 blockScales[ NUM_BLOCK_SCALES ];
uniform vec3 lutSize;
uniform vec3 lutOffset;

float sampleVolume( vec4 wpos )
{
	// base-level voxel coords
	vec3 pos = (im * wpos).xyz;

	// where to sample the LUT.
	// +0.5 because we want to sample the LUT voxel center (robust NEAREST lookup, see 49ffd82)
	vec3 q = floor( pos / blockSize ) - lutOffset + 0.5;

	// lutv.xyz = grid position of tile in the cache. lutv.w = level index into blockScales.
	uvec4 lutv = texture( lutSampler, q / lutSize );

	// corner of the tile's first non-padding voxel in the cache texture
	vec3 B0 = lutv.xyz * paddedBlockSize + cachePadOffset;

	// sj = r_base / r_j. maps base-level coords to coords at level j
	vec3 sj = blockScales[ lutv.w ];

	// level j coordinates as offset into the tile, plus tile corner offset B0
	vec3 c0 = B0 + mod( pos * sj, blockSize );

	return texture( volumeCache, c0 / cacheSize ).r;
}
