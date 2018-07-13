out vec4 FragColor;
uniform vec3 viewportSize;
uniform mat4 ipv;
uniform float xf;

uniform sampler3D volumeCache;

// -- comes from CacheSpec -----
uniform vec3 blockSize;
uniform vec3 paddedBlockSize;
uniform vec3 cachePadOffset;

// -- comes from TextureCache --
uniform vec3 cacheSize; // TODO: get from texture!?


float zn( float tw )
{
	return ( -tw + xf ) / ( -tw + 2 * tw * xf - xf );
}

void main()
{
	// frag coord in NDC
	vec2 uv = 2 * gl_FragCoord.xy / viewportSize.xy - 1;

	// NDC of frag on near and far plane
	vec4 front = vec4( uv, -1, 1 );
	vec4 back = vec4( uv, 1, 1 );

	// calculate eye ray in world space
	vec4 wfront = ipv * front;
	wfront *= 1 / wfront.w;
	vec4 wback = ipv * back;
	wback *= 1 / wback.w;


	// -- bounding box intersection for all volumes ----------
	float tnear = 1, tfar = 0;
	float nw, fw;

	// $repeat:{vis,intersectBoundingBox|
	bool vis = false;
	intersectBoundingBox( wfront, wback, nw, fw );
	if ( nw < fw )
	{
		tnear = min( tnear, max( 0, nw ) );
		tfar = max( tfar, min( 1, fw ) );
		vis = true;
	}
	// }$

	// -------------------------------------------------------


	if ( tnear < tfar )
	{
		float znear = zn( tnear );
		float zfar = zn( tfar );

		int numSteps = int( trunc( viewportSize.z * ( zfar - znear ) / 2 ) + 1 );
		vec4 ndcpos = vec4( uv, znear, 1 );
		vec4 ndcstep = vec4( 0, 0, 2 / viewportSize.z, 0 );

		vec4 v = vec4( 0 );
		for ( int i = 0; i < numSteps; ++i, ndcpos += ndcstep )
		{
			vec4 wpos = ipv * ndcpos;
			wpos *= 1 / wpos.w;

			// $repeat:{vis,blockTexture,convert|
			if ( vis )
			{
				float x = blockTexture( wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset );
				v = max( v, convert( x ) );
			}
			// }$
		}
		FragColor = v;
//		FragColor = vec4( tnear, tfar, 0, 1 );
//		FragColor = vec4( ( 1 + znear ) / 2, ( 1 + zfar ) / 2, 0, 1 );
	}
	else
		discard;
}
