#define NUM_BLOCK_SCALES 10

uniform mat4 im;
uniform vec3 sourcemin;
uniform vec3 sourcemax;

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
	vec4 mfront = im * wfront;
	vec4 mback = im * wback;
	intersectBox( mfront.xyz, (mback - mfront).xyz, sourcemin-0.5, sourcemax+0.5, tnear, tfar );
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
	vec3 pos = (im * wpos).xyz ;
	vec3 over = pos * step(0.0,pos) - pos;
	float zerofade = 1.0;
	pos = over + pos;
	zerofade = (1.0-over.x)*(1.0-over.y)*(1-over.z);	
	vec3 q = floor( pos / blockSize ) - lutOffset + 0.5;

	uvec4 lutv = texture( lutSampler, q / lutSize );
	vec3 B0 = lutv.xyz * paddedBlockSize + cachePadOffset;
	vec3 sj = blockScales[ lutv.w ];

	vec3 c0 = B0 + mod( pos * sj, blockSize ) + 0.5 * sj;
	                                       // + 0.5 ( sj - 1 )   + 0.5 for tex coord offset

	return zerofade*texture( volumeCache, c0 / cacheSize ).r;
}
