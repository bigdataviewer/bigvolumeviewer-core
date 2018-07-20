out vec4 FragColor;

in vec2 pos;

uniform vec4 color1;
uniform vec4 color2;
uniform vec4 color3;

void main()
{
	FragColor = ( length( pos ) < 0.5 )
		? ( ( pos.x > 0 || pos.y > 0 )
			? color1
			: color2 )
		: color3;
}
