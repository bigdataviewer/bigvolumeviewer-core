out vec4 FragColor;

uniform sampler2D tex;
uniform vec2 viewportScale;
uniform vec2 spw;
uniform vec2 tls;

void main()
{
	vec2 xg = trunc( ( gl_FragCoord.xy - 0.5 ) * viewportScale );
	vec2 o = vec2( mod( xg, spw ) );
	FragColor = texture( tex, ( ( xg - o ) / spw + o * tls + 0.5 ) / textureSize( tex, 0 ) );
}
